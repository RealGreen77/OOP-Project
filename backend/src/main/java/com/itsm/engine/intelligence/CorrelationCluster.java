package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Incident;

import java.util.List;

/**
 * A group of open incidents that appear to share a common cause.
 *
 * @param probableRoot       the service most likely at fault (directly affected or a shared dependency)
 * @param directIncidents    incidents reported directly against the root service
 * @param dependentIncidents incidents on services that depend on the root
 * @param majorIncident      true if the cluster is large enough to be treated as a major incident
 */
public record CorrelationCluster(
        ITService probableRoot,
        List<Incident> directIncidents,
        List<Incident> dependentIncidents,
        boolean majorIncident,
        String explanation) {

    public int size() {
        return directIncidents.size() + dependentIncidents.size();
    }
}
