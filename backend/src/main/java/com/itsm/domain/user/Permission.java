package com.itsm.domain.user;

/**
 * Fine-grained capabilities. Each {@link User} subclass declares its own permission set
 * polymorphically; Spring Security authorities are derived from these.
 */
public enum Permission {
    REPORT_INCIDENT,
    VIEW_OWN_INCIDENTS,
    VIEW_ALL_INCIDENTS,
    WORK_INCIDENT,
    ASSIGN_INCIDENT,
    CLOSE_INCIDENT,
    VIEW_TEAM_WORKLOAD,
    MANAGE_USERS,
    MANAGE_CATALOG,
    MANAGE_SLA,
    CONFIGURE_STRATEGY,
    VIEW_REPORTS,
    VIEW_AUDIT,
    RECEIVE_ESCALATIONS
}
