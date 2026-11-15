package com.itsm.engine.assignment;

import com.itsm.domain.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rotates through skilled technicians with capacity (falls back to everyone with capacity if nobody is skilled).
 * A separate cursor is kept per category so rotation is fair within each specialism.
 *
 * // [Pattern: Strategy] Concrete assignment strategy: Round-Robin.
 * // [Collections/Concurrency] ConcurrentHashMap + AtomicInteger make the rotation thread-safe.
 */
@Component
public class RoundRobinAssignmentStrategy implements AssignmentStrategy {

    public static final String KEY = "round-robin";
    private static final long NO_CATEGORY = -1L;

    private final Map<Long, AtomicInteger> cursors = new ConcurrentHashMap<>();

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String displayName() {
        return "Round-robin";
    }

    @Override
    public String description() {
        return "Rotates fairly through technicians skilled in the category (per-category rotation).";
    }

    @Override
    public List<TechnicianRecommendation> rank(Incident incident, List<TechnicianCandidate> candidates) {
        List<TechnicianCandidate> pool = pool(incident, candidates);
        if (pool.isEmpty()) return List.of();
        int start = Math.floorMod(cursorFor(incident).get(), pool.size());
        List<TechnicianRecommendation> ordered = new ArrayList<>(pool.size());
        for (int i = 0; i < pool.size(); i++) {
            TechnicianCandidate c = pool.get((start + i) % pool.size());
            double score = 1.0 - (double) i / pool.size();
            ordered.add(new TechnicianRecommendation(c.technician(), Math.round(score * 1000.0) / 1000.0,
                    c.activeIncidents(), c.technician().getMaxConcurrentIncidents(),
                    List.of(i == 0 ? "Next in rotation" : "Position " + (i + 1) + " in rotation")));
        }
        return ordered;
    }

    /** Selecting advances the rotation so the next incident goes to the next technician. */
    @Override
    public Optional<TechnicianRecommendation> select(Incident incident, List<TechnicianCandidate> candidates) {
        Optional<TechnicianRecommendation> next = rank(incident, candidates).stream().findFirst();
        next.ifPresent(r -> cursorFor(incident).incrementAndGet());
        return next;
    }

    private List<TechnicianCandidate> pool(Incident incident, List<TechnicianCandidate> candidates) {
        Comparator<TechnicianCandidate> byCode = Comparator.comparing(c -> c.technician().getUserCode());
        List<TechnicianCandidate> withCapacity = candidates.stream().filter(TechnicianCandidate::hasCapacity).sorted(byCode).toList();
        List<TechnicianCandidate> skilled = withCapacity.stream()
                .filter(c -> c.technician().hasSkill(incident.getCategory())).toList();
        return skilled.isEmpty() ? withCapacity : skilled;
    }

    private AtomicInteger cursorFor(Incident incident) {
        long key = incident.getCategory() == null || incident.getCategory().getId() == null
                ? NO_CATEGORY : incident.getCategory().getId();
        return cursors.computeIfAbsent(key, k -> new AtomicInteger());
    }
}
