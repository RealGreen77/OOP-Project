package com.itsm.domain.catalog;

import com.itsm.domain.common.RatedLevel;

/** How important an IT service is to the organisation. */
public enum BusinessCriticality implements RatedLevel {
    LOW(1, "Low - internal convenience service"),
    MEDIUM(2, "Medium - supports daily operations"),
    HIGH(3, "High - core business process"),
    MISSION_CRITICAL(4, "Mission critical - revenue / customer facing");

    private final int level;
    private final String label;

    BusinessCriticality(int level, String label) {
        this.level = level;
        this.label = label;
    }

    @Override public int level() { return level; }
    @Override public String label() { return label; }
}
