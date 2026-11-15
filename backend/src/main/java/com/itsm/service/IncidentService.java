package com.itsm.service;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.ClosureCode;
import com.itsm.domain.incident.Feedback;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentAction;
import com.itsm.domain.incident.IncidentFactory;
import com.itsm.domain.incident.IncidentNote;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.NoteType;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Resolution;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.user.Permission;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.engine.assignment.TechnicianRecommendation;
import com.itsm.engine.intelligence.CategorySuggestion;
import com.itsm.engine.intelligence.DuplicateDetector;
import com.itsm.engine.intelligence.IncidentText;
import com.itsm.engine.intelligence.SimilarIncident;
import com.itsm.engine.priority.PriorityAssessment;
import com.itsm.engine.sla.SlaPolicyResolver;
import com.itsm.events.ActorRef;
import com.itsm.events.IncidentEvent;
import com.itsm.events.IncidentEventPublisher;
import com.itsm.exception.DuplicateIncidentException;
import com.itsm.exception.IncidentNotFoundException;
import com.itsm.exception.InvalidStateTransitionException;
import com.itsm.exception.TechnicianUnavailableException;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.IncidentSearchCriteria;
import com.itsm.repository.IncidentSpecifications;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.CommonDtos.PageResponse;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.IncidentDtos.AuditEntryDto;
import com.itsm.web.dto.IncidentDtos.IncidentDetail;
import com.itsm.web.dto.IncidentDtos.IncidentSummary;
import com.itsm.web.dto.IncidentDtos.ReportIncidentRequest;
import com.itsm.web.dto.IncidentDtos.UpdateIncidentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Application service orchestrating the full incident lifecycle:
 * Report -> Classify -> Prioritise -> Apply SLA -> Assign -> Acknowledge -> Investigate -> Resolve -> Verify -> Close.
 *
 * Responsibilities are delegated to collaborators (composition over inheritance):
 *  - {@link Incident} + State classes enforce legal transitions
 *  - {@link PriorityService} / {@link AssignmentService} apply the active strategies
 *  - {@link IntelligenceService} provides categorisation, duplicate and correlation detection
 *  - {@link IncidentAccessPolicy} enforces object-level authorization
 *  - {@link IncidentEventPublisher} notifies observers (audit, notifications, live feed)
 *
 * // [MVP: Incident Management] Create, View, Update, Assign, Resolve, Close.
 */
@Service
public class IncidentService {

    private static final Duration SAME_REPORTER_DUPLICATE_WINDOW = Duration.ofMinutes(30);
    private static final double SAME_REPORTER_DUPLICATE_TEXT = 0.85;
    private static final int EXPORT_LIMIT = 5000;

    private final IncidentRepository incidentRepository;
    private final CatalogService catalogService;
    private final PriorityService priorityService;
    private final SlaPolicyResolver slaResolver;
    private final AssignmentService assignmentService;
    private final IntelligenceService intelligence;
    private final IncidentAccessPolicy access;
    private final IncidentEventPublisher publisher;
    private final AuditService auditService;
    private final SettingsService settings;
    private final CurrentUserProvider currentUser;
    private final DtoMapper mapper;
    private final DuplicateDetector duplicateDetector;
    private final Clock clock;

    public IncidentService(IncidentRepository incidentRepository, CatalogService catalogService,
                           PriorityService priorityService, SlaPolicyResolver slaResolver,
                           AssignmentService assignmentService, IntelligenceService intelligence,
                           IncidentAccessPolicy access, IncidentEventPublisher publisher, AuditService auditService,
                           SettingsService settings, CurrentUserProvider currentUser, DtoMapper mapper,
                           DuplicateDetector duplicateDetector, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.catalogService = catalogService;
        this.priorityService = priorityService;
        this.slaResolver = slaResolver;
        this.assignmentService = assignmentService;
        this.intelligence = intelligence;
        this.access = access;
        this.publisher = publisher;
        this.auditService = auditService;
        this.settings = settings;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.duplicateDetector = duplicateDetector;
        this.clock = clock;
    }

    // ================================================================ report (workflow 1)

    @Transactional
    public IncidentDetail report(ReportIncidentRequest request) {
        User reporter = currentUser.currentUser();
        ITService service = catalogService.findActiveService(request.serviceId());
        Instant now = clock.instant();
        IncidentText text = new IncidentText(request.title(), request.description(), service, null);

        if (!request.force()) rejectRepeatSubmission(reporter, text, now);

        // 1. Create (Factory validates reporter permission and initial state)
        Incident incident = IncidentFactory.report(request.title(), request.description(), reporter, service,
                request.impact(), request.urgency(), request.affectedUsers() == null ? 1 : request.affectedUsers(), now);
        incidentRepository.save(incident);
        incident.assignNumber(1000 + incident.getId());
        publisher.publish(new IncidentEvent.Created(incident, ActorRef.of(reporter), now));

        // 2. Classify (reporter's choice, otherwise automatic suggestion, otherwise the service default)
        classifyOnCreation(incident, request.categoryId(), text, now);

        // 3-5. Business impact -> priority -> applicable SLA
        applyPriorityAndSla(incident, ActorRef.SYSTEM, now);

        // Intelligent: suspected duplicate of an open incident?
        flagSuspectedDuplicate(incident, now);

        // 6-8. Identify suitable technician and assign (if automatic assignment is enabled)
        if (settings.autoAssignEnabled() && incident.getStatus() == IncidentStatus.CLASSIFIED) {
            autoAssign(incident, now);
        }

        // Intelligent: correlation / major incident detection
        intelligence.checkForMajorIncident(incident);
        return detail(incident, reporter, now);
    }

    // ================================================================ queries

    @Transactional(readOnly = true)
    public IncidentDetail get(Long id) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireView(user, incident);
        return detail(incident, user, clock.instant());
    }

    @Transactional(readOnly = true)
    public IncidentDetail getByNumber(String number) {
        Incident incident = incidentRepository.findByNumberIgnoreCase(number).orElseThrow(() -> new IncidentNotFoundException(number));
        return get(incident.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentSummary> search(IncidentSearchCriteria criteria, Pageable pageable) {
        User user = currentUser.currentUser();
        Instant now = clock.instant();
        Page<Incident> page = incidentRepository.findAll(IncidentSpecifications.from(scope(criteria, user), now), pageable);
        return PageResponse.of(page, i -> mapper.summary(i, now));
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> myQueue() {
        User user = currentUser.currentUser();
        Instant now = clock.instant();
        return incidentRepository.findByAssignedToIdAndStatusInOrderByReportedAtAsc(user.getId(), IncidentStatus.TECHNICIAN_WORKLOAD)
                .stream()
                .sorted((a, b) -> {
                    int p = Integer.compare(rank(a.getPriority()), rank(b.getPriority()));
                    return p != 0 ? p : a.getReportedAt().compareTo(b.getReportedAt());
                })
                .map(i -> mapper.summary(i, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEntryDto> history(Long id) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireView(user, incident);
        return auditService.incidentHistory(id).stream().map(mapper::audit).toList();
    }

    /** CSV export of the current search (reporting). */
    @Transactional(readOnly = true)
    public String exportCsv(IncidentSearchCriteria criteria) {
        User user = currentUser.currentUser();
        Instant now = clock.instant();
        List<Incident> rows = incidentRepository.findAll(IncidentSpecifications.from(scope(criteria, user), now),
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "reportedAt"))).getContent();
        StringBuilder csv = new StringBuilder("Number,Title,Status,Priority,Category,Service,Reporter,Assignee,Reported At,Resolved At,SLA Status,Escalation Level\n");
        for (Incident i : rows) {
            csv.append(String.join(",",
                    cell(i.getNumber()), cell(i.getTitle()), cell(i.getStatus().name()),
                    cell(i.getPriority() == null ? "" : i.getPriority().name()),
                    cell(i.getCategory() == null ? "" : i.getCategory().getName()),
                    cell(i.getAffectedService().getName()), cell(i.getReporter().getUserCode()),
                    cell(i.getAssignedTo() == null ? "" : i.getAssignedTo().getUserCode()),
                    cell(String.valueOf(i.getReportedAt())), cell(i.getResolvedAt() == null ? "" : i.getResolvedAt().toString()),
                    cell(i.slaStatus(now).name()), String.valueOf(i.getEscalationLevel()))).append('\n');
        }
        return csv.toString();
    }

    // ================================================================ updates

    @Transactional
    public IncidentDetail update(Long id, UpdateIncidentRequest r) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireEditor(user, incident);
        Instant now = clock.instant();
        ITService service = catalogService.findActiveService(r.serviceId());
        List<String> changes = new ArrayList<>();
        if (!incident.getTitle().equals(r.title().trim())) changes.add("title");
        if (!incident.getDescription().equals(r.description().trim())) changes.add("description");
        if (!incident.getAffectedService().getId().equals(service.getId())) changes.add("service " + incident.getAffectedService().getCode() + "->" + service.getCode());
        if (incident.getImpact() != r.impact()) changes.add("impact " + incident.getImpact() + "->" + r.impact());
        if (incident.getUrgency() != r.urgency()) changes.add("urgency " + incident.getUrgency() + "->" + r.urgency());
        if (incident.getAffectedUsers() != r.affectedUsers()) changes.add("affected users " + incident.getAffectedUsers() + "->" + r.affectedUsers());
        if (changes.isEmpty()) return detail(incident, user, now);

        incident.updateDetails(r.title(), r.description(), service, r.impact(), r.urgency(), r.affectedUsers());
        publisher.publish(new IncidentEvent.DetailsUpdated(incident, ActorRef.of(user), now, String.join(", ", changes)));
        applyPriorityAndSla(incident, ActorRef.of(user), now); // re-prioritise; publishes only if priority changed
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail classify(Long id, Long categoryId) {
        User user = currentUser.currentUser();
        access.requireClassify(user);
        Incident incident = load(id);
        Category category = catalogService.findCategory(categoryId);
        Instant now = clock.instant();
        Category previous = incident.getCategory();
        IncidentStatus before = incident.getStatus();
        incident.classify(category, now);
        publisher.publish(new IncidentEvent.Classified(incident, ActorRef.of(user), now, previous, category, false, 1.0));
        if (before == IncidentStatus.NEW && settings.autoAssignEnabled()) autoAssign(incident, now);
        return detail(incident, user, now);
    }

    /** Assign manually (technicianId) or via the active strategy (technicianId == null). */
    @Transactional
    public IncidentDetail assign(Long id, Long technicianId) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireAssign(user, incident, technicianId);
        Instant now = clock.instant();
        requireAllowed(incident, incident.getAssignedTo() == null ? IncidentAction.ASSIGN : IncidentAction.REASSIGN);

        if (technicianId == null) {
            TechnicianRecommendation pick = assignmentService.autoSelect(incident)
                    .orElseThrow(() -> new TechnicianUnavailableException(
                            "No technician with free capacity is available for " + incident.getNumber()));
            applyAssignment(incident, pick.technician(), ActorRef.of(user), now, true);
        } else {
            Technician tech = assignmentService.validateManualAssignment(incident, technicianId);
            applyAssignment(incident, tech, ActorRef.of(user), now, false);
        }
        return detail(incident, user, now);
    }

    // ================================================================ technician workflow (workflow 2)

    @Transactional
    public IncidentDetail acknowledge(Long id) {
        return workerTransition(id, null, (incident) -> incident.acknowledge(clock.instant()));
    }

    @Transactional
    public IncidentDetail startWork(Long id) {
        return workerTransition(id, null, (incident) -> incident.startWork(clock.instant()));
    }

    @Transactional
    public IncidentDetail requestInformation(Long id, String question) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireWorker(user, incident);
        Instant now = clock.instant();
        requireAllowed(incident, IncidentAction.REQUEST_INFO);
        IncidentNote note = incident.addNote(user, NoteType.INFO_REQUEST, question, false, now);
        transition(incident, user, question, i -> i.requestInformation(now));
        publisher.publish(new IncidentEvent.NoteAdded(incident, ActorRef.of(user), now, note));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail provideInformation(Long id, String answer) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireReporter(user, incident, false);
        Instant now = clock.instant();
        requireAllowed(incident, IncidentAction.PROVIDE_INFO);
        incident.addNote(user, NoteType.INFO_RESPONSE, answer, false, now);
        transition(incident, user, null, i -> i.provideInformation(now));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail addNote(Long id, String content, boolean internal, NoteType requestedType) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireView(user, incident);
        access.requireNoteAuthor(user, incident, internal);
        Instant now = clock.instant();
        NoteType type = requestedType == null || requestedType == NoteType.SYSTEM || requestedType == NoteType.RESOLUTION
                ? (user.can(Permission.WORK_INCIDENT) ? NoteType.INVESTIGATION : NoteType.COMMENT)
                : requestedType;
        if (!user.can(Permission.WORK_INCIDENT) && type == NoteType.INVESTIGATION) type = NoteType.COMMENT;
        IncidentNote note = incident.addNote(user, type, content, internal, now);
        publisher.publish(new IncidentEvent.NoteAdded(incident, ActorRef.of(user), now, note));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail resolve(Long id, String summary, String rootCause, boolean workaround) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireWorker(user, incident);
        Instant now = clock.instant();
        Resolution resolution = new Resolution(summary, rootCause, workaround);
        transition(incident, user, null, i -> i.resolve(resolution, now));
        incident.addNote(user, NoteType.RESOLUTION, summary, false, now);
        return detail(incident, user, now);
    }

    // ================================================================ reporter verification & closure

    @Transactional
    public IncidentDetail verify(Long id) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireReporter(user, incident, true);
        Instant now = clock.instant();
        transition(incident, user, null, i -> i.verify(now));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail reopen(Long id, String reason) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireReporter(user, incident, true);
        Instant now = clock.instant();
        requireAllowed(incident, IncidentAction.REOPEN);
        incident.addNote(user, NoteType.COMMENT, "Reopened: " + reason, false, now);
        transition(incident, user, reason, i -> i.reopen(now));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail close(Long id, ClosureCode code, String notes) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireCloser(user, incident);
        Instant now = clock.instant();
        ClosureCode effective = code == null || code == ClosureCode.DUPLICATE ? ClosureCode.RESOLVED : code;
        transition(incident, user, notes, i -> i.close(effective, notes, now));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail markDuplicate(Long id, Long parentId) {
        User user = currentUser.currentUser();
        access.requireSupportStaff(user, "mark incidents as duplicates");
        Incident incident = load(id);
        Incident parent = load(parentId);
        Instant now = clock.instant();
        transition(incident, user, "Duplicate of " + parent.getNumber(), i -> i.markDuplicateOf(parent, now));
        return detail(incident, user, now);
    }

    @Transactional
    public IncidentDetail submitFeedback(Long id, int rating, String comment) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireReporter(user, incident, false);
        Instant now = clock.instant();
        incident.submitFeedback(new Feedback(rating, comment, now));
        publisher.publish(new IncidentEvent.FeedbackSubmitted(incident, ActorRef.of(user), now));
        return detail(incident, user, now);
    }

    // ================================================================ internals

    private void rejectRepeatSubmission(User reporter, IncidentText text, Instant now) {
        List<Incident> recent = incidentRepository.findByReporterIdAndReportedAtAfter(reporter.getId(),
                now.minus(SAME_REPORTER_DUPLICATE_WINDOW)).stream().filter(i -> i.getStatus().isActive()).toList();
        if (recent.isEmpty()) return;
        List<String> numbers = duplicateDetector.findSimilar(text, recent, now, Duration.ofHours(1), 0.0).stream()
                .filter(s -> s.textSimilarity() >= SAME_REPORTER_DUPLICATE_TEXT)
                .map(s -> s.incident().getNumber())
                .toList();
        if (!numbers.isEmpty()) throw new DuplicateIncidentException(numbers);
    }

    private void classifyOnCreation(Incident incident, Long requestedCategoryId, IncidentText text, Instant now) {
        Category category = null;
        boolean automatic = false;
        double confidence = 1.0;
        if (requestedCategoryId != null) {
            category = catalogService.findCategory(requestedCategoryId);
        } else {
            List<CategorySuggestion> suggestions = intelligence.suggestCategories(text);
            if (!suggestions.isEmpty()) {
                category = suggestions.get(0).category();
                confidence = suggestions.get(0).confidence();
            } else if (incident.getAffectedService().getDefaultCategory() != null) {
                category = incident.getAffectedService().getDefaultCategory();
                confidence = 0.5;
            }
            automatic = true;
        }
        if (category != null && category.isActive()) {
            incident.classify(category, now);
            publisher.publish(new IncidentEvent.Classified(incident, ActorRef.SYSTEM, now, null, category, automatic, confidence));
        }
        // otherwise the incident stays NEW for manual triage
    }

    /** Computes priority with the active strategy; (re)applies the SLA and publishes events when it changes. */
    private void applyPriorityAndSla(Incident incident, ActorRef actor, Instant now) {
        PriorityAssessment a = priorityService.assess(incident.getAffectedService(), incident.getImpact(),
                incident.getUrgency(), incident.getAffectedUsers());
        Priority previous = incident.getPriority();
        incident.applyPriority(a.priority(), a.score(), a.rationale());
        boolean priorityChanged = previous != a.priority();
        if (priorityChanged) {
            publisher.publish(new IncidentEvent.PriorityAssigned(incident, actor, now, previous, a.priority(), a.rationale()));
        }
        // Re-resolve the SLA when priority or service changed (services may have their own SLA policies).
        SlaPolicy policy = slaResolver.resolve(incident.getAffectedService(), a.priority());
        boolean slaChanged = priorityChanged || !incident.hasSla() || !policy.getName().equals(incident.getSla().getPolicyName());
        if (slaChanged) {
            incident.applySlaPolicy(policy);
            publisher.publish(new IncidentEvent.SlaApplied(incident, actor, now));
        }
    }

    private void flagSuspectedDuplicate(Incident incident, Instant now) {
        IncidentText text = new IncidentText(incident.getTitle(), incident.getDescription(), incident.getAffectedService(), incident.getId());
        List<SimilarIncident> matches = intelligence.findDuplicates(text, DuplicateDetector.SUSPECT_THRESHOLD);
        if (!matches.isEmpty()) {
            SimilarIncident best = matches.get(0);
            incident.flagSuspectedDuplicate(best.incident(), best.score());
            publisher.publish(new IncidentEvent.DuplicateSuspected(incident, now, best.incident(), best.score()));
        }
    }

    private void autoAssign(Incident incident, Instant now) {
        assignmentService.autoSelect(incident)
                .ifPresent(rec -> applyAssignment(incident, rec.technician(), ActorRef.SYSTEM, now, true));
    }

    private void applyAssignment(Incident incident, Technician tech, ActorRef actor, Instant now, boolean automatic) {
        Technician previous = incident.getAssignedTo();
        incident.assignTo(tech, now);
        publisher.publish(new IncidentEvent.Assigned(incident, actor, now, previous, tech,
                assignmentService.activeStrategy().key(), automatic));
    }

    private IncidentDetail workerTransition(Long id, String comment, Consumer<Incident> action) {
        User user = currentUser.currentUser();
        Incident incident = load(id);
        access.requireWorker(user, incident);
        transition(incident, user, comment, action);
        return detail(incident, user, clock.instant());
    }

    /** Runs a lifecycle action and publishes the resulting status change. */
    private void transition(Incident incident, User actor, String comment, Consumer<Incident> action) {
        IncidentStatus before = incident.getStatus();
        action.accept(incident);
        publisher.publish(new IncidentEvent.StatusChanged(incident, ActorRef.of(actor), clock.instant(), before,
                incident.getStatus(), comment));
    }

    private static void requireAllowed(Incident incident, IncidentAction action) {
        if (!incident.allows(action)) throw new InvalidStateTransitionException(incident.getStatus(), action);
    }

    /** Employees only ever see their own incidents, whatever filters they send. */
    private IncidentSearchCriteria scope(IncidentSearchCriteria criteria, User user) {
        if (user.can(Permission.VIEW_ALL_INCIDENTS)) return criteria;
        return criteria.toBuilder().reporterId(user.getId()).build();
    }

    private Incident load(Long id) {
        return incidentRepository.findDetailedById(id).orElseThrow(() -> new IncidentNotFoundException(id));
    }

    private IncidentDetail detail(Incident incident, User viewer, Instant now) {
        boolean staff = access.seesInternalNotes(viewer);
        return mapper.detail(incident, now, staff, staff ? intelligence.assessRisk(incident, now) : null);
    }

    private static int rank(Priority p) {
        return p == null ? 9 : p.ordinal();
    }

    private static String cell(String value) {
        if (value == null) return "";
        String v = value.replace("\"", "\"\"");
        // Neutralise spreadsheet formula injection.
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v + "\"";
    }
}
