package com.itsm.domain.sla;

/** Computed SLA health of an incident. */
public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    PAUSED,
    BREACHED,
    MET,
    NOT_APPLICABLE
}
