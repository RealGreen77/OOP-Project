package com.itsm.domain.user;

import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * A support engineer who investigates and resolves incidents.
 *
 * // [OOP: Inheritance] Technician "is-a" User; TeamLead further specialises Technician.
 * // [OOP: Association] Skills (Category) and service expertise (ITService) are many-to-many associations.
 * // [OOP: Aggregation] A technician optionally belongs to a TeamLead's team; both exist independently.
 * // [Collections] Set is used for skills because membership checks must be O(1) and duplicates meaningless.
 */
@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    private static final Set<Permission> PERMISSIONS = immutable(EnumSet.of(
            Permission.REPORT_INCIDENT,
            Permission.VIEW_OWN_INCIDENTS,
            Permission.VIEW_ALL_INCIDENTS,
            Permission.WORK_INCIDENT));

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "technician_skills",
            joinColumns = @JoinColumn(name = "technician_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> skills = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "technician_services",
            joinColumns = @JoinColumn(name = "technician_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<ITService> serviceExpertise = new HashSet<>();

    @Column(name = "experience_years")
    private Integer experienceYears = 0;

    @Column(name = "max_concurrent_incidents")
    private Integer maxConcurrentIncidents = 5;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AvailabilityStatus availability = AvailabilityStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_lead_id")
    private TeamLead teamLead;

    protected Technician() {
    }

    public Technician(String userCode, String fullName, String email, String passwordHash, String department,
                      int experienceYears, int maxConcurrentIncidents) {
        super(userCode, fullName, email, passwordHash, department);
        updateCapacity(experienceYears, maxConcurrentIncidents);
    }

    @Override
    public Role getRole() {
        return Role.TECHNICIAN;
    }

    @Override
    public Set<Permission> permissions() {
        return PERMISSIONS;
    }

    // ---------------------------------------------------------------- domain behaviour

    public boolean hasSkill(Category category) {
        return category != null && skills.contains(category);
    }

    public boolean hasExpertiseIn(ITService service) {
        return service != null && serviceExpertise.contains(service);
    }

    /** True if the technician is available and below their concurrent-incident ceiling. */
    public boolean hasCapacity(int currentActiveIncidents) {
        return isActive() && getAvailability().canTakeWork() && currentActiveIncidents < getMaxConcurrentIncidents();
    }

    /** Fraction of capacity in use, 0.0 (idle) .. 1.0+ (saturated). */
    public double loadRatio(int currentActiveIncidents) {
        return getMaxConcurrentIncidents() == 0 ? 1.0 : (double) currentActiveIncidents / getMaxConcurrentIncidents();
    }

    public void replaceSkills(Set<Category> newSkills) {
        skills.clear();
        if (newSkills != null) skills.addAll(newSkills);
    }

    public void replaceServiceExpertise(Set<ITService> services) {
        serviceExpertise.clear();
        if (services != null) serviceExpertise.addAll(services);
    }

    public void updateCapacity(int experienceYears, int maxConcurrentIncidents) {
        if (experienceYears < 0) throw new InvalidInputException("experienceYears must be >= 0");
        if (maxConcurrentIncidents < 1 || maxConcurrentIncidents > 50) {
            throw new InvalidInputException("maxConcurrentIncidents must be between 1 and 50");
        }
        this.experienceYears = experienceYears;
        this.maxConcurrentIncidents = maxConcurrentIncidents;
    }

    public void changeAvailability(AvailabilityStatus availability) {
        this.availability = availability == null ? AvailabilityStatus.AVAILABLE : availability;
    }

    public void joinTeam(TeamLead lead) {
        if (lead == this) throw new InvalidInputException("A team lead cannot be their own team member");
        this.teamLead = lead;
    }

    // ---------------------------------------------------------------- accessors (read-only views)

    public Set<Category> getSkills() { return Collections.unmodifiableSet(skills); }
    public Set<ITService> getServiceExpertise() { return Collections.unmodifiableSet(serviceExpertise); }
    public int getExperienceYears() { return experienceYears == null ? 0 : experienceYears; }
    public int getMaxConcurrentIncidents() { return maxConcurrentIncidents == null ? 5 : maxConcurrentIncidents; }
    public AvailabilityStatus getAvailability() { return availability == null ? AvailabilityStatus.AVAILABLE : availability; }
    public TeamLead getTeamLead() { return teamLead; }
}
