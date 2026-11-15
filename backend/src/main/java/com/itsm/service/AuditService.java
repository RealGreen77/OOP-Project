package com.itsm.service;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.audit.AuditEntry;
import com.itsm.domain.user.User;
import com.itsm.events.ActorRef;
import com.itsm.repository.AuditEntryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes and queries the append-only audit log. // [MVP: Audit] */
@Service
public class AuditService {

    public static final String INCIDENT = "INCIDENT";
    public static final String USER = "USER";
    public static final String CONFIG = "CONFIGURATION";
    public static final String AUTH = "AUTH";

    private final AuditEntryRepository repository;
    private final Clock clock;

    public AuditService(AuditEntryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public AuditEntry record(AuditEntry entry) {
        return repository.save(entry);
    }

    /** Records an administrative / non-incident action performed by a user. */
    @Transactional
    public void recordAction(User actor, AuditAction action, String entityType, Long entityId, String entityRef,
                             String previous, String next, String description) {
        ActorRef ref = ActorRef.of(actor);
        repository.save(AuditEntry.builder(action, entityType, clock.instant())
                .actor(ref.id(), ref.name(), ref.role())
                .entity(entityId, entityRef)
                .transition(previous, next)
                .description(description)
                .build());
    }

    /** Security events must be recorded even if the surrounding transaction rolls back (e.g. failed login). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(AuditAction action, Long userId, String actorName, String description) {
        repository.save(AuditEntry.builder(action, AUTH, clock.instant())
                .actor(userId, actorName, null)
                .entity(userId, null)
                .description(description)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> incidentHistory(Long incidentId) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtAscIdAsc(INCIDENT, incidentId);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(String entityType, AuditAction action, Long actorId, String text,
                                   Instant from, Instant to, Pageable pageable) {
        Specification<AuditEntry> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (entityType != null && !entityType.isBlank()) p.add(cb.equal(root.get("entityType"), entityType));
            if (action != null) p.add(cb.equal(root.get("action"), action));
            if (actorId != null) p.add(cb.equal(root.get("actorId"), actorId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("occurredAt"), to));
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("entityRef")), like),
                        cb.like(cb.lower(root.get("actorName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }
}
