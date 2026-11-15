package com.itsm.engine.escalation;

import com.itsm.domain.incident.Incident;
import com.itsm.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Assembles the chain once and exposes a single entry point. */
@Component
public class EscalationChain {

    public static final int MAX_LEVEL = 3;

    private final EscalationHandler head;

    public EscalationChain(UserRepository userRepository) {
        this.head = new TeamLeadEscalationHandler(userRepository);
        head.linkWith(new AdministratorEscalationHandler(userRepository))
            .linkWith(new ITManagerEscalationHandler(userRepository));
    }

    public Optional<EscalationTarget> escalate(Incident incident, int targetLevel) {
        if (targetLevel < 1 || targetLevel > MAX_LEVEL) return Optional.empty();
        return head.escalate(incident, targetLevel);
    }
}
