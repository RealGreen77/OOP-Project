package com.itsm.domain.incident;

/**
 * Incident priority bands. P1 is the most severe.
 * // [MVP: Priority Management]
 */
public enum Priority {
    P1("Critical"),
    P2("High"),
    P3("Medium"),
    P4("Low");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True for priorities that warrant urgent channels (SMS) and automatic escalation. */
    public boolean isUrgent() {
        return this == P1 || this == P2;
    }

    public String display() {
        return name() + " - " + label;
    }
}
