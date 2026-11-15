package com.itsm.domain;

import com.itsm.TestData;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.sla.SlaRecord;
import com.itsm.domain.sla.SlaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.itsm.TestData.T0;
import static org.assertj.core.api.Assertions.assertThat;

/** SLA calculation, deadline tracking, warnings and violation detection. */
class SlaRecordTest {

    private final SlaRecord p1 = SlaRecord.start(TestData.policy(Priority.P1, 5, 30), T0);

    @Test
    @DisplayName("TC-04 SLA calculation: deadlines derive from the policy targets")
    void deadlines() {
        assertThat(p1.getResponseDeadline()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
        assertThat(p1.getResolutionDeadline()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
        assertThat(p1.status(T0.plusSeconds(60), null)).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("TC-05 Boundary: breach detected only strictly after the deadline, and only once")
    void responseBreachBoundary() {
        assertThat(p1.flagResponseBreachIfDue(T0.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(p1.flagResponseBreachIfDue(T0.plus(Duration.ofMinutes(5)).plusSeconds(1))).isTrue();
        assertThat(p1.flagResponseBreachIfDue(T0.plus(Duration.ofMinutes(6)))).isFalse(); // already recorded
        assertThat(p1.status(T0.plus(Duration.ofMinutes(6)), null)).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    @DisplayName("Warning is raised once when the warning threshold (75%) is reached")
    void warningOnce() {
        assertThat(p1.shouldWarn(T0.plus(Duration.ofMinutes(22)))).isFalse();   // 73%
        assertThat(p1.shouldWarn(T0.plus(Duration.ofMinutes(23)))).isTrue();    // 77%
        assertThat(p1.shouldWarn(T0.plus(Duration.ofMinutes(24)))).isFalse();
    }

    @Test
    @DisplayName("Resolved within target is MET; resolved late is BREACHED")
    void metOrBreached() {
        assertThat(p1.status(T0.plus(Duration.ofMinutes(40)), T0.plus(Duration.ofMinutes(25)))).isEqualTo(SlaStatus.MET);
        SlaRecord late = SlaRecord.start(TestData.policy(Priority.P1, 5, 30), T0);
        late.finaliseResolution(T0.plus(Duration.ofMinutes(31)));
        assertThat(late.isResolutionBreached()).isTrue();
    }

    @Test
    @DisplayName("Responding late records a response breach immediately")
    void lateResponse() {
        SlaRecord r = SlaRecord.start(TestData.policy(Priority.P2, 15, 120), T0);
        r.recordResponse(T0.plus(Duration.ofMinutes(16)));
        assertThat(r.isResponseBreached()).isTrue();
    }

    @Test
    @DisplayName("While paused, no breach is flagged")
    void pausedNoBreach() {
        SlaRecord r = SlaRecord.start(TestData.policy(Priority.P1, 5, 30), T0);
        r.recordResponse(T0.plusSeconds(60));
        r.pause(T0.plus(Duration.ofMinutes(10)));
        assertThat(r.flagResolutionBreachIfDue(T0.plus(Duration.ofHours(2)))).isFalse();
        assertThat(r.status(T0.plus(Duration.ofHours(2)), null)).isEqualTo(SlaStatus.PAUSED);
        r.resume(T0.plus(Duration.ofMinutes(20)));
        assertThat(r.getResolutionDeadline()).isEqualTo(T0.plus(Duration.ofMinutes(40)));
    }
}
