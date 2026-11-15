package com.itsm.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Strongly-typed, validated application configuration bound from {@code itsm.*} properties.
 * Invalid configuration fails fast at start-up instead of at first use.
 */
@Validated
@ConfigurationProperties(prefix = "itsm")
public record ItsmProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Security security,
        @Valid @NotNull Sla sla,
        @Valid @NotNull Intelligence intelligence,
        @Valid @NotNull Notifications notifications,
        @NotNull List<String> corsAllowedOrigins,
        boolean seedDemoData) {

    public record Jwt(@NotBlank String secret, @Min(5) long expirationMinutes, @NotBlank String issuer) {
    }

    public record Security(@Min(1) int maxFailedLogins, @Min(1) int lockMinutes) {
    }

    public record Sla(@Min(5_000) long monitorIntervalMs, boolean autoEscalation) {
    }

    public record Intelligence(@Min(1) int duplicateWindowHours, @Min(1) int correlationWindowHours,
                               @Min(2) int majorIncidentThreshold) {
    }

    public record Notifications(@NotBlank String emailMode, @NotBlank String fromAddress, boolean smsEnabled,
                                List<String> simulatedEmailDomains,
                                @NotBlank String frontendBaseUrl) {
    }
}
