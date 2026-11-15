package com.itsm.service;

import com.itsm.config.ItsmProperties;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Urgency;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.user.User;
import com.itsm.engine.intelligence.CategorySuggester;
import com.itsm.engine.intelligence.CategorySuggestion;
import com.itsm.engine.intelligence.CorrelationCluster;
import com.itsm.engine.intelligence.DuplicateDetector;
import com.itsm.engine.intelligence.IncidentCorrelator;
import com.itsm.engine.intelligence.IncidentText;
import com.itsm.engine.intelligence.ResolutionSuggester;
import com.itsm.engine.intelligence.SimilarIncident;
import com.itsm.engine.intelligence.SlaRiskAssessment;
import com.itsm.engine.intelligence.SlaRiskPredictor;
import com.itsm.engine.priority.PriorityAssessment;
import com.itsm.engine.sla.SlaPolicyResolver;
import com.itsm.events.IncidentEvent;
import com.itsm.events.IncidentEventPublisher;
import com.itsm.exception.IncidentNotFoundException;
import com.itsm.exception.SlaConfigurationMissingException;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.IntelligenceDtos.AnalysisResponse;
import com.itsm.web.dto.IntelligenceDtos.AnalyzeRequest;
import com.itsm.web.dto.IntelligenceDtos.CategorySuggestionDto;
import com.itsm.web.dto.IntelligenceDtos.ClusterIncident;
import com.itsm.web.dto.IntelligenceDtos.CorrelationClusterDto;
import com.itsm.web.dto.IntelligenceDtos.PriorityPreview;
import com.itsm.web.dto.IntelligenceDtos.RecommendationDto;
import com.itsm.web.dto.IntelligenceDtos.RecommendationResponse;
import com.itsm.web.dto.IntelligenceDtos.ResolutionSuggestionDto;
import com.itsm.web.dto.IntelligenceDtos.SimilarIncidentDto;
import com.itsm.web.dto.IntelligenceDtos.SlaRiskItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the intelligent features: live analysis, duplicate detection, correlation / major-incident
 * detection, SLA-risk prediction, smart technician recommendation and suggested resolutions.
 */
@Service
public class IntelligenceService {

    private static final Duration STATS_TTL = Duration.ofMinutes(5);
    private static final Duration MAJOR_ALERT_COOLDOWN = Duration.ofHours(1);

    private final IncidentRepository incidentRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogService catalogService;
    private final CategorySuggester categorySuggester;
    private final DuplicateDetector duplicateDetector;
    private final IncidentCorrelator correlator;
    private final ResolutionSuggester resolutionSuggester;
    private final SlaRiskPredictor riskPredictor;
    private final PriorityService priorityService;
    private final SlaPolicyResolver slaResolver;
    private final AssignmentService assignmentService;
    private final IncidentAccessPolicy accessPolicy;
    private final CurrentUserProvider currentUser;
    private final IncidentEventPublisher publisher;
    private final DtoMapper mapper;
    private final ItsmProperties properties;
    private final Clock clock;

    /** Cache of historical mean time-to-resolve, keyed by "priority:categoryId". */
    private final Map<String, CachedDuration> meanResolutionCache = new ConcurrentHashMap<>();
    /** Last major-incident alert per root service, to avoid alert storms. */
    private final Map<Long, Instant> lastMajorAlert = new ConcurrentHashMap<>();

    private record CachedDuration(Duration value, Instant computedAt) {}

    public IntelligenceService(IncidentRepository incidentRepository, CategoryRepository categoryRepository,
                               CatalogService catalogService, CategorySuggester categorySuggester,
                               DuplicateDetector duplicateDetector, IncidentCorrelator correlator,
                               ResolutionSuggester resolutionSuggester, SlaRiskPredictor riskPredictor,
                               PriorityService priorityService, SlaPolicyResolver slaResolver,
                               AssignmentService assignmentService, IncidentAccessPolicy accessPolicy,
                               CurrentUserProvider currentUser, IncidentEventPublisher publisher, DtoMapper mapper,
                               ItsmProperties properties, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.categoryRepository = categoryRepository;
        this.catalogService = catalogService;
        this.categorySuggester = categorySuggester;
        this.duplicateDetector = duplicateDetector;
        this.correlator = correlator;
        this.resolutionSuggester = resolutionSuggester;
        this.riskPredictor = riskPredictor;
        this.priorityService = priorityService;
        this.slaResolver = slaResolver;
        this.assignmentService = assignmentService;
        this.accessPolicy = accessPolicy;
        this.currentUser = currentUser;
        this.publisher = publisher;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    // ================================================================ live analysis (while reporting)

    @Transactional(readOnly = true)
    public AnalysisResponse analyze(AnalyzeRequest request) {
        ITService service = catalogService.findActiveService(request.serviceId());
        IncidentText text = new IncidentText(request.title(), request.description(), service, null);
        List<CategorySuggestionDto> categories = suggestCategories(text).stream()
                .map(s -> new CategorySuggestionDto(mapper.ref(s.category()), s.confidence(), s.matchedKeywords()))
                .toList();
        List<SimilarIncidentDto> duplicates = findDuplicates(text, DuplicateDetector.SUSPECT_THRESHOLD).stream()
                .limit(5).map(this::similarDto).toList();

        PriorityPreview preview = null;
        if (request.impact() != null && request.urgency() != null) {
            int users = request.affectedUsers() == null ? 1 : request.affectedUsers();
            PriorityAssessment a = priorityService.assess(service, request.impact(), request.urgency(), users);
            Integer response = null;
            Integer resolution = null;
            try {
                SlaPolicy policy = slaResolver.resolve(service, a.priority());
                response = policy.getResponseMinutes();
                resolution = policy.getResolutionMinutes();
            } catch (SlaConfigurationMissingException ignored) {
                // preview still useful without SLA targets
            }
            preview = new PriorityPreview(a.priority(), a.score(), a.rationale(), a.strategyKey(), response, resolution);
        }
        return new AnalysisResponse(categories, duplicates, preview);
    }

    public List<CategorySuggestion> suggestCategories(IncidentText text) {
        List<Category> active = categoryRepository.findByActiveTrueOrderByName();
        return categorySuggester.suggest(text, active);
    }

    /** Open incidents within the duplicate window that resemble the given text. */
    public List<SimilarIncident> findDuplicates(IncidentText text, double minScore) {
        Instant now = clock.instant();
        Duration window = Duration.ofHours(properties.intelligence().duplicateWindowHours());
        List<Incident> pool = incidentRepository.findByReportedAtAfter(now.minus(window)).stream()
                .filter(i -> i.getStatus().isActive())
                .toList();
        return duplicateDetector.findSimilar(text, pool, now, window, minScore);
    }

    // ================================================================ incident-specific intelligence

    @Transactional(readOnly = true)
    public List<SimilarIncidentDto> similarTo(Long incidentId) {
        Incident incident = loadVisible(incidentId);
        IncidentText text = new IncidentText(incident.getTitle(), incident.getDescription(),
                incident.getAffectedService(), incident.getId());
        return findDuplicates(text, 0.35).stream().limit(8).map(this::similarDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ResolutionSuggestionDto> suggestedResolutions(Long incidentId) {
        Incident incident = loadVisible(incidentId);
        accessPolicy.requireSupportStaff(currentUser.currentUser(), "view suggested resolutions");
        return resolutionSuggester.suggest(incident, incidentRepository.findResolvedWithResolution(), 3).stream()
                .map(s -> new ResolutionSuggestionDto(s.sourceIncident().getId(), s.sourceIncident().getNumber(),
                        s.sourceIncident().getTitle(), mapper.ref(s.sourceIncident().getCategory()), s.similarity(),
                        s.summary(), s.rootCause()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommendations(Long incidentId) {
        Incident incident = loadVisible(incidentId);
        accessPolicy.requireSupportStaff(currentUser.currentUser(), "view technician recommendations");
        var strategy = assignmentService.activeStrategy();
        List<RecommendationDto> recs = assignmentService.recommend(incident).stream().limit(6)
                .map(r -> new RecommendationDto(mapper.userRef(r.technician()), r.score(), r.activeIncidents(),
                        r.maxIncidents(), r.reasons()))
                .toList();
        return new RecommendationResponse(strategy.key(), strategy.displayName(), recs);
    }

    // ================================================================ correlation & major incidents

    @Transactional(readOnly = true)
    public List<CorrelationClusterDto> correlations() {
        return currentClusters().stream().map(this::clusterDto).toList();
    }

    /** Called after a new incident is reported: alert once per root service per hour if a major incident forms. */
    public void checkForMajorIncident(Incident trigger) {
        Instant now = clock.instant();
        for (CorrelationCluster cluster : currentClusters()) {
            boolean containsTrigger = cluster.directIncidents().contains(trigger) || cluster.dependentIncidents().contains(trigger);
            if (!cluster.majorIncident() || !containsTrigger) continue;
            Long rootId = cluster.probableRoot().getId();
            Instant last = lastMajorAlert.get(rootId);
            if (last == null || Duration.between(last, now).compareTo(MAJOR_ALERT_COOLDOWN) > 0) {
                lastMajorAlert.put(rootId, now);
                publisher.publish(new IncidentEvent.MajorIncidentDetected(trigger, now, cluster));
            }
        }
    }

    private List<CorrelationCluster> currentClusters() {
        Instant since = clock.instant().minus(Duration.ofHours(properties.intelligence().correlationWindowHours()));
        List<Incident> open = incidentRepository.findByReportedAtAfter(since).stream()
                .filter(i -> i.getStatus().isActive() && i.getDuplicateOf() == null)
                .toList();
        return correlator.correlate(open, properties.intelligence().majorIncidentThreshold());
    }

    // ================================================================ SLA risk

    @Transactional(readOnly = true)
    public List<SlaRiskItem> slaRisks() {
        Instant now = clock.instant();
        return incidentRepository.findByStatusIn(IncidentStatus.ACTIVE).stream()
                .map(i -> {
                    SlaRiskAssessment r = assessRisk(i, now);
                    return r == null ? null : new SlaRiskItem(i.getId(), i.getNumber(), i.getTitle(), i.getPriority(),
                            i.getStatus(), mapper.userRef(i.getAssignedTo()), r.riskScore(), r.level().name(),
                            r.predictedResolution(), r.deadline(), r.factors());
                })
                .filter(Objects::nonNull)
                .filter(r -> r.riskScore() >= 0.5)
                .sorted(Comparator.comparingDouble(SlaRiskItem::riskScore).reversed())
                .toList();
    }

    public SlaRiskAssessment assessRisk(Incident incident, Instant now) {
        if (!incident.hasSla() || !incident.getStatus().isActive()) return null;
        int workload = incident.getAssignedTo() == null ? 0 : assignmentService.activeWorkload(incident.getAssignedTo().getId());
        return riskPredictor.assess(incident, now, workload, meanResolution(incident.getPriority(),
                incident.getCategory() == null ? null : incident.getCategory().getId()));
    }

    /** Mean time-to-resolve for (priority, category), cached for a few minutes. */
    Duration meanResolution(Priority priority, Long categoryId) {
        if (priority == null) return null;
        String key = priority + ":" + categoryId;
        Instant now = clock.instant();
        CachedDuration cached = meanResolutionCache.get(key);
        if (cached != null && Duration.between(cached.computedAt(), now).compareTo(STATS_TTL) < 0) return cached.value();
        List<Object[]> rows = incidentRepository.findResolutionDurations(priority, categoryId);
        if (rows.size() < 3 && categoryId != null) rows = incidentRepository.findResolutionDurations(priority, null);
        Duration mean = rows.size() < 3 ? null : Duration.ofSeconds((long) rows.stream()
                .mapToLong(r -> Duration.between((Instant) r[0], (Instant) r[1]).getSeconds())
                .average().orElse(0));
        meanResolutionCache.put(key, new CachedDuration(mean, now));
        return mean;
    }

    // ================================================================ helpers

    private Incident loadVisible(Long id) {
        Incident incident = incidentRepository.findDetailedById(id).orElseThrow(() -> new IncidentNotFoundException(id));
        User user = currentUser.currentUser();
        accessPolicy.requireView(user, incident);
        return incident;
    }

    private SimilarIncidentDto similarDto(SimilarIncident s) {
        Incident i = s.incident();
        return new SimilarIncidentDto(i.getId(), i.getNumber(), i.getTitle(), i.getStatus(), mapper.ref(i.getAffectedService()),
                i.getReportedAt(), s.score(), s.textSimilarity(), s.reasons());
    }

    private CorrelationClusterDto clusterDto(CorrelationCluster c) {
        return new CorrelationClusterDto(mapper.ref(c.probableRoot()), c.size(), c.majorIncident(), c.explanation(),
                c.directIncidents().stream().map(this::clusterIncident).toList(),
                c.dependentIncidents().stream().map(this::clusterIncident).toList());
    }

    private ClusterIncident clusterIncident(Incident i) {
        return new ClusterIncident(i.getId(), i.getNumber(), i.getTitle(), i.getPriority(), i.getStatus(),
                mapper.ref(i.getAffectedService()), i.getReportedAt());
    }
}
