package com.itsm.service;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.user.Technician;
import com.itsm.engine.assignment.AssignmentStrategy;
import com.itsm.engine.assignment.TechnicianCandidate;
import com.itsm.engine.assignment.TechnicianRecommendation;
import com.itsm.exception.TechnicianUnavailableException;
import com.itsm.exception.UserNotFoundException;
import com.itsm.exception.WorkloadExceededException;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.TechnicianRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds technician candidates with live metrics and delegates the choice to the active strategy.
 * // [MVP: Technician Assignment] [Intelligent Feature D: Smart Technician Recommendation]
 */
@Service
public class AssignmentService {

    private final TechnicianRepository technicianRepository;
    private final IncidentRepository incidentRepository;
    private final SettingsService settings;

    public AssignmentService(TechnicianRepository technicianRepository, IncidentRepository incidentRepository,
                             SettingsService settings) {
        this.technicianRepository = technicianRepository;
        this.incidentRepository = incidentRepository;
        this.settings = settings;
    }

    public AssignmentStrategy activeStrategy() {
        return settings.activeAssignmentStrategy();
    }

    public List<TechnicianRecommendation> recommend(Incident incident) {
        return activeStrategy().rank(incident, candidatesFor(incident));
    }

    public Optional<TechnicianRecommendation> autoSelect(Incident incident) {
        return activeStrategy().select(incident, candidatesFor(incident));
    }

    /** Validates a manual assignment choice and returns the technician. */
    public Technician validateManualAssignment(Incident incident, Long technicianId) {
        Technician tech = technicianRepository.findWithSkillsById(technicianId)
                .orElseThrow(() -> new UserNotFoundException(technicianId));
        if (!tech.isActive()) {
            throw new TechnicianUnavailableException("Technician " + tech.getUserCode() + " is deactivated");
        }
        if (!tech.getAvailability().canTakeWork()) {
            throw new TechnicianUnavailableException("Technician " + tech.getUserCode() + " is " + tech.getAvailability());
        }
        if (incident.isAssignedTo(tech)) {
            throw new TechnicianUnavailableException(incident.getNumber() + " is already assigned to " + tech.getUserCode());
        }
        int active = (int) incidentRepository.countByAssignedToIdAndStatusIn(tech.getId(), IncidentStatus.TECHNICIAN_WORKLOAD);
        if (active >= tech.getMaxConcurrentIncidents()) {
            throw new WorkloadExceededException(tech.getUserCode(), active, tech.getMaxConcurrentIncidents());
        }
        return tech;
    }

    public int activeWorkload(Long technicianId) {
        return technicianId == null ? 0
                : (int) incidentRepository.countByAssignedToIdAndStatusIn(technicianId, IncidentStatus.TECHNICIAN_WORKLOAD);
    }

    List<TechnicianCandidate> candidatesFor(Incident incident) {
        Map<Long, Integer> workload = new HashMap<>();
        for (Object[] row : incidentRepository.countWorkloadByTechnician(IncidentStatus.TECHNICIAN_WORKLOAD)) {
            workload.put((Long) row[0], ((Long) row[1]).intValue());
        }
        Map<Long, Long> serviceHistory = new HashMap<>();
        for (Object[] row : incidentRepository.countResolvedByTechnicianForService(incident.getAffectedService().getId())) {
            serviceHistory.put((Long) row[0], (Long) row[1]);
        }
        return technicianRepository.findAllActiveWithSkills().stream()
                .filter(t -> !incident.isAssignedTo(t))
                .map(t -> new TechnicianCandidate(t, workload.getOrDefault(t.getId(), 0), serviceHistory.getOrDefault(t.getId(), 0L)))
                .toList();
    }
}
