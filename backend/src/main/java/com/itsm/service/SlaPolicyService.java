package com.itsm.service;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.ResourceNotFoundException;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.CatalogDtos.SlaPolicyDto;
import com.itsm.web.dto.CatalogDtos.SlaPolicyRequest;
import com.itsm.web.dto.DtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Administrator configuration of SLA policies (global defaults + per-service overrides). */
@Service
public class SlaPolicyService {

    private final SlaPolicyRepository repository;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final DtoMapper mapper;

    public SlaPolicyService(SlaPolicyRepository repository, CatalogService catalogService, AuditService auditService,
                            CurrentUserProvider currentUser, DtoMapper mapper) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyDto> list() {
        return repository.findAllOrdered().stream().map(mapper::slaPolicy).toList();
    }

    @Transactional
    public SlaPolicyDto create(SlaPolicyRequest r) {
        ITService service = r.serviceId() == null ? null : catalogService.findService(r.serviceId());
        ensureUnique(null, r, service);
        SlaPolicy policy = repository.save(new SlaPolicy(r.name(), r.priority(), r.responseMinutes(),
                r.resolutionMinutes(), r.warningThresholdPercent(), service));
        audit("Created SLA policy \"" + policy.getName() + "\" for " + scope(policy), policy);
        return mapper.slaPolicy(policy);
    }

    @Transactional
    public SlaPolicyDto update(Long id, SlaPolicyRequest r) {
        SlaPolicy policy = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("SLA policy", id));
        ensureUnique(id, r, policy.getService());
        String before = policy.getResponseMinutes() + "/" + policy.getResolutionMinutes() + " min";
        policy.update(r.name(), r.priority(), r.responseMinutes(), r.resolutionMinutes(), r.warningThresholdPercent());
        if (r.active() != null) {
            if (r.active()) policy.activate();
            else {
                if (policy.isGlobalDefault()) {
                    throw new BusinessRuleViolationException("DEFAULT_SLA_REQUIRED",
                            "Global default SLA policies cannot be deactivated (every priority needs a fallback)");
                }
                policy.deactivate();
            }
        }
        auditService.recordAction(currentUser.currentUser(), AuditAction.CONFIGURATION_CHANGED, AuditService.CONFIG,
                policy.getId(), "SLA-" + policy.getId(), before,
                policy.getResponseMinutes() + "/" + policy.getResolutionMinutes() + " min",
                "Updated SLA policy \"" + policy.getName() + "\" for " + scope(policy));
        return mapper.slaPolicy(policy);
    }

    private void ensureUnique(Long selfId, SlaPolicyRequest r, ITService service) {
        boolean clash = repository.findAll().stream()
                .filter(SlaPolicy::isActive)
                .filter(p -> !Objects.equals(p.getId(), selfId))
                .anyMatch(p -> p.getPriority() == r.priority()
                        && Objects.equals(p.getService() == null ? null : p.getService().getId(),
                                          service == null ? null : service.getId()));
        if (clash) {
            throw new BusinessRuleViolationException("SLA_POLICY_EXISTS",
                    "An active SLA policy already exists for " + r.priority() + " / "
                            + (service == null ? "global default" : service.getName()));
        }
    }

    private void audit(String description, SlaPolicy policy) {
        auditService.recordAction(currentUser.currentUser(), AuditAction.CONFIGURATION_CHANGED, AuditService.CONFIG,
                policy.getId(), "SLA-" + policy.getId(), null, null, description);
    }

    private static String scope(SlaPolicy p) {
        return p.getPriority() + " / " + (p.getService() == null ? "all services" : p.getService().getName());
    }
}
