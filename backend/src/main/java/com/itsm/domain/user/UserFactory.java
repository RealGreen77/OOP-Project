package com.itsm.domain.user;

import com.itsm.exception.InvalidInputException;

/**
 * Creates the correct {@link User} subclass for a role.
 *
 * // [Pattern: Factory] Callers (registration, admin user management, data seeding) ask for "a user with role X"
 * //                   and receive the right concrete type without knowing the class hierarchy.
 * // [OOP: Polymorphism] The return type is the abstract User.
 */
public final class UserFactory {

    public static final int DEFAULT_EXPERIENCE = 1;
    public static final int DEFAULT_MAX_INCIDENTS = 5;

    private UserFactory() {
    }

    public static User create(Role role, String userCode, String fullName, String email, String passwordHash,
                              String department, Integer experienceYears, Integer maxConcurrentIncidents) {
        if (role == null) throw new InvalidInputException("Role is required");
        int exp = experienceYears == null ? DEFAULT_EXPERIENCE : experienceYears;
        int max = maxConcurrentIncidents == null ? DEFAULT_MAX_INCIDENTS : maxConcurrentIncidents;
        return switch (role) {
            case EMPLOYEE -> new Employee(userCode, fullName, email, passwordHash, department);
            case TECHNICIAN -> new Technician(userCode, fullName, email, passwordHash, department, exp, max);
            case TEAM_LEAD -> new TeamLead(userCode, fullName, email, passwordHash, department, exp, max);
            case ADMIN -> new Administrator(userCode, fullName, email, passwordHash, department);
            case IT_MANAGER -> new ITManager(userCode, fullName, email, passwordHash, department);
        };
    }

    /** Prefix for human-readable user codes: E102, T205, L301, A001, M001. */
    public static String codePrefix(Role role) {
        return switch (role) {
            case EMPLOYEE -> "E";
            case TECHNICIAN -> "T";
            case TEAM_LEAD -> "L";
            case ADMIN -> "A";
            case IT_MANAGER -> "M";
        };
    }

    public static Class<? extends User> typeFor(Role role) {
        return switch (role) {
            case EMPLOYEE -> Employee.class;
            case TECHNICIAN -> Technician.class;
            case TEAM_LEAD -> TeamLead.class;
            case ADMIN -> Administrator.class;
            case IT_MANAGER -> ITManager.class;
        };
    }
}
