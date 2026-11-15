package com.itsm.engine.escalation;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.user.User;

import java.util.List;
import java.util.Optional;

/**
 * One link of the escalation chain: Technician -> Team Lead -> Administrator -> IT Manager.
 *
 * // [Pattern: Chain of Responsibility] Each handler either handles the requested escalation level or
 * //                                   passes the request to its successor. New levels can be inserted
 * //                                   without touching existing handlers or the SLA monitor.
 * // [Intelligent Feature C] Automatic Escalation.
 */
public abstract class EscalationHandler {

    private EscalationHandler next;

    /** Links the successor and returns it, enabling fluent chain construction. */
    public EscalationHandler linkWith(EscalationHandler successor) {
        this.next = successor;
        return successor;
    }

    public final Optional<EscalationTarget> escalate(Incident incident, int targetLevel) {
        if (handles(targetLevel)) {
            List<User> recipients = recipients(incident);
            if (!recipients.isEmpty()) {
                return Optional.of(new EscalationTarget(level(), levelName(), recipients));
            }
            // Nobody available at this level: skip upward rather than silently dropping the escalation.
            return next == null ? Optional.empty() : next.escalate(incident, next.level());
        }
        return next == null ? Optional.empty() : next.escalate(incident, targetLevel);
    }

    protected boolean handles(int targetLevel) {
        return targetLevel == level();
    }

    protected abstract int level();

    protected abstract String levelName();

    protected abstract List<User> recipients(Incident incident);
}
