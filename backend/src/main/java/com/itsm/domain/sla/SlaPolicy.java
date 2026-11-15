package com.itsm.domain.sla;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.common.BaseEntity;
import com.itsm.domain.incident.Priority;
import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;

/**
 * Response/resolution targets for a priority. A policy with no service is the global default;
 * a service-specific policy overrides it (different services may have different SLAs).
 * // [MVP: SLA Management]
 * // [OOP: Aggregation] An SLA policy optionally references an ITService; neither owns the other.
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Priority priority;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    /** Percentage of the resolution window after which a warning is raised. */
    @Column(name = "warning_threshold_pct", nullable = false)
    private int warningThresholdPercent = 75;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ITService service;

    @Column(nullable = false)
    private boolean active = true;

    protected SlaPolicy() {
    }

    public SlaPolicy(String name, Priority priority, int responseMinutes, int resolutionMinutes,
                     int warningThresholdPercent, ITService service) {
        update(name, priority, responseMinutes, resolutionMinutes, warningThresholdPercent);
        this.service = service;
    }

    public void update(String name, Priority priority, int responseMinutes, int resolutionMinutes,
                       int warningThresholdPercent) {
        if (name == null || name.isBlank()) throw new InvalidInputException("SLA policy name is required");
        if (priority == null) throw new InvalidInputException("SLA priority is required");
        if (responseMinutes <= 0) throw new InvalidInputException("Response target must be positive");
        if (resolutionMinutes < responseMinutes) {
            throw new InvalidInputException("Resolution target must be >= response target");
        }
        if (warningThresholdPercent < 10 || warningThresholdPercent > 95) {
            throw new InvalidInputException("Warning threshold must be between 10% and 95%");
        }
        this.name = name.trim();
        this.priority = priority;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.warningThresholdPercent = warningThresholdPercent;
    }

    public boolean isGlobalDefault() {
        return service == null;
    }

    public Duration responseTarget() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration resolutionTarget() {
        return Duration.ofMinutes(resolutionMinutes);
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }

    public String getName() { return name; }
    public Priority getPriority() { return priority; }
    public int getResponseMinutes() { return responseMinutes; }
    public int getResolutionMinutes() { return resolutionMinutes; }
    public int getWarningThresholdPercent() { return warningThresholdPercent; }
    public ITService getService() { return service; }
    public boolean isActive() { return active; }
}
