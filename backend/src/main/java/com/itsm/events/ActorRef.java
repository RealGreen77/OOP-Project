package com.itsm.events;

import com.itsm.domain.audit.AuditEntry;
import com.itsm.domain.user.User;

/** Who triggered an event - a user or the system itself (scheduler, automation). */
public record ActorRef(Long id, String name, String role) {

    public static final ActorRef SYSTEM = new ActorRef(null, AuditEntry.SYSTEM_ACTOR, "SYSTEM");

    public static ActorRef of(User user) {
        return user == null ? SYSTEM : new ActorRef(user.getId(), user.displayName(), user.getRole().name());
    }

    public boolean isSystem() {
        return id == null;
    }
}
