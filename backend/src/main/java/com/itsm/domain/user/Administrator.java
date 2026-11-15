package com.itsm.domain.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * IT administrator: configures the platform (users, services, SLAs, strategies) and oversees incidents.
 * // [OOP: Inheritance] Administrator "is-a" User.
 */
@Entity
@DiscriminatorValue("ADMIN")
public class Administrator extends User {

    private static final Set<Permission> PERMISSIONS = immutable(EnumSet.of(
            Permission.REPORT_INCIDENT,
            Permission.VIEW_OWN_INCIDENTS,
            Permission.VIEW_ALL_INCIDENTS,
            Permission.ASSIGN_INCIDENT,
            Permission.CLOSE_INCIDENT,
            Permission.VIEW_TEAM_WORKLOAD,
            Permission.MANAGE_USERS,
            Permission.MANAGE_CATALOG,
            Permission.MANAGE_SLA,
            Permission.CONFIGURE_STRATEGY,
            Permission.VIEW_REPORTS,
            Permission.VIEW_AUDIT,
            Permission.RECEIVE_ESCALATIONS));

    protected Administrator() {
    }

    public Administrator(String userCode, String fullName, String email, String passwordHash, String department) {
        super(userCode, fullName, email, passwordHash, department);
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

    @Override
    public Set<Permission> permissions() {
        return PERMISSIONS;
    }
}
