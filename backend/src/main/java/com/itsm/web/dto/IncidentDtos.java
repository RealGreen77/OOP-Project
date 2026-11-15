package com.itsm.web.dto;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.incident.ClosureCode;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.IncidentAction;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.NoteType;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Urgency;
import com.itsm.domain.notification.DeliveryStatus;
import com.itsm.domain.notification.NotificationChannelType;
import com.itsm.domain.sla.SlaStatus;
import com.itsm.web.dto.CommonDtos.Ref;
import com.itsm.web.dto.CommonDtos.UserRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Incident lifecycle requests and responses. */
public final class IncidentDtos {

    private IncidentDtos() {
    }

    // ---------------------------------------------------------------- requests

    public record ReportIncidentRequest(
            @NotBlank @Size(min = 5, max = 200) String title,
            @NotBlank @Size(min = 10, max = 4000) String description,
            @NotNull Long serviceId,
            Long categoryId,
            @NotNull Impact impact,
            @NotNull Urgency urgency,
            @Min(1) @Max(1_000_000) Integer affectedUsers,
            boolean force) {}

    public record UpdateIncidentRequest(
            @NotBlank @Size(min = 5, max = 200) String title,
            @NotBlank @Size(min = 10, max = 4000) String description,
            @NotNull Long serviceId,
            @NotNull Impact impact,
            @NotNull Urgency urgency,
            @NotNull @Min(1) @Max(1_000_000) Integer affectedUsers) {}

    public record ClassifyRequest(@NotNull Long categoryId) {}

    /** technicianId null = let the active assignment strategy choose. */
    public record AssignRequest(Long technicianId) {}

    public record CommentRequest(@Size(max = 2000) String comment) {}

    public record RequiredCommentRequest(@NotBlank @Size(min = 3, max = 2000) String comment) {}

    public record NoteRequest(
            @NotBlank @Size(max = 4000) String content,
            boolean internal,
            NoteType type) {}

    public record ResolveRequest(
            @NotBlank @Size(min = 10, max = 2000) String summary,
            @Size(max = 1000) String rootCause,
            boolean workaround) {}

    public record CloseRequest(@Size(max = 1000) String notes, ClosureCode closureCode) {}

    public record DuplicateRequest(@NotNull Long parentIncidentId) {}

    public record FeedbackRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}

    // ---------------------------------------------------------------- responses

    public record SlaDto(
            String policyName,
            Integer responseTargetMinutes,
            Integer resolutionTargetMinutes,
            Instant responseDeadline,
            Instant resolutionDeadline,
            Instant respondedAt,
            boolean responseBreached,
            boolean resolutionBreached,
            boolean paused,
            SlaStatus status,
            double elapsedPercent) {}

    public record SlaRiskDto(double riskScore, String level, Instant predictedResolution, Instant deadline,
                             List<String> factors) {}

    public record NoteDto(Long id, NoteType type, String content, boolean internal, UserRef author, Instant createdAt) {}

    public record ResolutionDto(String summary, String rootCause, boolean workaround) {}

    public record FeedbackDto(Integer rating, String comment, Instant submittedAt) {}

    public record IncidentSummary(
            Long id,
            String number,
            String title,
            IncidentStatus status,
            Priority priority,
            Impact impact,
            Urgency urgency,
            Ref category,
            Ref service,
            UserRef reporter,
            UserRef assignedTo,
            Instant reportedAt,
            SlaStatus slaStatus,
            Instant resolutionDeadline,
            int escalationLevel,
            boolean suspectedDuplicate) {}

    public record IncidentDetail(
            Long id,
            String number,
            String title,
            String description,
            IncidentStatus status,
            Set<IncidentAction> allowedActions,
            Priority priority,
            Double priorityScore,
            String priorityRationale,
            Impact impact,
            Urgency urgency,
            int affectedUsers,
            Ref category,
            Ref service,
            BusinessCriticality serviceCriticality,
            UserRef reporter,
            UserRef assignedTo,
            Instant reportedAt,
            Instant assignedAt,
            Instant acknowledgedAt,
            Instant resolvedAt,
            Instant verifiedAt,
            Instant closedAt,
            SlaDto sla,
            SlaRiskDto slaRisk,
            ResolutionDto resolution,
            ClosureCode closureCode,
            String closureNotes,
            FeedbackDto feedback,
            int escalationLevel,
            int reopenCount,
            Ref duplicateOf,
            Ref suspectedDuplicateOf,
            Double suspectedDuplicateScore,
            List<NoteDto> notes,
            long version) {}

    public record AuditEntryDto(
            Long id, Instant occurredAt, String actorName, String actorRole, AuditAction action,
            String entityType, Long entityId, String entityRef, String previousState, String newState,
            String description) {}

    public record NotificationDto(
            Long id, NotificationChannelType channel, String eventType, String title, String message,
            Long incidentId, String incidentNumber, boolean read, Instant createdAt,
            DeliveryStatus deliveryStatus, String destination, UserRef recipient, String failureReason) {}

    public record UnreadCount(long unread) {}
}
