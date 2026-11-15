package com.itsm.engine.escalation;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.user.Role;
import com.itsm.domain.user.User;
import com.itsm.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

/** Level 2: system administrators (excluding IT managers, who form level 3). */
public class AdministratorEscalationHandler extends EscalationHandler {

    private final UserRepository userRepository;

    public AdministratorEscalationHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected int level() {
        return 2;
    }

    @Override
    protected String levelName() {
        return "System Administrator";
    }

    @Override
    protected List<User> recipients(Incident incident) {
        return new ArrayList<>(userRepository.findActiveAdministrators().stream()
                .filter(a -> a.getRole() == Role.ADMIN).toList());
    }
}
