package com.itsm.events;

/**
 * Observer side of the Observer pattern. Any Spring bean implementing this interface is registered
 * automatically - adding a new reaction to incident events requires no change to existing code.
 */
public interface IncidentEventListener {

    void onEvent(IncidentEvent event);

    /** Lower values run first. */
    default int order() {
        return 100;
    }

    /**
     * Critical listeners (e.g. audit) propagate failures so the whole transaction rolls back;
     * non-critical ones (notifications) are isolated so a failing e-mail server cannot block incident work.
     */
    default boolean critical() {
        return false;
    }
}
