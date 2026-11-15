package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups open incidents by a probable common service or infrastructure component using the service
 * dependency graph. Example: incidents on "Payment Gateway" and "HR Portal" while "Core Database"
 * (which both depend on) also has an incident -> one cluster rooted at Core Database.
 *
 * Algorithm: every incident "votes" for its own service and its direct dependencies. Services are
 * then taken greedily - services that have their own incidents first (evidence), then by votes, direct incidents
 * and number of dependents; each incident joins at most one cluster. Clusters of size >= 2 are reported. A cluster
 * of size >= {@code majorThreshold} whose root has its own incidents is a major incident; a root inferred only
 * from dependents (3+) is reported as a possible shared cause, never as a major incident.
 *
 * // [Intelligent Feature B] Incident Correlation.
 * // [Collections] HashMap of service -> incident lists; HashSet to ensure each incident is clustered once.
 */
@Component
public class IncidentCorrelator {

    public static final int DEFAULT_MAJOR_THRESHOLD = 3;

    public List<CorrelationCluster> correlate(List<Incident> openIncidents, int majorThreshold) {
        Map<Long, ITService> services = new HashMap<>();
        Map<Long, List<Incident>> direct = new HashMap<>();
        Map<Long, List<Incident>> votes = new LinkedHashMap<>();

        for (Incident incident : openIncidents) {
            ITService svc = incident.getAffectedService();
            services.put(svc.getId(), svc);
            direct.computeIfAbsent(svc.getId(), k -> new ArrayList<>()).add(incident);
            votes.computeIfAbsent(svc.getId(), k -> new ArrayList<>()).add(incident);
            // Direct dependencies only: following the graph transitively would pull almost every incident
            // under shared infrastructure (e.g. everything -> SSO -> database) and destroy the signal.
            for (ITService dep : svc.getDependencies()) {
                services.put(dep.getId(), dep);
                votes.computeIfAbsent(dep.getId(), k -> new ArrayList<>()).add(incident);
            }
        }

        List<Long> order = new ArrayList<>(votes.keySet());
        // Evidence first: services with their own incidents are considered before purely inferred roots.
        order.sort(Comparator.<Long, Boolean>comparing(id -> !direct.containsKey(id))
                .thenComparing(Comparator.<Long>comparingInt(id -> votes.get(id).size()).reversed())
                .thenComparing(Comparator.<Long>comparingInt(id -> direct.getOrDefault(id, List.of()).size()).reversed())
                .thenComparing(Comparator.<Long>comparingInt(id -> dependentsCount(services.get(id), services)).reversed()));

        Set<Long> clustered = new HashSet<>();
        List<CorrelationCluster> clusters = new ArrayList<>();
        for (Long serviceId : order) {
            ITService root = services.get(serviceId);
            List<Incident> remaining = votes.get(serviceId).stream().filter(i -> !clustered.contains(i.getId())).toList();
            if (remaining.size() < 2) continue;
            // A dependency-only root needs evidence: at least one direct incident, or 3+ dependents.
            List<Incident> directHere = remaining.stream().filter(i -> i.getAffectedService().getId().equals(serviceId)).toList();
            List<Incident> dependents = remaining.stream().filter(i -> !i.getAffectedService().getId().equals(serviceId)).toList();
            if (directHere.isEmpty() && dependents.size() < 3) continue;

            remaining.forEach(i -> clustered.add(i.getId()));
            // Only a root with its own incidents is "confirmed"; an inferred root is a hypothesis, never a major incident.
            boolean major = !directHere.isEmpty() && remaining.size() >= majorThreshold;
            String explanation = directHere.isEmpty()
                    ? String.format("Possible shared cause: %d incidents on services that depend on %s", dependents.size(), root.getName())
                    : dependents.isEmpty()
                        ? String.format("%d incidents reported directly against %s", directHere.size(), root.getName())
                        : String.format("%d incident(s) on %s and %d on dependent services", directHere.size(),
                                root.getName(), dependents.size());
            clusters.add(new CorrelationCluster(root, directHere, dependents, major, explanation));
        }
        clusters.sort(Comparator.comparingInt(CorrelationCluster::size).reversed());
        return clusters;
    }

    private static int dependentsCount(ITService service, Map<Long, ITService> known) {
        int count = 0;
        for (ITService s : known.values()) {
            if (s != service && s.getDependencies().contains(service)) count++;
        }
        return count;
    }
}
