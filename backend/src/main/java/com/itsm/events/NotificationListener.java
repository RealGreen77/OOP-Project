package com.itsm.events;

import com.itsm.domain.incident.ClosureCode;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.notification.NotificationDispatcher;
import com.itsm.notification.NotificationMessage;
import com.itsm.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides who must be told about each event and with what message, then hands off to the dispatcher.
 * // [Pattern: Observer] Concrete observer (non-critical: failures never block incident processing).
 * // [MVP: Notification] Created, assigned, priority changed, SLA approaching/violated, resolved, closed...
 */
@Component
public class NotificationListener implements IncidentEventListener {

    private final NotificationDispatcher dispatcher;
    private final UserRepository userRepository;

    public NotificationListener(NotificationDispatcher dispatcher, UserRepository userRepository) {
        this.dispatcher = dispatcher;
        this.userRepository = userRepository;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void onEvent(IncidentEvent event) {
        Incident i = event.incident();
        String num = i.getNumber();
        List<User> to = new ArrayList<>();
        String title;
        String body;

        switch (event) {
            case IncidentEvent.Created e -> {
                to.add(i.getReporter());
                title = "Incident received";
                body = "Your incident " + num + " \"" + i.getTitle() + "\" was logged. We will keep you updated.";
                if (i.getPriority() == Priority.P1) {
                    to.addAll(userRepository.findActiveTeamLeads());
                    to.addAll(userRepository.findActiveAdministrators());
                    title = "New P1 incident";
                    body = "Critical incident " + num + " reported on " + i.getAffectedService().getName() + ": " + i.getTitle();
                }
            }
            case IncidentEvent.Assigned e -> {
                notify(List.of(e.assignee()), event, "Incident assigned to you",
                        num + " (" + priorityLabel(i) + ") on " + i.getAffectedService().getName() + ": " + i.getTitle()
                                + ". Please acknowledge it.");
                to.add(i.getReporter());
                title = "Technician assigned";
                body = "Your incident " + num + " has been assigned to " + e.assignee().getFullName() + ".";
            }
            case IncidentEvent.PriorityAssigned e -> {
                if (!e.isChange()) return;
                to.add(i.getReporter());
                to.add(i.getAssignedTo());
                title = "Priority changed to " + e.current();
                body = "Priority of " + num + " changed from " + e.previous() + " to " + e.current().display() + ".";
            }
            case IncidentEvent.StatusChanged e -> {
                switch (e.to()) {
                    case ACKNOWLEDGED -> { to.add(i.getReporter()); title = "Incident acknowledged";
                        body = i.getAssignedTo().getFullName() + " acknowledged " + num + " and will investigate."; }
                    case PENDING_INFO -> { to.add(i.getReporter()); title = "Information requested";
                        body = "The technician needs more information about " + num + ". "
                                + (e.comment() == null ? "" : "\"" + e.comment() + "\" ") + "Please respond in the portal."; }
                    case RESOLVED -> { to.add(i.getReporter()); title = "Incident resolved - please verify";
                        body = num + " was resolved: " + i.getResolution().getSummary()
                                + " Please confirm the fix or reopen the incident."; }
                    case CLOSED -> { to.add(i.getReporter()); to.add(i.getAssignedTo());
                        title = i.getClosureCode() == ClosureCode.DUPLICATE ? "Incident closed as duplicate" : "Incident closed";
                        body = i.getClosureCode() == ClosureCode.DUPLICATE
                                ? num + " was closed as a duplicate of " + i.getDuplicateOf().getNumber() + ". Follow that incident for updates."
                                : num + " is now closed. Thank you for your feedback."; }
                    case IN_PROGRESS -> {
                        if (e.from() == IncidentStatus.PENDING_INFO || e.from() == IncidentStatus.RESOLVED) {
                            to.add(i.getAssignedTo());
                            title = e.from() == IncidentStatus.RESOLVED ? "Incident reopened" : "Reporter responded";
                            body = e.from() == IncidentStatus.RESOLVED
                                    ? "The reporter reopened " + num + (e.comment() == null ? "." : ": \"" + e.comment() + "\"")
                                    : "The reporter added the requested information to " + num + ".";
                        } else return;
                    }
                    case VERIFIED -> { to.add(i.getAssignedTo()); title = "Resolution verified";
                        body = "The reporter confirmed your fix for " + num + "."; }
                    default -> { return; }
                }
            }
            case IncidentEvent.NoteAdded e -> {
                if (e.note().isInternal()) return;
                boolean byReporter = i.isReportedBy(e.note().getAuthor());
                to.add(byReporter ? i.getAssignedTo() : i.getReporter());
                title = "New update on " + num;
                body = shorten(e.note().getContent(), 300);
            }
            case IncidentEvent.SlaWarning e -> {
                to.add(i.getAssignedTo());
                to.add(teamLeadOf(i.getAssignedTo()));
                title = "SLA deadline approaching";
                body = String.format("%s has used %.0f%% of its resolution window. Deadline: %s UTC.", num,
                        e.elapsedFraction() * 100, i.getSla().getResolutionDeadline().toString().replace('T', ' ').substring(0, 16));
            }
            case IncidentEvent.SlaBreached e -> {
                to.add(i.getAssignedTo());
                to.add(teamLeadOf(i.getAssignedTo()));
                to.addAll(userRepository.findActiveAdministrators());
                title = (e.kind() == IncidentEvent.SlaBreached.Kind.RESPONSE ? "Response" : "Resolution") + " SLA violated";
                body = num + " (" + priorityLabel(i) + ") breached its " + e.kind().name().toLowerCase() + " SLA.";
            }
            case IncidentEvent.Escalated e -> {
                to.addAll(e.target().recipients());
                title = "Incident escalated to you (" + e.target().levelName() + ")";
                body = num + " (" + priorityLabel(i) + "): " + e.reason();
            }
            case IncidentEvent.DuplicateSuspected e -> {
                to.add(e.original().getAssignedTo());
                to.add(i.getAssignedTo());
                title = "Possible duplicate detected";
                body = String.format("%s looks like a duplicate of %s (%.0f%% similar). Review and merge if appropriate.",
                        num, e.original().getNumber(), e.score() * 100);
            }
            case IncidentEvent.MajorIncidentDetected e -> {
                to.addAll(userRepository.findActiveAdministrators());
                to.addAll(userRepository.findActiveTeamLeads());
                title = "Major incident: " + e.cluster().probableRoot().getName();
                body = e.cluster().explanation() + ". Probable root service: " + e.cluster().probableRoot().getName() + ".";
            }
            case IncidentEvent.FeedbackSubmitted e -> {
                to.add(i.getAssignedTo());
                title = "Feedback received";
                body = "The reporter rated your work on " + num + " " + i.getFeedback().getRating() + "/5.";
            }
            case IncidentEvent.Classified e -> { return; }
            case IncidentEvent.SlaApplied e -> { return; }
            case IncidentEvent.DetailsUpdated e -> { return; }
        }
        notify(to, event, title, body);
    }

    private void notify(List<? extends User> recipients, IncidentEvent event, String title, String body) {
        Incident i = event.incident();
        List<User> filtered = recipients.stream()
                .filter(Objects::nonNull)
                // never notify the person who performed the action about their own action
                .filter(u -> event.actor().isSystem() || !u.getId().equals(event.actor().id()))
                .map(u -> (User) u)
                .toList();
        if (filtered.isEmpty()) return;
        dispatcher.dispatch(filtered, new NotificationMessage(event.eventType(), title, body, i.getId(), i.getNumber(),
                i.getPriority()));
    }

    private static User teamLeadOf(Technician technician) {
        return technician == null ? null : technician.getTeamLead();
    }

    private static String priorityLabel(Incident i) {
        return i.getPriority() == null ? "unprioritised" : i.getPriority().display();
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
