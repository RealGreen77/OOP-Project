package com.itsm.engine;

/**
 * Common contract of every pluggable algorithm (priority calculation, technician assignment).
 * // [OOP: Interfaces] Lets a single generic registry manage any family of strategies.
 */
public interface NamedStrategy {

    /** Stable identifier stored in configuration (e.g. "skill-based"). */
    String key();

    /** Name shown to administrators. */
    String displayName();

    /** One-line explanation of how the algorithm works. */
    String description();
}
