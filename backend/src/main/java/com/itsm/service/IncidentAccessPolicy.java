package com.itsm.service;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.user.Permission;
import com.itsm.domain.user.Role;
import com.itsm.domain.user.User;
import com.itsm.exception.UnauthorizedOperationException;
import org.springframework.stereotype.Component;

/**
 * Object-level authorization rules for incidents (who may do what to which incident).
 * Kept separate from the workflow so the rules are readable, testable and in one place (Single Responsibility).
 *
 * // [Security] Role-based + ownership-based authorization ("protection against unauthorized operations").
 */
@Component
public class IncidentAccessPolicy {

    public boolean canView(User user, Incident incident) {
        return user.can(Permission.VIEW_ALL_INCIDENTS) || incident.isReportedBy(user);
    }

    public boolean seesInternalNotes(User user) {
        return user.can(Permission.WORK_INCIDENT) || user.can(Permission.VIEW_ALL_INCIDENTS);
    }

    public void requireView(User user, Incident incident) {
        if (!canView(user, incident)) deny("view " + incident.getNumber());
    }

    /** Acknowledge / start / request info / resolve: the assigned technician, or a team lead / admin covering. */
    public void requireWorker(User user, Incident incident) {
        if (incident.isAssignedTo(user)) return;
        if (user.getRole() == Role.TEAM_LEAD || user.getRole().isAdministrative()) return;
        deny("work on " + incident.getNumber() + " (it is assigned to "
                + (incident.getAssignedTo() == null ? "nobody" : incident.getAssignedTo().getUserCode()) + ")");
    }

    /** Assigning to others needs ASSIGN_INCIDENT; technicians may take an unassigned incident themselves. */
    public void requireAssign(User user, Incident incident, Long targetTechnicianId) {
        if (user.can(Permission.ASSIGN_INCIDENT)) return;
        boolean selfAssign = user.can(Permission.WORK_INCIDENT) && incident.getAssignedTo() == null
                && targetTechnicianId != null && targetTechnicianId.equals(user.getId());
        if (!selfAssign) deny("assign " + incident.getNumber());
    }

    public void requireClassify(User user) {
        if (!(user.can(Permission.WORK_INCIDENT) || user.can(Permission.ASSIGN_INCIDENT))) deny("classify incidents");
    }

    /** Reporter-only actions (provide info, feedback); administrators may act on the reporter's behalf for verify/reopen. */
    public void requireReporter(User user, Incident incident, boolean allowAdmin) {
        if (incident.isReportedBy(user)) return;
        if (allowAdmin && user.getRole().isAdministrative()) return;
        deny("perform this action - only the reporter of " + incident.getNumber() + " can");
    }

    public void requireCloser(User user, Incident incident) {
        if (user.can(Permission.CLOSE_INCIDENT) || incident.isAssignedTo(user) || user.getRole() == Role.TEAM_LEAD) return;
        deny("close " + incident.getNumber());
    }

    public void requireSupportStaff(User user, String action) {
        if (!(user.can(Permission.WORK_INCIDENT) || user.can(Permission.ASSIGN_INCIDENT))) deny(action);
    }

    /** Reporters may edit their own incident; support staff may edit any. */
    public void requireEditor(User user, Incident incident) {
        if (incident.isReportedBy(user) || user.can(Permission.WORK_INCIDENT) || user.can(Permission.ASSIGN_INCIDENT)) return;
        deny("edit " + incident.getNumber());
    }

    /** Reporters may comment on their own incident (never internally); staff may add any note. */
    public void requireNoteAuthor(User user, Incident incident, boolean internal) {
        boolean staff = user.can(Permission.WORK_INCIDENT) || user.can(Permission.VIEW_ALL_INCIDENTS);
        if (staff) return;
        if (internal) deny("add internal notes");
        if (!incident.isReportedBy(user)) deny("comment on " + incident.getNumber());
    }

    private static void deny(String what) {
        throw new UnauthorizedOperationException("You are not allowed to " + what);
    }
}
