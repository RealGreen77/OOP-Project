package com.itsm.domain.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * An employee / customer who reports incidents and tracks them.
 * // [OOP: Inheritance] Employee "is-a" User.
 */
@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    private static final Set<Permission> PERMISSIONS = immutable(EnumSet.of(
            Permission.REPORT_INCIDENT,
            Permission.VIEW_OWN_INCIDENTS));

    protected Employee() {
    }

    public Employee(String userCode, String fullName, String email, String passwordHash, String department) {
        super(userCode, fullName, email, passwordHash, department);
    }

    // [OOP: Polymorphism] Overrides the abstract role/permission contract.
    @Override
    public Role getRole() {
        return Role.EMPLOYEE;
    }

    @Override
    public Set<Permission> permissions() {
        return PERMISSIONS;
    }
}
