package com.itsm.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Immutable, append-only audit record.
 *
 * // [MVP: Audit] Timestamp, user, action, incident, previous state, new state and description.
 * // [OOP: Encapsulation] No setters at all - once written an audit entry can never be modified.
 * //                    Actor details are denormalised so history survives user renames/deletions.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    public static final String SYSTEM_ACTOR = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", nullable = false, length = 150)
    private String actorName;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_ref", length = 40)
    private String entityRef;

    @Column(name = "previous_state", length = 60)
    private String previousState;

    @Column(name = "new_state", length = 60)
    private String newState;

    @Column(nullable = false, length = 1000)
    private String description;

    protected AuditEntry() {
    }

    private AuditEntry(Builder b) {
        this.occurredAt = b.occurredAt;
        this.actorId = b.actorId;
        this.actorName = b.actorName == null ? SYSTEM_ACTOR : b.actorName;
        this.actorRole = b.actorRole;
        this.action = b.action;
        this.entityType = b.entityType;
        this.entityId = b.entityId;
        this.entityRef = b.entityRef;
        this.previousState = b.previousState;
        this.newState = b.newState;
        String d = b.description == null ? b.action.name() : b.description;
        this.description = d.length() > 1000 ? d.substring(0, 1000) : d;
    }

    public static Builder builder(AuditAction action, String entityType, Instant occurredAt) {
        return new Builder(action, entityType, occurredAt);
    }

    /** // [Pattern: Builder] Readable construction of an object with many optional fields. */
    public static final class Builder {
        private final AuditAction action;
        private final String entityType;
        private final Instant occurredAt;
        private Long actorId;
        private String actorName;
        private String actorRole;
        private Long entityId;
        private String entityRef;
        private String previousState;
        private String newState;
        private String description;

        private Builder(AuditAction action, String entityType, Instant occurredAt) {
            this.action = action;
            this.entityType = entityType;
            this.occurredAt = occurredAt;
        }

        public Builder actor(Long id, String name, String role) {
            this.actorId = id;
            this.actorName = name;
            this.actorRole = role;
            return this;
        }

        public Builder entity(Long id, String ref) {
            this.entityId = id;
            this.entityRef = ref;
            return this;
        }

        public Builder transition(String previous, String next) {
            this.previousState = previous;
            this.newState = next;
            return this;
        }

        public Builder description(String text) {
            this.description = text;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(this);
        }
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public AuditAction getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getEntityRef() { return entityRef; }
    public String getPreviousState() { return previousState; }
    public String getNewState() { return newState; }
    public String getDescription() { return description; }
}
