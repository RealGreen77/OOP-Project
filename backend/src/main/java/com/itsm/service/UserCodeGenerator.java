package com.itsm.service;

import com.itsm.domain.user.Role;
import com.itsm.domain.user.UserFactory;
import com.itsm.repository.UserRepository;
import org.springframework.stereotype.Component;

/** Generates unique, role-prefixed user codes (E101, T201, L301, A001, M001). */
@Component
public class UserCodeGenerator {

    private final UserRepository userRepository;

    public UserCodeGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String next(Role role) {
        String prefix = UserFactory.codePrefix(role);
        int base = switch (role) {
            case EMPLOYEE -> 101;
            case TECHNICIAN -> 201;
            case TEAM_LEAD -> 301;
            case ADMIN, IT_MANAGER -> 1;
        };
        long n = base + userRepository.countByType(UserFactory.typeFor(role));
        String code;
        do {
            code = prefix + (n < 100 ? String.format("%03d", n) : String.valueOf(n));
            n++;
        } while (userRepository.existsByUserCode(code));
        return code;
    }
}
