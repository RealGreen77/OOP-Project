package com.itsm.domain.catalog;

import com.itsm.domain.common.BaseEntity;
import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A business IT service (Payment Gateway, Email, VPN ...).
 *
 * // [OOP: Association] Self-referencing many-to-many "depends on" association forms a service
 * //                   dependency graph, used by incident correlation to find probable root causes.
 * // [Collections] ArrayDeque used for breadth-first traversal of the dependency graph.
 */
@Entity
@Table(name = "it_services")
public class ITService extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessCriticality criticality;

    @Column(name = "owner_team", length = 80)
    private String ownerTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_category_id")
    private Category defaultCategory;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_dependencies",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_id"))
    private Set<ITService> dependencies = new HashSet<>();

    @Column(nullable = false)
    private boolean active = true;

    protected ITService() {
    }

    public ITService(String code, String name, String description, BusinessCriticality criticality,
                     String ownerTeam, Category defaultCategory) {
        update(code, name, description, criticality, ownerTeam, defaultCategory);
    }

    public void update(String code, String name, String description, BusinessCriticality criticality,
                       String ownerTeam, Category defaultCategory) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_]{1,39}")) {
            throw new InvalidInputException("Service code must be 2-40 letters/digits/underscores");
        }
        if (name == null || name.isBlank()) throw new InvalidInputException("Service name is required");
        if (criticality == null) throw new InvalidInputException("Business criticality is required");
        this.code = code.toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.description = description;
        this.criticality = criticality;
        this.ownerTeam = ownerTeam;
        this.defaultCategory = defaultCategory;
    }

    /** Adds a dependency, rejecting self-references and cycles to keep the graph a DAG. */
    public void addDependency(ITService other) {
        if (other == null) return;
        if (other == this || other.transitiveDependencies().contains(this)) {
            throw new InvalidInputException("Dependency " + name + " -> " + other.getName() + " would create a cycle");
        }
        dependencies.add(other);
    }

    public void clearDependencies() {
        dependencies.clear();
    }

    /** All services this one depends on, directly or indirectly (breadth-first). */
    public Set<ITService> transitiveDependencies() {
        Set<ITService> visited = new LinkedHashSet<>();
        Deque<ITService> queue = new ArrayDeque<>(dependencies);
        while (!queue.isEmpty()) {
            ITService next = queue.poll();
            if (visited.add(next)) {
                queue.addAll(next.dependencies);
            }
        }
        return visited;
    }

    public boolean dependsOn(ITService other) {
        return transitiveDependencies().contains(other);
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BusinessCriticality getCriticality() { return criticality; }
    public String getOwnerTeam() { return ownerTeam; }
    public Category getDefaultCategory() { return defaultCategory; }
    public Set<ITService> getDependencies() { return Collections.unmodifiableSet(dependencies); }
    public boolean isActive() { return active; }
}
