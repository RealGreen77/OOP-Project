package com.itsm.engine;

import com.itsm.TestData;
import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.Priority;
import com.itsm.engine.intelligence.CategorySuggestion;
import com.itsm.engine.intelligence.CorrelationCluster;
import com.itsm.engine.intelligence.DuplicateDetector;
import com.itsm.engine.intelligence.IncidentCorrelator;
import com.itsm.engine.intelligence.IncidentText;
import com.itsm.engine.intelligence.KeywordCategorySuggester;
import com.itsm.engine.intelligence.SimilarIncident;
import com.itsm.engine.intelligence.SlaRiskAssessment;
import com.itsm.engine.intelligence.SlaRiskPredictor;
import com.itsm.engine.intelligence.TextAnalyzer;
import com.itsm.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.itsm.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Intelligent features: duplicate detection, categorisation, correlation, SLA risk. */
class IntelligenceEngineTest {

    private final TextAnalyzer analyzer = new TextAnalyzer();
    private final Category application = TestData.category("APPLICATION", "payment", "checkout", "portal");
    private final Category network = TestData.category("NETWORK", "vpn", "wifi", "network");
    private final ITService coreDb = TestData.service("CORE_DB", BusinessCriticality.MISSION_CRITICAL, null);
    private final ITService payment = serviceDependingOn("PAYMENT", coreDb);
    private final ITService hr = serviceDependingOn("HR_PORTAL", coreDb);
    private final ITService email = TestData.service("EMAIL", BusinessCriticality.HIGH, application);

    @Test
    @DisplayName("TC-09 Duplicate detection: the spec's three payment incidents are recognised as related")
    void specPaymentExample() {
        Incident inc101 = TestData.incident("Payment application unavailable", "The payment application is unavailable for customers", payment, T0);
        Incident inc102 = TestData.incident("Payment transaction failing", "Every payment transaction is failing at checkout", payment, T0.plusSeconds(600));
        Incident unrelated = TestData.incident("Email attachments too large", "Cannot attach files over 25MB to emails", email, T0);

        List<SimilarIncident> matches = new DuplicateDetector(analyzer).findSimilar(
                new IncidentText("Unable to make payment", "I am unable to make a payment, it fails every time", payment, null),
                List.of(inc101, inc102, unrelated), T0.plusSeconds(1200), Duration.ofHours(72), DuplicateDetector.SUSPECT_THRESHOLD);

        assertThat(matches).extracting(SimilarIncident::incident).containsExactlyInAnyOrder(inc101, inc102);
        assertThat(matches.get(0).reasons()).anyMatch(r -> r.contains("Same service"));
    }

    @Test
    @DisplayName("Keyword categorisation suggests NETWORK for a VPN problem, with evidence")
    void categorisation() {
        List<CategorySuggestion> suggestions = new KeywordCategorySuggester(analyzer).suggest(
                new IncidentText("VPN keeps disconnecting", "Remote network access via VPN drops", email, null),
                List.of(application, network));
        assertThat(suggestions.get(0).category()).isSameAs(network);
        assertThat(suggestions.get(0).matchedKeywords()).contains("vpn", "network");
        assertThat(suggestions.get(0).confidence()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Correlation: incidents on services sharing a dependency cluster around the root service")
    void correlationFindsRoot() {
        Incident db = TestData.incident("Database CPU at 100%", "Primary database CPU is pinned", coreDb, T0);
        Incident pay = TestData.incident("Payments slow", "Checkout takes minutes to complete", payment, T0);
        Incident portal = TestData.incident("HR portal timeout", "HR portal times out on payslips", hr, T0);
        Incident other = TestData.incident("Email outage", "Cannot send any email today", email, T0);

        List<CorrelationCluster> clusters = new IncidentCorrelator().correlate(List.of(db, pay, portal, other), 3);
        assertThat(clusters).hasSize(1);
        CorrelationCluster c = clusters.get(0);
        assertThat(c.probableRoot()).isSameAs(coreDb);
        assertThat(c.size()).isEqualTo(3);
        assertThat(c.majorIncident()).isTrue();
    }

    @Test
    @DisplayName("Correlation: a root inferred only from dependents is a possible cause, never a major incident")
    void inferredRootIsNotMajor() {
        ITService erp = serviceDependingOn("ERP", coreDb);
        Incident pay = TestData.incident("Payments slow", "Checkout takes minutes to complete", payment, T0);
        Incident portal = TestData.incident("HR portal timeout", "HR portal times out on payslips", hr, T0);
        Incident reports = TestData.incident("ERP reports failing", "Month-end reports time out", erp, T0);

        List<CorrelationCluster> clusters = new IncidentCorrelator().correlate(List.of(pay, portal, reports), 3);
        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).probableRoot()).isSameAs(coreDb);
        assertThat(clusters.get(0).majorIncident()).isFalse();
        assertThat(clusters.get(0).explanation()).startsWith("Possible shared cause");
    }

    @Test
    @DisplayName("SLA risk: an unassigned incident near its deadline is HIGH risk; a fresh one is LOW")
    void slaRisk() {
        Incident i = TestData.classifiedIncident(network, email, Priority.P2, TestData.policy(Priority.P2, 15, 120));
        SlaRiskPredictor predictor = new SlaRiskPredictor();
        SlaRiskAssessment fresh = predictor.assess(i, T0.plus(Duration.ofMinutes(5)), 0, Duration.ofMinutes(30));
        SlaRiskAssessment late = predictor.assess(i, T0.plus(Duration.ofMinutes(110)), 0, Duration.ofMinutes(60));
        assertThat(fresh.level()).isEqualTo(SlaRiskAssessment.RiskLevel.LOW);
        assertThat(late.level()).isEqualTo(SlaRiskAssessment.RiskLevel.HIGH);
        assertThat(late.factors()).anyMatch(f -> f.contains("Not yet assigned"));
    }

    @Test
    @DisplayName("SLA risk: a paused clock past its original deadline is not reported as breached")
    void pausedClockIsNotBreached() {
        Incident i = TestData.classifiedIncident(network, email, Priority.P2, TestData.policy(Priority.P2, 15, 120));
        i.getSla().pause(T0.plus(Duration.ofMinutes(60)));
        SlaRiskAssessment risk = new SlaRiskPredictor().assess(i, T0.plus(Duration.ofMinutes(180)), 0, Duration.ofMinutes(30));
        assertThat(risk.level()).isNotEqualTo(SlaRiskAssessment.RiskLevel.BREACHED);
        assertThat(risk.factors()).anyMatch(f -> f.contains("paused"));
    }

    @Test
    @DisplayName("Service dependency graph rejects cycles")
    void dependencyCycle() {
        assertThatThrownBy(() -> coreDb.addDependency(payment)).isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("Text analyser normalises synonyms and removes stop words")
    void textAnalyser() {
        assertThat(analyzer.tokens("Unable to make payment")).containsExactly("fail", "payment");
        assertThat(analyzer.tokens("The VPN is down!")).containsExactly("vpn", "fail");
    }

    private static ITService serviceDependingOn(String code, ITService dependency) {
        ITService s = TestData.service(code, BusinessCriticality.HIGH, null);
        s.addDependency(dependency);
        return s;
    }
}
