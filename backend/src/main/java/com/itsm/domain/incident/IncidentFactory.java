package com.itsm.domain.incident;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.user.User;
import com.itsm.exception.InvalidInputException;
import com.itsm.exception.UnauthorizedOperationException;
import com.itsm.domain.user.Permission;

import java.time.Instant;

/**
 * Creates new incidents in a valid initial state.
 *
 * // [Pattern: Factory] Centralises construction rules (reporter permission, inactive-service check,
 * //                   initial NEW status) so callers cannot create a half-initialised incident.
 */
public final class IncidentFactory {

    private IncidentFactory() {
    }

    public static Incident report(String title, String description, User reporter, ITService service,
                                  Impact impact, Urgency urgency, int affectedUsers, Instant reportedAt) {
        if (reporter == null) throw new InvalidInputException("Reporter is required");
        if (!reporter.can(Permission.REPORT_INCIDENT)) {
            throw new UnauthorizedOperationException("User " + reporter.getUserCode() + " cannot report incidents");
        }
        if (service == null || !service.isActive()) {
            throw new InvalidInputException("Incidents can only be reported against an active service");
        }
        return new Incident(title, description, reporter, service, impact, urgency,
                Math.max(1, affectedUsers), reportedAt);
    }
}
