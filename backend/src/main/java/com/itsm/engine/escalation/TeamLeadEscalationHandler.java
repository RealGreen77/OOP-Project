package com.itsm.engine.escalation;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.user.TeamLead;
import com.itsm.domain.user.User;
import com.itsm.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

/** Level 1: the assigned technician's team lead (or every active team lead if there is none). */
public class TeamLeadEscalationHandler extends EscalationHandler {

    private final UserRepository userRepository;

    public TeamLeadEscalationHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected int level() {
        return 1;
    }

    @Override
    protected String levelName() {
        return "Team Lead";
    }

    @Override
    protected List<User> recipients(Incident incident) {
        TeamLead lead = incident.getAssignedTo() == null ? null : incident.getAssignedTo().getTeamLead();
        if (lead != null && lead.isActive() && !incident.isAssignedTo(lead)) {
            return List.of(lead);
        }
        return new ArrayList<>(userRepository.findActiveTeamLeads().stream()
                .filter(t -> !incident.isAssignedTo(t)).toList());
    }
}
