package com.itsm.service;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.settings.SystemSetting;
import com.itsm.engine.StrategyRegistry;
import com.itsm.engine.assignment.AssignmentStrategy;
import com.itsm.engine.assignment.SmartScoringAssignmentStrategy;
import com.itsm.engine.priority.MatrixPriorityStrategy;
import com.itsm.engine.priority.PriorityStrategy;
import com.itsm.repository.SystemSettingRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.CatalogDtos.StrategyOption;
import com.itsm.web.dto.CatalogDtos.StrategySettings;
import com.itsm.web.dto.CatalogDtos.StrategySettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Runtime selection of the active priority and assignment strategies.
 * // [Pattern: Strategy] "The system should allow the assignment strategy to be changed without redesigning
 * //                    the entire application" - it is a single persisted setting.
 */
@Service
public class SettingsService {

    private final SystemSettingRepository repository;
    private final StrategyRegistry<PriorityStrategy> priorityStrategies;
    private final StrategyRegistry<AssignmentStrategy> assignmentStrategies;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public SettingsService(SystemSettingRepository repository, StrategyRegistry<PriorityStrategy> priorityStrategies,
                           StrategyRegistry<AssignmentStrategy> assignmentStrategies, AuditService auditService,
                           CurrentUserProvider currentUser, Clock clock) {
        this.repository = repository;
        this.priorityStrategies = priorityStrategies;
        this.assignmentStrategies = assignmentStrategies;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PriorityStrategy activePriorityStrategy() {
        String key = value(SystemSetting.PRIORITY_STRATEGY, MatrixPriorityStrategy.KEY);
        return priorityStrategies.contains(key) ? priorityStrategies.get(key) : priorityStrategies.get(MatrixPriorityStrategy.KEY);
    }

    @Transactional(readOnly = true)
    public AssignmentStrategy activeAssignmentStrategy() {
        String key = value(SystemSetting.ASSIGNMENT_STRATEGY, SmartScoringAssignmentStrategy.KEY);
        return assignmentStrategies.contains(key) ? assignmentStrategies.get(key) : assignmentStrategies.get(SmartScoringAssignmentStrategy.KEY);
    }

    @Transactional(readOnly = true)
    public boolean autoAssignEnabled() {
        return Boolean.parseBoolean(value(SystemSetting.AUTO_ASSIGN, "true"));
    }

    @Transactional(readOnly = true)
    public StrategySettings current() {
        return new StrategySettings(activePriorityStrategy().key(), activeAssignmentStrategy().key(), autoAssignEnabled(),
                priorityStrategies.all().stream().map(s -> new StrategyOption(s.key(), s.displayName(), s.description())).toList(),
                assignmentStrategies.all().stream().map(s -> new StrategyOption(s.key(), s.displayName(), s.description())).toList());
    }

    @Transactional
    public StrategySettings update(StrategySettingsRequest request) {
        // Validate keys first (throws InvalidInputException listing available keys).
        priorityStrategies.get(request.priorityStrategy());
        assignmentStrategies.get(request.assignmentStrategy());
        var actor = currentUser.currentUser();
        change(SystemSetting.PRIORITY_STRATEGY, request.priorityStrategy(), actor);
        change(SystemSetting.ASSIGNMENT_STRATEGY, request.assignmentStrategy(), actor);
        change(SystemSetting.AUTO_ASSIGN, String.valueOf(request.autoAssign()), actor);
        return current();
    }

    /** Used by the seeder to initialise defaults. */
    @Transactional
    public void initialiseDefaults() {
        putIfAbsent(SystemSetting.PRIORITY_STRATEGY, MatrixPriorityStrategy.KEY);
        putIfAbsent(SystemSetting.ASSIGNMENT_STRATEGY, SmartScoringAssignmentStrategy.KEY);
        putIfAbsent(SystemSetting.AUTO_ASSIGN, "true");
    }

    private void change(String key, String newValue, com.itsm.domain.user.User actor) {
        SystemSetting setting = repository.findById(key).orElse(null);
        String old = setting == null ? null : setting.getValue();
        if (newValue.equals(old)) return;
        if (setting == null) repository.save(new SystemSetting(key, newValue, clock.instant(), actor.displayName()));
        else setting.update(newValue, clock.instant(), actor.displayName());
        auditService.recordAction(actor, AuditAction.CONFIGURATION_CHANGED, AuditService.CONFIG, null, key, old, newValue,
                "Setting " + key + " changed from " + old + " to " + newValue);
    }

    private void putIfAbsent(String key, String value) {
        if (!repository.existsById(key)) repository.save(new SystemSetting(key, value, clock.instant(), "SYSTEM"));
    }

    private String value(String key, String fallback) {
        return repository.findById(key).map(SystemSetting::getValue).orElse(fallback);
    }
}
