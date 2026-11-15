package com.itsm.domain.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A senior technician who leads a team and is the first escalation point.
 *
 * // [OOP: Inheritance] TeamLead "is-a" Technician (multi-level hierarchy: User -> Technician -> TeamLead).
 * // [OOP: Polymorphism] Extends the inherited permission set using super.permissions().
 * // [OOP: Aggregation] A team lead aggregates technicians who exist independently of the team.
 */
@Entity
@DiscriminatorValue("TEAM_LEAD")
public class TeamLead extends Technician {

    @OneToMany(mappedBy = "teamLead")
    private List<Technician> teamMembers = new ArrayList<>();

    protected TeamLead() {
    }

    public TeamLead(String userCode, String fullName, String email, String passwordHash, String department,
                    int experienceYears, int maxConcurrentIncidents) {
        super(userCode, fullName, email, passwordHash, department, experienceYears, maxConcurrentIncidents);
    }

    @Override
    public Role getRole() {
        return Role.TEAM_LEAD;
    }

    @Override
    public Set<Permission> permissions() {
        EnumSet<Permission> extended = EnumSet.copyOf(super.permissions());
        extended.add(Permission.ASSIGN_INCIDENT);
        extended.add(Permission.VIEW_TEAM_WORKLOAD);
        extended.add(Permission.RECEIVE_ESCALATIONS);
        extended.add(Permission.VIEW_REPORTS);
        return Collections.unmodifiableSet(extended);
    }

    public List<Technician> getTeamMembers() {
        return Collections.unmodifiableList(teamMembers);
    }
}
