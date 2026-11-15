package com.itsm.config;

import com.itsm.engine.StrategyRegistry;
import com.itsm.engine.assignment.AssignmentStrategy;
import com.itsm.engine.priority.PriorityStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/** Core beans: time source and strategy registries. */
@Configuration
public class CoreConfig {

    /** Injected everywhere instead of calling Instant.now() directly, so time-dependent logic is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Every PriorityStrategy bean is discovered automatically. // [Pattern: Strategy] */
    @Bean
    public StrategyRegistry<PriorityStrategy> priorityStrategies(List<PriorityStrategy> strategies) {
        return new StrategyRegistry<>("priority", strategies);
    }

    /** Every AssignmentStrategy bean is discovered automatically. // [Pattern: Strategy] */
    @Bean
    public StrategyRegistry<AssignmentStrategy> assignmentStrategies(List<AssignmentStrategy> strategies) {
        return new StrategyRegistry<>("assignment", strategies);
    }
}
