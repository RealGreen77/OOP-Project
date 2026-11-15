package com.itsm.domain;

import com.itsm.TestData;
import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.ClosureCode;
import com.itsm.domain.incident.Feedback;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentAction;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Resolution;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.user.Technician;
import com.itsm.exception.BusinessRuleViolationException;
import com.itsm.exception.InvalidInputException;
import com.itsm.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.itsm.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State pattern: legal and illegal lifecycle transitions. */
class IncidentLifecycleTest {

    private Category network;
    private ITService vpn;
    private Technician tech;
    private SlaPolicy p2;

    @BeforeEach
    void setUp() {
        network = TestData.category("NETWORK", "vpn");
        vpn = TestData.service("VPN", BusinessCriticality.HIGH, network);
        tech = TestData.technician("T1", 5, 5, network);
        p2 = TestData.policy(Priority.P2, 15, 120);
    }

    @Test
    @DisplayName("TC-01 Happy path: NEW -> CLASSIFIED -> ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> VERIFIED -> CLOSED")
    void fullLifecycle() {
        Incident i = TestData.incident("VPN is down", "VPN disconnects for all remote users", vpn, T0);
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.NEW);

        i.classify(network, T0);
        i.applyPriority(Priority.P2, 20, "test");
        i.applySlaPolicy(p2);
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.CLASSIFIED);

        i.assignTo(tech, T0.plusSeconds(60));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
        i.acknowledge(T0.plusSeconds(300));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
        assertThat(i.getSla().getRespondedAt()).isEqualTo(T0.plusSeconds(300));
        i.startWork(T0.plusSeconds(400));
        i.resolve(new Resolution("Restarted the VPN concentrator", "Memory leak", false), T0.plusSeconds(3600));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        i.verify(T0.plusSeconds(4000));
        i.close(ClosureCode.RESOLVED, "done", T0.plusSeconds(4100));

        assertThat(i.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(i.getClosedAt()).isEqualTo(T0.plusSeconds(4100));
        assertThat(i.getSla().isBreached()).isFalse();
        assertThat(i.allowedActions()).containsExactly(IncidentAction.FEEDBACK);
    }

    @Test
    @DisplayName("TC-02 Invalid transition: NEW -> CLOSED is rejected")
    void newCannotBeClosed() {
        Incident i = TestData.incident("Printer broken", "Office printer does not print", vpn, T0);
        assertThatThrownBy(() -> i.close(ClosureCode.RESOLVED, null, T0))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CLOSE").hasMessageContaining("NEW");
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    @Test
    @DisplayName("TC-03 Invalid transition: cannot resolve before the technician starts work")
    void cannotResolveFromAssigned() {
        Incident i = TestData.classifiedIncident(network, vpn, Priority.P2, p2);
        i.assignTo(tech, T0);
        assertThatThrownBy(() -> i.resolve(new Resolution("A long enough summary", null, false), T0))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Reopening a resolved incident returns it to IN_PROGRESS and counts the reopen")
    void reopen() {
        Incident i = inProgress();
        i.resolve(new Resolution("Applied a temporary fix", null, true), T0.plusSeconds(600));
        i.reopen(T0.plusSeconds(700));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(i.getReopenCount()).isEqualTo(1);
        assertThat(i.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("Waiting on the reporter pauses the SLA clock and resuming pushes the deadline back")
    void pendingInfoPausesSla() {
        Incident i = inProgress();
        Instant deadline = i.getSla().getResolutionDeadline();
        i.requestInformation(T0.plusSeconds(600));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.PENDING_INFO);
        assertThat(i.getSla().isPaused()).isTrue();
        i.provideInformation(T0.plusSeconds(600 + 1800));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(i.getSla().getResolutionDeadline()).isEqualTo(deadline.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Duplicates: closing as duplicate links the parent; self-duplicates are rejected")
    void duplicates() {
        Incident parent = TestData.classifiedIncident(network, vpn, Priority.P2, p2);
        Incident child = TestData.classifiedIncident(network, vpn, Priority.P2, p2);
        assertThatThrownBy(() -> child.markDuplicateOf(child, T0)).isInstanceOf(InvalidInputException.class);
        child.markDuplicateOf(parent, T0);
        assertThat(child.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(child.getClosureCode()).isEqualTo(ClosureCode.DUPLICATE);
        assertThat(child.getDuplicateOf()).isSameAs(parent);
        assertThatThrownBy(() -> child.markDuplicateOf(parent, T0)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Feedback: only after resolution, rating 1-5, only once")
    void feedbackRules() {
        Incident i = inProgress();
        assertThatThrownBy(() -> i.submitFeedback(new Feedback(5, "great", T0)))
                .isInstanceOf(InvalidStateTransitionException.class);
        i.resolve(new Resolution("Replaced the faulty cable", null, false), T0.plusSeconds(60));
        assertThatThrownBy(() -> new Feedback(6, "too high", T0)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> new Feedback(0, "too low", T0)).isInstanceOf(InvalidInputException.class);
        i.submitFeedback(new Feedback(4, "good", T0));
        assertThatThrownBy(() -> i.submitFeedback(new Feedback(5, "again", T0)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Reassignment sends the incident back to ASSIGNED for the new technician")
    void reassignment() {
        Incident i = inProgress();
        Technician other = TestData.technician("T2", 2, 5, network);
        i.assignTo(other, T0.plusSeconds(900));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(i.isAssignedTo(other)).isTrue();
    }

    @Test
    @DisplayName("Boundary: incident title and description length are validated by the domain")
    void detailValidation() {
        assertThatThrownBy(() -> TestData.incident("Hey", "A sufficiently long description", vpn, T0))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("Title");
        assertThatThrownBy(() -> TestData.incident("Valid title", "short", vpn, T0))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("Description");
    }

    private Incident inProgress() {
        Incident i = TestData.classifiedIncident(network, vpn, Priority.P2, p2);
        i.assignTo(tech, T0);
        i.acknowledge(T0.plusSeconds(60));
        i.startWork(T0.plusSeconds(120));
        return i;
    }
}
