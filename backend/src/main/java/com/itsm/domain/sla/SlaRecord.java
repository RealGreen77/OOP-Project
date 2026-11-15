package com.itsm.domain.sla;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Duration;
import java.time.Instant;

/**
 * The SLA clock attached to one incident: targets, deadlines, pause handling and breach flags.
 *
 * // [OOP: Composition] Embedded in Incident; it has no identity or life outside its incident.
 * // [OOP: Encapsulation] Deadlines can only move through pause()/resume(); breach flags are one-way.
 *
 * The clock pauses while the incident waits on the reporter (PENDING_INFO) and the deadlines are
 * pushed back by the paused duration on resume, as in real ITSM tools.
 */
@Embeddable
public class SlaRecord {

    @Column(name = "sla_policy_name", length = 100)
    private String policyName;

    @Column(name = "sla_response_minutes")
    private Integer responseTargetMinutes;

    @Column(name = "sla_resolution_minutes")
    private Integer resolutionTargetMinutes;

    @Column(name = "sla_warning_pct")
    private Integer warningThresholdPercent;

    @Column(name = "sla_started_at")
    private Instant startedAt;

    @Column(name = "sla_response_deadline")
    private Instant responseDeadline;

    @Column(name = "sla_resolution_deadline")
    private Instant resolutionDeadline;

    @Column(name = "sla_responded_at")
    private Instant respondedAt;

    @Column(name = "sla_response_breached")
    private Boolean responseBreached = false;

    @Column(name = "sla_resolution_breached")
    private Boolean resolutionBreached = false;

    @Column(name = "sla_warning_issued")
    private Boolean warningIssued = false;

    @Column(name = "sla_paused_at")
    private Instant pausedAt;

    @Column(name = "sla_paused_seconds")
    private Long totalPausedSeconds = 0L;

    protected SlaRecord() {
    }

    /** Starts a new SLA clock from the given policy. // [Pattern: Factory Method] */
    public static SlaRecord start(SlaPolicy policy, Instant from) {
        SlaRecord r = new SlaRecord();
        r.policyName = policy.getName();
        r.responseTargetMinutes = policy.getResponseMinutes();
        r.resolutionTargetMinutes = policy.getResolutionMinutes();
        r.warningThresholdPercent = policy.getWarningThresholdPercent();
        r.startedAt = from;
        r.responseDeadline = from.plus(policy.responseTarget());
        r.resolutionDeadline = from.plus(policy.resolutionTarget());
        return r;
    }

    /**
     * Re-targets the clock (e.g. after the priority changes) keeping the original start time,
     * the response state and paused time.
     */
    public SlaRecord retarget(SlaPolicy policy) {
        SlaRecord r = start(policy, startedAt);
        long paused = getTotalPausedSeconds();
        r.responseDeadline = r.responseDeadline.plusSeconds(paused);
        r.resolutionDeadline = r.resolutionDeadline.plusSeconds(paused);
        r.totalPausedSeconds = paused;
        r.pausedAt = pausedAt;
        r.respondedAt = respondedAt;
        r.responseBreached = isResponseBreached();
        r.resolutionBreached = isResolutionBreached();
        return r;
    }

    // ---------------------------------------------------------------- clock control

    public void recordResponse(Instant at) {
        if (respondedAt == null) {
            respondedAt = at;
            if (at.isAfter(responseDeadline)) responseBreached = true;
        }
    }

    public void pause(Instant at) {
        if (pausedAt == null) pausedAt = at;
    }

    public void resume(Instant at) {
        if (pausedAt != null) {
            long seconds = Math.max(0, Duration.between(pausedAt, at).getSeconds());
            totalPausedSeconds = getTotalPausedSeconds() + seconds;
            resolutionDeadline = resolutionDeadline.plusSeconds(seconds);
            if (respondedAt == null) responseDeadline = responseDeadline.plusSeconds(seconds);
            pausedAt = null;
        }
    }

    /** @return true if the response breach is newly detected */
    public boolean flagResponseBreachIfDue(Instant now) {
        if (!isResponseBreached() && respondedAt == null && !isPaused() && now.isAfter(responseDeadline)) {
            responseBreached = true;
            return true;
        }
        return false;
    }

    /** @return true if the resolution breach is newly detected */
    public boolean flagResolutionBreachIfDue(Instant now) {
        if (!isResolutionBreached() && !isPaused() && now.isAfter(resolutionDeadline)) {
            resolutionBreached = true;
            return true;
        }
        return false;
    }

    /** Marks a resolution breach if the incident was resolved after its deadline. */
    public void finaliseResolution(Instant resolvedAt) {
        if (resolvedAt.isAfter(resolutionDeadline)) resolutionBreached = true;
    }

    /** @return true if the pre-breach warning should be raised now (only once). */
    public boolean shouldWarn(Instant now) {
        if (Boolean.TRUE.equals(warningIssued) || isResolutionBreached() || isPaused()) return false;
        if (elapsedFraction(now) >= getWarningThresholdPercent() / 100.0) {
            warningIssued = true;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- queries

    /** Fraction of the resolution window used, excluding paused time. */
    public double elapsedFraction(Instant now) {
        Instant effectiveNow = isPaused() ? pausedAt : now;
        long total = Duration.between(startedAt, resolutionDeadline).getSeconds() - getTotalPausedSeconds();
        long used = Duration.between(startedAt, effectiveNow).getSeconds() - getTotalPausedSeconds();
        return total <= 0 ? 1.0 : Math.max(0.0, (double) used / total);
    }

    public Duration remaining(Instant now) {
        Instant effectiveNow = isPaused() ? pausedAt : now;
        return Duration.between(effectiveNow, resolutionDeadline);
    }

    public SlaStatus status(Instant now, Instant resolvedAt) {
        if (resolvedAt != null) {
            return isResolutionBreached() || resolvedAt.isAfter(resolutionDeadline) ? SlaStatus.BREACHED : SlaStatus.MET;
        }
        if (isResolutionBreached() || isResponseBreached() || now.isAfter(resolutionDeadline)) return SlaStatus.BREACHED;
        if (isPaused()) return SlaStatus.PAUSED;
        if (elapsedFraction(now) >= getWarningThresholdPercent() / 100.0) return SlaStatus.AT_RISK;
        return SlaStatus.ON_TRACK;
    }

    public boolean isPaused() { return pausedAt != null; }
    public boolean isResponseBreached() { return Boolean.TRUE.equals(responseBreached); }
    public boolean isResolutionBreached() { return Boolean.TRUE.equals(resolutionBreached); }
    public boolean isBreached() { return isResponseBreached() || isResolutionBreached(); }
    public boolean isWarningIssued() { return Boolean.TRUE.equals(warningIssued); }
    public long getTotalPausedSeconds() { return totalPausedSeconds == null ? 0 : totalPausedSeconds; }
    public int getWarningThresholdPercent() { return warningThresholdPercent == null ? 75 : warningThresholdPercent; }
    public String getPolicyName() { return policyName; }
    public Integer getResponseTargetMinutes() { return responseTargetMinutes; }
    public Integer getResolutionTargetMinutes() { return resolutionTargetMinutes; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResponseDeadline() { return responseDeadline; }
    public Instant getResolutionDeadline() { return resolutionDeadline; }
    public Instant getRespondedAt() { return respondedAt; }
    public Instant getPausedAt() { return pausedAt; }
}
