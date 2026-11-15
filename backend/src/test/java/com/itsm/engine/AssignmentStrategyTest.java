package com.itsm.engine;

import com.itsm.TestData;
import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.user.AvailabilityStatus;
import com.itsm.domain.user.Technician;
import com.itsm.engine.assignment.AssignmentStrategy;
import com.itsm.engine.assignment.LoadBalancedAssignmentStrategy;
import com.itsm.engine.assignment.RoundRobinAssignmentStrategy;
import com.itsm.engine.assignment.SkillBasedAssignmentStrategy;
import com.itsm.engine.assignment.SmartScoringAssignmentStrategy;
import com.itsm.engine.assignment.TechnicianCandidate;
import com.itsm.engine.assignment.TechnicianRecommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Strategy pattern + Template Method: technician assignment algorithms. */
class AssignmentStrategyTest {

    private Category database;
    private Category network;
    private Incident dbIncident;
    private Technician junior;      // DB skill, 1 year, 1/5 busy
    private Technician senior;      // DB skill, 8 years, 3/5 busy
    private Technician networker;   // network only, idle
    private Technician saturated;   // DB skill, full

    @BeforeEach
    void setUp() {
        database = TestData.category("DATABASE", "sql");
        network = TestData.category("NETWORK", "vpn");
        ITService erp = TestData.service("ERP", BusinessCriticality.HIGH, database);
        dbIncident = TestData.classifiedIncident(database, erp, Priority.P3, TestData.policy(Priority.P3, 60, 480));
        junior = TestData.technician("T1", 1, 5, database);
        senior = TestData.technician("T2", 8, 5, database);
        networker = TestData.technician("T3", 4, 5, network);
        saturated = TestData.technician("T4", 6, 2, database);
    }

    private List<TechnicianCandidate> candidates() {
        return List.of(
                new TechnicianCandidate(junior, 1, 0),
                new TechnicianCandidate(senior, 3, 4),
                new TechnicianCandidate(networker, 0, 0),
                new TechnicianCandidate(saturated, 2, 9));
    }

    @Test
    @DisplayName("TC-07 Skill-based: only skilled technicians with capacity; least loaded wins")
    void skillBased() {
        List<TechnicianRecommendation> ranked = new SkillBasedAssignmentStrategy().rank(dbIncident, candidates());
        assertThat(ranked).extracting(TechnicianRecommendation::technician).containsExactly(junior, senior);
    }

    @Test
    @DisplayName("Load-balanced: the idle technician wins even without the skill")
    void loadBalanced() {
        List<TechnicianRecommendation> ranked = new LoadBalancedAssignmentStrategy().rank(dbIncident, candidates());
        assertThat(ranked.get(0).technician()).isSameAs(networker);
        assertThat(ranked).extracting(TechnicianRecommendation::technician).doesNotContain(saturated);
    }

    @Test
    @DisplayName("Round-robin: consecutive selections rotate through skilled technicians")
    void roundRobin() {
        AssignmentStrategy rr = new RoundRobinAssignmentStrategy();
        Technician first = rr.select(dbIncident, candidates()).orElseThrow().technician();
        Technician second = rr.select(dbIncident, candidates()).orElseThrow().technician();
        Technician third = rr.select(dbIncident, candidates()).orElseThrow().technician();
        assertThat(first).isNotSameAs(second);
        assertThat(third).isSameAs(first);
    }

    @Test
    @DisplayName("Smart scoring: P1 prefers the experienced engineer with service history; reasons are explained")
    void smartPrefersSeniorForCritical() {
        Incident critical = TestData.classifiedIncident(database, dbIncident.getAffectedService(), Priority.P1,
                TestData.policy(Priority.P1, 5, 30));
        List<TechnicianRecommendation> ranked = new SmartScoringAssignmentStrategy().rank(critical, candidates());
        assertThat(ranked.get(0).technician()).isSameAs(senior);
        assertThat(ranked.get(0).reasons()).anyMatch(r -> r.contains("Senior engineer"));
    }

    @Test
    @DisplayName("TC-08 Technician unavailable: nobody on leave or at capacity is ever selected")
    void unavailableTechniciansExcluded() {
        junior.changeAvailability(AvailabilityStatus.ON_LEAVE);
        senior.changeAvailability(AvailabilityStatus.OFFLINE);
        List<TechnicianCandidate> onlyDb = List.of(new TechnicianCandidate(junior, 0, 0),
                new TechnicianCandidate(senior, 0, 0), new TechnicianCandidate(saturated, 2, 0));
        assertThat(new SkillBasedAssignmentStrategy().select(dbIncident, onlyDb)).isEmpty();
        assertThat(new SmartScoringAssignmentStrategy().select(dbIncident, onlyDb)).isEmpty();
        assertThat(new RoundRobinAssignmentStrategy().select(dbIncident, onlyDb)).isEmpty();
    }
}
