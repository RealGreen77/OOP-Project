package com.itsm.domain.incident;

import com.itsm.domain.common.RatedLevel;

/** How quickly the business needs the incident to be resolved. */
public enum Urgency implements RatedLevel {
    LOW(1, "Low - can wait"),
    MEDIUM(2, "Medium - needed within the day"),
    HIGH(3, "High - work is blocked"),
    CRITICAL(4, "Critical - immediate business loss");

    private final int level;
    private final String label;

    Urgency(int level, String label) {
        this.level = level;
        this.label = label;
    }

    @Override public int level() { return level; }
    @Override public String label() { return label; }
}
