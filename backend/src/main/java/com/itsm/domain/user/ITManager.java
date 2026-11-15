package com.itsm.domain.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * IT Manager: an administrator who is the final escalation level for critical incidents.
 *
 * // [OOP: Inheritance] ITManager "is-a" Administrator - it inherits every administrative permission
 * //                    unchanged and only refines its role identity (Liskov-substitutable).
 */
@Entity
@DiscriminatorValue("IT_MANAGER")
public class ITManager extends Administrator {

    protected ITManager() {
    }

    public ITManager(String userCode, String fullName, String email, String passwordHash, String department) {
        super(userCode, fullName, email, passwordHash, department);
    }

    @Override
    public Role getRole() {
        return Role.IT_MANAGER;
    }
}
