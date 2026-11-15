package com.itsm.web.dto;

import com.itsm.domain.audit.AuditEntry;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentNote;
import com.itsm.domain.notification.Notification;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.sla.SlaRecord;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.engine.intelligence.SlaRiskAssessment;
import com.itsm.web.dto.CatalogDtos.CategoryDto;
import com.itsm.web.dto.CatalogDtos.ServiceDto;
import com.itsm.web.dto.CatalogDtos.SlaPolicyDto;
import com.itsm.web.dto.CommonDtos.Ref;
import com.itsm.web.dto.CommonDtos.UserRef;
import com.itsm.web.dto.IncidentDtos.AuditEntryDto;
import com.itsm.web.dto.IncidentDtos.FeedbackDto;
import com.itsm.web.dto.IncidentDtos.IncidentDetail;
import com.itsm.web.dto.IncidentDtos.IncidentSummary;
import com.itsm.web.dto.IncidentDtos.NoteDto;
import com.itsm.web.dto.IncidentDtos.NotificationDto;
import com.itsm.web.dto.IncidentDtos.ResolutionDto;
import com.itsm.web.dto.IncidentDtos.SlaDto;
import com.itsm.web.dto.IncidentDtos.SlaRiskDto;
import com.itsm.web.dto.UserDtos.TechnicianProfile;
import com.itsm.web.dto.UserDtos.UserDetail;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Maps domain entities to API DTOs. Entities never leave the service layer, so internal fields
 * (password hashes, lazy associations, version internals) cannot leak into responses.
 */
@Component
public class DtoMapper {

    public Ref ref(Category c) {
        return c == null ? null : new Ref(c.getId(), c.getCode(), c.getName());
    }

    public Ref ref(ITService s) {
        return s == null ? null : new Ref(s.getId(), s.getCode(), s.getName());
    }

    public Ref ref(Incident i) {
        return i == null ? null : new Ref(i.getId(), i.getNumber(), i.getTitle());
    }

    public UserRef userRef(User u) {
        return u == null ? null : new UserRef(u.getId(), u.getUserCode(), u.getFullName(), u.getRole().name());
    }

    public UserDetail userDetail(User u, int activeIncidents) {
        TechnicianProfile tech = null;
        if (u instanceof Technician t) {
            tech = new TechnicianProfile(t.getExperienceYears(), t.getMaxConcurrentIncidents(), t.getAvailability(),
                    t.getSkills().stream().sorted(Comparator.comparing(Category::getName)).map(this::ref).toList(),
                    t.getServiceExpertise().stream().sorted(Comparator.comparing(ITService::getName)).map(this::ref).toList(),
                    userRef(t.getTeamLead()), activeIncidents);
        }
        return new UserDetail(u.getId(), u.getUserCode(), u.getFullName(), u.getEmail(), u.getRole(), u.getDepartment(),
                u.getPhone(), u.isActive(), u.getLastLoginAt(), u.getCreatedAt(),
                u.getPreferences().isEmailEnabled(), u.getPreferences().isSmsEnabled(),
                u.permissions().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new)), tech);
    }

    public CategoryDto category(Category c) {
        return new CategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                c.getKeywords().stream().sorted().toList(), c.isActive());
    }

    public ServiceDto service(ITService s) {
        return new ServiceDto(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getCriticality(),
                s.getOwnerTeam(), ref(s.getDefaultCategory()),
                s.getDependencies().stream().sorted(Comparator.comparing(ITService::getName)).map(this::ref).toList(),
                s.isActive());
    }

    public SlaPolicyDto slaPolicy(SlaPolicy p) {
        return new SlaPolicyDto(p.getId(), p.getName(), p.getPriority(), p.getResponseMinutes(), p.getResolutionMinutes(),
                p.getWarningThresholdPercent(), ref(p.getService()), p.isActive());
    }

    public SlaDto sla(Incident i, Instant now) {
        SlaRecord s = i.getSla();
        if (s == null || s.getStartedAt() == null) return null;
        double elapsed = i.getResolvedAt() != null ? 100.0 * Math.min(1.5, s.elapsedFraction(i.getResolvedAt()))
                : 100.0 * Math.min(1.5, s.elapsedFraction(now));
        return new SlaDto(s.getPolicyName(), s.getResponseTargetMinutes(), s.getResolutionTargetMinutes(),
                s.getResponseDeadline(), s.getResolutionDeadline(), s.getRespondedAt(), s.isResponseBreached(),
                s.isResolutionBreached(), s.isPaused(), i.slaStatus(now), Math.round(elapsed * 10) / 10.0);
    }

    public SlaRiskDto risk(SlaRiskAssessment r) {
        return r == null ? null : new SlaRiskDto(r.riskScore(), r.level().name(), r.predictedResolution(), r.deadline(), r.factors());
    }

    public IncidentSummary summary(Incident i, Instant now) {
        return new IncidentSummary(i.getId(), i.getNumber(), i.getTitle(), i.getStatus(), i.getPriority(), i.getImpact(),
                i.getUrgency(), ref(i.getCategory()), ref(i.getAffectedService()), userRef(i.getReporter()),
                userRef(i.getAssignedTo()), i.getReportedAt(), i.slaStatus(now),
                i.getSla() == null ? null : i.getSla().getResolutionDeadline(), i.getEscalationLevel(),
                i.getSuspectedDuplicateOf() != null && i.getDuplicateOf() == null);
    }

    public IncidentDetail detail(Incident i, Instant now, boolean includeInternalNotes, SlaRiskAssessment risk) {
        List<NoteDto> notes = i.getNotes().stream()
                .filter(n -> includeInternalNotes || !n.isInternal())
                .map(this::note)
                .toList();
        return new IncidentDetail(i.getId(), i.getNumber(), i.getTitle(), i.getDescription(), i.getStatus(),
                i.allowedActions(), i.getPriority(), i.getPriorityScore(), i.getPriorityRationale(), i.getImpact(),
                i.getUrgency(), i.getAffectedUsers(), ref(i.getCategory()), ref(i.getAffectedService()),
                i.getAffectedService().getCriticality(), userRef(i.getReporter()), userRef(i.getAssignedTo()),
                i.getReportedAt(), i.getAssignedAt(), i.getAcknowledgedAt(), i.getResolvedAt(), i.getVerifiedAt(),
                i.getClosedAt(), sla(i, now), risk(risk),
                i.getResolution() == null || i.getResolution().getSummary() == null ? null
                        : new ResolutionDto(i.getResolution().getSummary(), i.getResolution().getRootCause(), i.getResolution().isWorkaround()),
                i.getClosureCode(), i.getClosureNotes(),
                i.getFeedback() == null || i.getFeedback().getRating() == null ? null
                        : new FeedbackDto(i.getFeedback().getRating(), i.getFeedback().getComment(), i.getFeedback().getSubmittedAt()),
                i.getEscalationLevel(), i.getReopenCount(), ref(i.getDuplicateOf()), ref(i.getSuspectedDuplicateOf()),
                i.getSuspectedDuplicateScore(), notes, i.getVersion());
    }

    public NoteDto note(IncidentNote n) {
        return new NoteDto(n.getId(), n.getType(), n.getContent(), n.isInternal(), userRef(n.getAuthor()), n.getCreatedAt());
    }

    public AuditEntryDto audit(AuditEntry a) {
        return new AuditEntryDto(a.getId(), a.getOccurredAt(), a.getActorName(), a.getActorRole(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getEntityRef(), a.getPreviousState(), a.getNewState(), a.getDescription());
    }

    public NotificationDto notification(Notification n) {
        return new NotificationDto(n.getId(), n.getChannel(), n.getEventType(), n.getTitle(), n.getMessage(),
                n.getIncidentId(), n.getIncidentNumber(), n.isRead(), n.getCreatedAt(), n.getDeliveryStatus(),
                n.getDestination(), userRef(n.getRecipient()), n.getFailureReason());
    }
}
