package com.itsm.domain.common;

/**
 * Contract for any ordinal rating used by the prioritisation engine
 * (impact, urgency, business criticality).
 *
 * // [OOP: Interfaces] A small, focused contract implemented by several enums so that
 * // priority strategies can treat every rating uniformly (programming to an interface).
 */
public interface RatedLevel {

    /** Numeric weight on a 1..{@link #MAX_LEVEL} scale. */
    int level();

    /** Human friendly label for UI / audit messages. */
    String label();

    int MAX_LEVEL = 4;

    /** Normalised weight in the range (0, 1]. */
    default double normalised() {
        return (double) level() / MAX_LEVEL;
    }
}
