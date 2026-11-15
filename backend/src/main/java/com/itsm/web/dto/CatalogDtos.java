package com.itsm.web.dto;

import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.incident.Priority;
import com.itsm.web.dto.CommonDtos.Ref;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/** Categories, IT services, SLA policies and strategy settings. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    private static final String CODE_RULE = "^[A-Za-z][A-Za-z0-9_]{1,39}$";
    private static final String CODE_MESSAGE = "Code must be 2-40 letters, digits or underscores, starting with a letter";

    public record CategoryDto(Long id, String code, String name, String description, List<String> keywords, boolean active) {}

    public record CategoryRequest(
            @NotBlank @Pattern(regexp = CODE_RULE, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            Set<@NotBlank @Size(max = 60) String> keywords,
            Boolean active) {}

    public record ServiceDto(
            Long id, String code, String name, String description, BusinessCriticality criticality,
            String ownerTeam, Ref defaultCategory, List<Ref> dependencies, boolean active) {}

    public record ServiceRequest(
            @NotBlank @Pattern(regexp = CODE_RULE, message = CODE_MESSAGE) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull BusinessCriticality criticality,
            @Size(max = 80) String ownerTeam,
            Long defaultCategoryId,
            Set<Long> dependencyIds,
            Boolean active) {}

    public record SlaPolicyDto(
            Long id, String name, Priority priority, int responseMinutes, int resolutionMinutes,
            int warningThresholdPercent, Ref service, boolean active) {}

    public record SlaPolicyRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull Priority priority,
            @NotNull @Min(1) @Max(100_000) Integer responseMinutes,
            @NotNull @Min(1) @Max(500_000) Integer resolutionMinutes,
            @NotNull @Min(10) @Max(95) Integer warningThresholdPercent,
            Long serviceId,
            Boolean active) {}

    public record StrategyOption(String key, String displayName, String description) {}

    public record StrategySettings(
            String activePriorityStrategy,
            String activeAssignmentStrategy,
            boolean autoAssign,
            List<StrategyOption> priorityStrategies,
            List<StrategyOption> assignmentStrategies) {}

    public record StrategySettingsRequest(
            @NotBlank String priorityStrategy,
            @NotBlank String assignmentStrategy,
            boolean autoAssign) {}
}
