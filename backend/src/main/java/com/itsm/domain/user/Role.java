package com.itsm.domain.user;

/** User categories. Each maps to a concrete subclass of {@link User}. */
public enum Role {
    EMPLOYEE,
    TECHNICIAN,
    TEAM_LEAD,
    ADMIN,
    IT_MANAGER;

    public boolean isSupportStaff() {
        return this == TECHNICIAN || this == TEAM_LEAD;
    }

    public boolean isAdministrative() {
        return this == ADMIN || this == IT_MANAGER;
    }
}
