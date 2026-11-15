package com.itsm.engine;

import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Urgency;
import com.itsm.engine.priority.MatrixPriorityStrategy;
import com.itsm.engine.priority.PriorityAssessment;
import com.itsm.engine.priority.PriorityContext;
import com.itsm.engine.priority.PriorityStrategy;
import com.itsm.engine.priority.WeightedScoringPriorityStrategy;
import com.itsm.exception.InvalidInputException;
import com.itsm.repository.IncidentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static com.itsm.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Strategy pattern: interchangeable priority algorithms. */
class PriorityStrategyTest {

    private final MatrixPriorityStrategy matrix = new MatrixPriorityStrategy();

    @ParameterizedTest(name = "{0} x {1} x {2} -> {3}")
    @DisplayName("TC-06 Matrix priority boundaries (32 -> P1, 16 -> P2, 6 -> P3, below -> P4)")
    @CsvSource({
            "CRITICAL, CRITICAL, MISSION_CRITICAL, P1",   // 64
            "CRITICAL, CRITICAL, MEDIUM, P1",             // 32 (boundary)
            "HIGH, HIGH, HIGH, P2",                       // 27
            "CRITICAL, CRITICAL, LOW, P2",                // 16 (boundary)
            "HIGH, MEDIUM, LOW, P3",                      // 6 (boundary)
            "MEDIUM, MEDIUM, LOW, P4",                    // 4
            "LOW, LOW, LOW, P4"                           // 1 (minimum)
    })
    void matrixBoundaries(Impact impact, Urgency urgency, BusinessCriticality criticality, Priority expected) {
        PriorityAssessment a = matrix.assess(new PriorityContext(impact, urgency, criticality, 1, null));
        assertThat(a.priority()).isEqualTo(expected);
        assertThat(a.rationale()).contains(expected.name());
    }

    @Test
    @DisplayName("Weighted strategy: affected users and recurring incidents raise priority")
    void weightedUsesReachAndHistory() {
        IncidentRepository repo = mock(IncidentRepository.class);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        WeightedScoringPriorityStrategy weighted = new WeightedScoringPriorityStrategy(repo, clock);

        when(repo.countByAffectedServiceIdAndReportedAtAfter(eq(1L), any())).thenReturn(0L);
        PriorityAssessment single = weighted.assess(new PriorityContext(Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, 1, 1L));
        PriorityAssessment many = weighted.assess(new PriorityContext(Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, 1000, 1L));
        assertThat(many.score()).isGreaterThan(single.score());

        when(repo.countByAffectedServiceIdAndReportedAtAfter(eq(1L), any())).thenReturn(5L);
        PriorityAssessment recurring = weighted.assess(new PriorityContext(Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, 1, 1L));
        assertThat(recurring.score()).isEqualTo(single.score() + 8.0);
        assertThat(recurring.rationale()).contains("recurrence boost");
    }

    @Test
    @DisplayName("Polymorphism: the registry returns strategies by key and rejects unknown keys")
    void registry() {
        StrategyRegistry<PriorityStrategy> registry = new StrategyRegistry<>("priority",
                List.of(matrix, new WeightedScoringPriorityStrategy(mock(IncidentRepository.class), Clock.systemUTC())));
        assertThat(registry.get("matrix")).isSameAs(matrix);
        assertThat(registry.all()).hasSize(2);
        assertThatThrownBy(() -> registry.get("magic")).isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("matrix").hasMessageContaining("weighted");
    }
}
