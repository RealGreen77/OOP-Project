package com.itsm.engine.intelligence;

import com.itsm.domain.incident.Incident;

import java.util.List;

/** An existing incident judged similar to the one being analysed. */
public record SimilarIncident(Incident incident, double score, double textSimilarity, List<String> reasons) {
}
