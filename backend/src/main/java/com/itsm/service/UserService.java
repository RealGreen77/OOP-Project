package com.itsm.service;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.user.AvailabilityStatus;
import com.itsm.domain.user.Role;
import com.itsm.domain.user.TeamLead;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.domain.user.UserFactory;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.InvalidInputException;
import com.itsm.exception.ResourceAlreadyExistsException;
import com.itsm.exception.UnauthorizedOperationException;
import com.itsm.exception.UserNotFoundException;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.repository.UserRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.CommonDtos.UserRef;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.UserDtos.CreateUserRequest;
import com.itsm.web.dto.UserDtos.TechnicianWorkload;
import com.itsm.web.dto.UserDtos.UpdateUserRequest;
import com.itsm.web.dto.UserDtos.UserDetail;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Administrator user & technician management, plus technician self-service (availability). */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final CategoryRepository categoryRepository;
    private final ITServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserCodeGenerator codeGenerator;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final DtoMapper mapper;

    public UserService(UserRepository userRepository, TechnicianRepository technicianRepository,
                       CategoryRepository categoryRepository, ITServiceRepository serviceRepository,
                       IncidentRepository incidentRepository, PasswordEncoder passwordEncoder,
                       UserCodeGenerator codeGenerator, AuditService auditService,
                       CurrentUserProvider currentUser, DtoMapper mapper) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
        this.passwordEncoder = passwordEncoder;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<UserDetail> list(String query) {
        Map<Long, Integer> workload = workloadMap();
        return userRepository.search(query == null || query.isBlank() ? null : query.trim()).stream()
                .map(u -> mapper.userDetail(u, workload.getOrDefault(u.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDetail get(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        return mapper.userDetail(user, workloadMap().getOrDefault(id, 0));
    }

    @Transactional
    public UserDetail create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResourceAlreadyExistsException("User", "email", request.email());
        }
        User user = UserFactory.create(request.role(), codeGenerator.next(request.role()), request.fullName(),
                request.email(), passwordEncoder.encode(request.password()), request.department(),
                request.experienceYears(), request.maxConcurrentIncidents());
        user.updateProfile(request.fullName(), request.department(), blank(request.phone()));
        if (user instanceof Technician tech) {
            applyTechnicianFields(tech, request.skillCategoryIds(), request.serviceIds(), request.teamLeadId());
        }
        userRepository.save(user);
        auditService.recordAction(currentUser.currentUser(), AuditAction.USER_CREATED, AuditService.USER, user.getId(),
                user.getUserCode(), null, user.getRole().name(), "Created " + user.getRole() + " " + user.displayName());
        return mapper.userDetail(user, 0);
    }

    @Transactional
    public UserDetail update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        user.updateProfile(request.fullName(), request.department(), blank(request.phone()));
        if (user instanceof Technician tech) {
            tech.updateCapacity(
                    request.experienceYears() == null ? tech.getExperienceYears() : request.experienceYears(),
                    request.maxConcurrentIncidents() == null ? tech.getMaxConcurrentIncidents() : request.maxConcurrentIncidents());
            if (request.availability() != null) tech.changeAvailability(request.availability());
            applyTechnicianFields(tech, request.skillCategoryIds(), request.serviceIds(), request.teamLeadId());
        }
        auditService.recordAction(currentUser.currentUser(), AuditAction.USER_UPDATED, AuditService.USER, user.getId(),
                user.getUserCode(), null, null, "Updated profile of " + user.displayName());
        return mapper.userDetail(user, workloadMap().getOrDefault(id, 0));
    }

    @Transactional
    public UserDetail setActive(Long id, boolean active) {
        User actor = currentUser.currentUser();
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (!active && user.getId().equals(actor.getId())) {
            throw new BusinessRuleViolationException("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
        }
        if (!active && user.getRole().isSupportStaff()) {
            long open = incidentRepository.countByAssignedToIdAndStatusIn(id, IncidentStatus.TECHNICIAN_WORKLOAD);
            if (open > 0) {
                throw new BusinessRuleViolationException("TECHNICIAN_HAS_OPEN_WORK",
                        user.getUserCode() + " still owns " + open + " active incident(s); reassign them first");
            }
        }
        String before = user.isActive() ? "ACTIVE" : "INACTIVE";
        if (active) user.activate(); else user.deactivate();
        auditService.recordAction(actor, AuditAction.USER_UPDATED, AuditService.USER, user.getId(), user.getUserCode(),
                before, active ? "ACTIVE" : "INACTIVE", (active ? "Activated " : "Deactivated ") + user.displayName());
        return mapper.userDetail(user, 0);
    }

    /** Technicians and team leads with live workload (for workload board and manual assignment). */
    @Transactional(readOnly = true)
    public List<TechnicianWorkload> technicianWorkloads() {
        Map<Long, Integer> workload = workloadMap();
        return technicianRepository.findAllActiveWithSkills().stream()
                .map(t -> {
                    int active = workload.getOrDefault(t.getId(), 0);
                    return new TechnicianWorkload(mapper.userRef(t), t.getAvailability(), active,
                            t.getMaxConcurrentIncidents(),
                            Math.round(t.loadRatio(active) * 1000) / 10.0,
                            t.getSkills().stream().map(Category::getName).sorted().toList(),
                            t.getExperienceYears(), mapper.userRef(t.getTeamLead()));
                })
                .sorted(Comparator.comparing((TechnicianWorkload w) -> w.technician().userCode()))
                .toList();
    }

    @Transactional
    public UserDetail changeMyAvailability(AvailabilityStatus availability) {
        User user = currentUser.currentUser();
        if (!(user instanceof Technician tech)) {
            throw new UnauthorizedOperationException("Only technicians have an availability status");
        }
        AvailabilityStatus before = tech.getAvailability();
        tech.changeAvailability(availability);
        auditService.recordAction(user, AuditAction.USER_UPDATED, AuditService.USER, user.getId(), user.getUserCode(),
                before.name(), availability.name(), "Availability changed to " + availability);
        return mapper.userDetail(user, workloadMap().getOrDefault(user.getId(), 0));
    }

    @Transactional(readOnly = true)
    public List<UserRef> teamLeads() {
        return userRepository.findActiveTeamLeads().stream().map(mapper::userRef).toList();
    }

    // ---------------------------------------------------------------- helpers

    private void applyTechnicianFields(Technician tech, Set<Long> skillIds, Set<Long> serviceIds, Long teamLeadId) {
        if (skillIds != null) {
            Set<Category> skills = new HashSet<>(categoryRepository.findAllById(skillIds));
            if (skills.size() != skillIds.size()) throw new InvalidInputException("One or more skill categories do not exist");
            tech.replaceSkills(skills);
        }
        if (serviceIds != null) {
            Set<ITService> services = new HashSet<>(serviceRepository.findAllById(serviceIds));
            if (services.size() != serviceIds.size()) throw new InvalidInputException("One or more services do not exist");
            tech.replaceServiceExpertise(services);
        }
        if (teamLeadId != null) {
            User lead = userRepository.findById(teamLeadId).orElseThrow(() -> new UserNotFoundException(teamLeadId));
            if (!(lead instanceof TeamLead teamLead) || lead.getRole() != Role.TEAM_LEAD) {
                throw new InvalidInputException("User " + lead.getUserCode() + " is not a team lead");
            }
            tech.joinTeam(teamLead);
        }
    }

    private Map<Long, Integer> workloadMap() {
        Map<Long, Integer> map = new HashMap<>();
        for (Object[] row : incidentRepository.countWorkloadByTechnician(IncidentStatus.TECHNICIAN_WORKLOAD)) {
            map.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return map;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
