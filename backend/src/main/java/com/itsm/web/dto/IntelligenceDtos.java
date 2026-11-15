package com.itsm.web.dto;

import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Urgency;
import com.itsm.web.dto.CommonDtos.Ref;
import com.itsm.web.dto.CommonDtos.UserRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** DTOs for the intelligent/automated features. */
public final class IntelligenceDtos {

    private IntelligenceDtos() {
    }

    /** Live analysis while the reporter is still typing (nothing is saved). */
    public record AnalyzeRequest(
            @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull Long serviceId,
            Impact impact,
            Urgency urgency,
            @Min(1) @Max(1_000_000) Integer affectedUsers) {}

    public record CategorySuggestionDto(Ref category, double confidence, List<String> matchedKeywords) {}

    public record SimilarIncidentDto(Long id, String number, String title, IncidentStatus status, Ref service,
                                     Instant reportedAt, double score, double textSimilarity, List<String> reasons) {}

    public record PriorityPreview(Priority priority, double score, String rationale, String strategy,
                                  Integer responseMinutes, Integer resolutionMinutes) {}

    public record AnalysisResponse(List<CategorySuggestionDto> categorySuggestions,
                                   List<SimilarIncidentDto> possibleDuplicates,
                                   PriorityPreview predictedPriority) {}

    public record ResolutionSuggestionDto(Long incidentId, String incidentNumber, String title, Ref category,
                                          double similarity, String summary, String rootCause) {}

    public record RecommendationDto(UserRef technician, double score, int activeIncidents, int maxIncidents,
                                    List<String> reasons) {}

    public record RecommendationResponse(String strategy, String strategyName, List<RecommendationDto> recommendations) {}

    public record ClusterIncident(Long id, String number, String title, Priority priority, IncidentStatus status,
                                  Ref service, Instant reportedAt) {}

    public record CorrelationClusterDto(Ref probableRoot, int size, boolean majorIncident, String explanation,
                                        List<ClusterIncident> directIncidents, List<ClusterIncident> dependentIncidents) {}

    public record SlaRiskItem(Long incidentId, String number, String title, Priority priority, IncidentStatus status,
                              UserRef assignedTo, double riskScore, String level, Instant predictedResolution,
                              Instant deadline, List<String> factors) {}
}
