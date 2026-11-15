package com.itsm.domain.incident;

import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Resolution details recorded by the technician.
 * // [OOP: Composition] Immutable value object owned by an Incident.
 */
@Embeddable
public class Resolution {

    @Column(name = "resolution_summary", length = 2000)
    private String summary;

    @Column(name = "resolution_root_cause", length = 1000)
    private String rootCause;

    @Column(name = "resolution_workaround")
    private Boolean workaround = false;

    protected Resolution() {
    }

    public Resolution(String summary, String rootCause, boolean workaround) {
        if (summary == null || summary.trim().length() < 10) {
            throw new InvalidInputException("Resolution summary must be at least 10 characters");
        }
        this.summary = summary.trim();
        this.rootCause = rootCause == null ? null : rootCause.trim();
        this.workaround = workaround;
    }

    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public boolean isWorkaround() { return Boolean.TRUE.equals(workaround); }
}
