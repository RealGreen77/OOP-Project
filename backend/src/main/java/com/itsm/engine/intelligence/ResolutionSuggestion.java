package com.itsm.engine.intelligence;

import com.itsm.domain.incident.Incident;

/** A previously used resolution that may apply to the current incident. */
public record ResolutionSuggestion(Incident sourceIncident, double similarity, String summary, String rootCause) {
}
