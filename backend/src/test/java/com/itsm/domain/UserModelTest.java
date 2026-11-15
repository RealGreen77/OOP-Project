package com.itsm.domain;

import com.itsm.TestData;
import com.itsm.domain.user.Administrator;
import com.itsm.domain.user.ITManager;
import com.itsm.domain.user.Permission;
import com.itsm.domain.user.Role;
import com.itsm.domain.user.TeamLead;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.domain.user.UserFactory;
import com.itsm.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.itsm.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Inheritance, polymorphism, factory and encapsulated security state of the user hierarchy. */
class UserModelTest {

    @Test
    @DisplayName("Factory + polymorphism: each role yields the right subclass with its own permissions")
    void factoryAndPermissions() {
        User employee = UserFactory.create(Role.EMPLOYEE, "E1", "Emp", "e@x.io", "h", null, null, null);
        User lead = UserFactory.create(Role.TEAM_LEAD, "L1", "Lead", "l@x.io", "h", null, 9, 3);
        User manager = UserFactory.create(Role.IT_MANAGER, "M1", "Mgr", "m@x.io", "h", null, null, null);

        assertThat(lead).isInstanceOf(TeamLead.class).isInstanceOf(Technician.class);
        assertThat(manager).isInstanceOf(ITManager.class).isInstanceOf(Administrator.class);

        assertThat(employee.can(Permission.REPORT_INCIDENT)).isTrue();
        assertThat(employee.can(Permission.VIEW_ALL_INCIDENTS)).isFalse();
        assertThat(lead.can(Permission.WORK_INCIDENT)).isTrue();        // inherited from Technician
        assertThat(lead.can(Permission.ASSIGN_INCIDENT)).isTrue();      // added by TeamLead
        assertThat(manager.can(Permission.MANAGE_USERS)).isTrue();      // inherited from Administrator
        assertThat(manager.getRole()).isEqualTo(Role.IT_MANAGER);
    }

    @Test
    @DisplayName("Deactivated users lose every permission")
    void deactivatedHasNoPermissions() {
        User admin = UserFactory.create(Role.ADMIN, "A1", "Admin", "a@x.io", "h", null, null, null);
        admin.deactivate();
        assertThat(admin.can(Permission.MANAGE_USERS)).isFalse();
    }

    @Test
    @DisplayName("TC-10 Invalid login lockout: account locks after 5 failures and unlocks after the lock period")
    void lockout() {
        User user = TestData.employee("E9");
        for (int i = 1; i <= 4; i++) {
            assertThat(user.recordFailedLogin(T0, 5, Duration.ofMinutes(15))).isFalse();
        }
        assertThat(user.recordFailedLogin(T0, 5, Duration.ofMinutes(15))).isTrue();
        assertThat(user.isLocked(T0.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(T0.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    @DisplayName("Boundary: technician capacity must be 1-50 and experience non-negative")
    void technicianCapacityValidation() {
        Technician t = TestData.technician("T9", 2, 5);
        assertThatThrownBy(() -> t.updateCapacity(2, 0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> t.updateCapacity(-1, 5)).isInstanceOf(InvalidInputException.class);
        t.updateCapacity(0, 50);
        assertThat(t.getMaxConcurrentIncidents()).isEqualTo(50);
        assertThat(t.hasCapacity(49)).isTrue();
        assertThat(t.hasCapacity(50)).isFalse();
    }
}
