package com.itsm.repository;

import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.sla.SlaStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Immutable set of search filters. Eight independent criteria:
 * text / incident number, date range, status, priority, category, service, technician, SLA status
 * (plus reporter scoping and the "escalated only" flag).
 *
 * // [MVP: Search & Reporting] At least five meaningful search/filter criteria.
 * // [Pattern: Builder] Built fluently via {@link #builder()} because most fields are optional.
 */
public record IncidentSearchCriteria(
        String text,
        Instant reportedFrom,
        Instant reportedTo,
        Set<IncidentStatus> statuses,
        Set<Priority> priorities,
        Long categoryId,
        Long serviceId,
        Long technicianId,
        Long reporterId,
        SlaStatus slaStatus,
        Boolean escalatedOnly,
        Boolean unassignedOnly) {

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().text(text).reportedFrom(reportedFrom).reportedTo(reportedTo).statuses(statuses)
                .priorities(priorities).categoryId(categoryId).serviceId(serviceId).technicianId(technicianId)
                .reporterId(reporterId).slaStatus(slaStatus).escalatedOnly(escalatedOnly).unassignedOnly(unassignedOnly);
    }

    public static final class Builder {
        private String text;
        private Instant reportedFrom;
        private Instant reportedTo;
        private Set<IncidentStatus> statuses;
        private Set<Priority> priorities;
        private Long categoryId;
        private Long serviceId;
        private Long technicianId;
        private Long reporterId;
        private SlaStatus slaStatus;
        private Boolean escalatedOnly;
        private Boolean unassignedOnly;

        public Builder text(String v) { this.text = v; return this; }
        public Builder reportedFrom(Instant v) { this.reportedFrom = v; return this; }
        public Builder reportedTo(Instant v) { this.reportedTo = v; return this; }
        public Builder statuses(Set<IncidentStatus> v) { this.statuses = v; return this; }
        public Builder priorities(Set<Priority> v) { this.priorities = v; return this; }
        public Builder categoryId(Long v) { this.categoryId = v; return this; }
        public Builder serviceId(Long v) { this.serviceId = v; return this; }
        public Builder technicianId(Long v) { this.technicianId = v; return this; }
        public Builder reporterId(Long v) { this.reporterId = v; return this; }
        public Builder slaStatus(SlaStatus v) { this.slaStatus = v; return this; }
        public Builder escalatedOnly(Boolean v) { this.escalatedOnly = v; return this; }
        public Builder unassignedOnly(Boolean v) { this.unassignedOnly = v; return this; }

        public IncidentSearchCriteria build() {
            return new IncidentSearchCriteria(text, reportedFrom, reportedTo, statuses, priorities, categoryId,
                    serviceId, technicianId, reporterId, slaStatus, escalatedOnly, unassignedOnly);
        }
    }
}
