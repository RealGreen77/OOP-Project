package com.itsm.repository;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.sla.SlaStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates {@link IncidentSearchCriteria} into a JPA Criteria predicate. Every filter is optional and
 * filters are AND-ed together, so any combination of the criteria can be used.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> from(IncidentSearchCriteria c, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();

            if (c.text() != null && !c.text().isBlank()) {
                String like = "%" + c.text().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("number")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (c.reportedFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("reportedAt"), c.reportedFrom()));
            if (c.reportedTo() != null) p.add(cb.lessThan(root.get("reportedAt"), c.reportedTo()));
            if (c.statuses() != null && !c.statuses().isEmpty()) p.add(root.get("status").in(c.statuses()));
            if (c.priorities() != null && !c.priorities().isEmpty()) p.add(root.get("priority").in(c.priorities()));
            if (c.categoryId() != null) p.add(cb.equal(root.get("category").get("id"), c.categoryId()));
            if (c.serviceId() != null) p.add(cb.equal(root.get("affectedService").get("id"), c.serviceId()));
            if (c.technicianId() != null) p.add(cb.equal(root.get("assignedTo").get("id"), c.technicianId()));
            if (c.reporterId() != null) p.add(cb.equal(root.get("reporter").get("id"), c.reporterId()));
            if (Boolean.TRUE.equals(c.escalatedOnly())) p.add(cb.greaterThan(root.get("escalationLevel"), 0));
            if (Boolean.TRUE.equals(c.unassignedOnly())) p.add(cb.isNull(root.get("assignedTo")));
            if (c.slaStatus() != null) p.add(slaPredicate(c.slaStatus(), root, cb, now));

            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** SLA status is derived data, so it is expressed directly in terms of the embedded SLA columns. */
    private static Predicate slaPredicate(SlaStatus status, Root<Incident> root, CriteriaBuilder cb, Instant now) {
        Path<Object> sla = root.get("sla");
        Predicate breachedFlag = cb.or(cb.isTrue(sla.get("responseBreached")), cb.isTrue(sla.get("resolutionBreached")));
        Predicate resolved = cb.isNotNull(root.get("resolvedAt"));
        Predicate hasSla = cb.isNotNull(sla.get("startedAt"));
        Predicate paused = cb.isNotNull(sla.get("pausedAt"));
        Predicate overdue = cb.lessThan(sla.<Instant>get("resolutionDeadline"), now);
        return switch (status) {
            case BREACHED -> cb.and(hasSla, cb.or(breachedFlag, cb.and(cb.not(resolved), overdue)));
            case MET -> cb.and(hasSla, resolved, cb.not(breachedFlag));
            case PAUSED -> cb.and(hasSla, paused, cb.not(resolved));
            case AT_RISK -> cb.and(hasSla, cb.not(resolved), cb.not(breachedFlag), cb.not(overdue),
                    cb.isTrue(sla.get("warningIssued")));
            case ON_TRACK -> cb.and(hasSla, cb.not(resolved), cb.not(breachedFlag), cb.not(overdue), cb.not(paused),
                    cb.or(cb.isNull(sla.get("warningIssued")), cb.isFalse(sla.get("warningIssued"))));
            case NOT_APPLICABLE -> cb.isNull(sla.get("startedAt"));
        };
    }

    /** Convenience: only incidents still being worked. */
    public static Specification<Incident> active() {
        return (root, q, cb) -> root.get("status").in(IncidentStatus.ACTIVE);
    }
}
