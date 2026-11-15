package com.itsm.domain.incident;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps a persisted {@link IncidentStatus} to its (stateless, shared) {@link IncidentState} instance.
 *
 * // [Pattern: Factory + Flyweight] States hold no per-incident data, so one instance per status is reused.
 * // [Collections] EnumMap: compact, array-backed and ordered by enum declaration - ideal for enum keys.
 */
public final class IncidentStateFactory {

    private static final Map<IncidentStatus, IncidentState> STATES;

    static {
        EnumMap<IncidentStatus, IncidentState> map = new EnumMap<>(IncidentStatus.class);
        register(map, new NewState());
        register(map, new ClassifiedState());
        register(map, new AssignedState());
        register(map, new AcknowledgedState());
        register(map, new InProgressState());
        register(map, new PendingInfoState());
        register(map, new ResolvedState());
        register(map, new VerifiedState());
        register(map, new ClosedState());
        if (map.size() != IncidentStatus.values().length) {
            throw new IllegalStateException("Every IncidentStatus must have a state implementation");
        }
        STATES = Collections.unmodifiableMap(map);
    }

    private IncidentStateFactory() {
    }

    public static IncidentState forStatus(IncidentStatus status) {
        return STATES.get(status);
    }

    private static void register(Map<IncidentStatus, IncidentState> map, IncidentState state) {
        map.put(state.status(), state);
    }
}
