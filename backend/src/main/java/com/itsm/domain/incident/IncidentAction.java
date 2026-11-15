package com.itsm.domain.incident;

/** Operations that move an incident through its lifecycle. Used for state validation and UI hints. */
public enum IncidentAction {
    CLASSIFY,
    ASSIGN,
    REASSIGN,
    ACKNOWLEDGE,
    START_WORK,
    REQUEST_INFO,
    PROVIDE_INFO,
    RESOLVE,
    VERIFY,
    REOPEN,
    CLOSE,
    MARK_DUPLICATE,
    UPDATE_DETAILS,
    ADD_NOTE,
    FEEDBACK
}
