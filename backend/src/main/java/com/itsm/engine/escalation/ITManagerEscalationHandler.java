package com.itsm.engine.escalation;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.user.User;
import com.itsm.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

/** Level 3 (final): IT managers. */
public class ITManagerEscalationHandler extends EscalationHandler {

    private final UserRepository userRepository;

    public ITManagerEscalationHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected int level() {
        return 3;
    }

    @Override
    protected String levelName() {
        return "IT Manager";
    }

    @Override
    protected List<User> recipients(Incident incident) {
        return new ArrayList<>(userRepository.findActiveItManagers());
    }
}
