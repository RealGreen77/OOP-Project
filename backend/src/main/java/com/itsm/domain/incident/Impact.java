package com.itsm.domain.incident;

import com.itsm.domain.common.RatedLevel;

/** How widely an incident affects the business. */
public enum Impact implements RatedLevel {
    LOW(1, "Low - single user, workaround available"),
    MEDIUM(2, "Medium - a team or department affected"),
    HIGH(3, "High - multiple departments affected"),
    CRITICAL(4, "Critical - organisation-wide / customer facing");

    private final int level;
    private final String label;

    Impact(int level, String label) {
        this.level = level;
        this.label = label;
    }

    @Override public int level() { return level; }
    @Override public String label() { return label; }
}
