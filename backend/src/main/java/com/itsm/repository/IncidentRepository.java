package com.itsm.repository;

import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * // [Pattern: Repository] Plus {@link JpaSpecificationExecutor} for dynamic multi-criteria search.
 */
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTo"})
    Optional<Incident> findByNumberIgnoreCase(String number);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTo"})
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"reporter", "category", "affectedService", "assignedTo"})
    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"category", "affectedService", "assignedTo"})
    List<Incident> findByReportedAtAfter(Instant since);

    @EntityGraph(attributePaths = {"category", "affectedService"})
    List<Incident> findByReporterIdAndReportedAtAfter(Long reporterId, Instant since);

    long countByAssignedToIdAndStatusIn(Long technicianId, Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"affectedService", "category", "reporter"})
    List<Incident> findByAssignedToIdAndStatusInOrderByReportedAtAsc(Long technicianId, Collection<IncidentStatus> statuses);

    long countByAffectedServiceIdAndReportedAtAfter(Long serviceId, Instant since);

    /** Active incident count per technician: rows of [technicianId, count]. */
    @Query("""
            select i.assignedTo.id, count(i) from Incident i
            where i.assignedTo is not null and i.status in :statuses
            group by i.assignedTo.id
            """)
    List<Object[]> countWorkloadByTechnician(@Param("statuses") Collection<IncidentStatus> statuses);

    /** Incidents resolved per technician for a given service: rows of [technicianId, count]. */
    @Query("""
            select i.assignedTo.id, count(i) from Incident i
            where i.assignedTo is not null and i.resolvedAt is not null and i.affectedService.id = :serviceId
            group by i.assignedTo.id
            """)
    List<Object[]> countResolvedByTechnicianForService(@Param("serviceId") Long serviceId);

    /** Historical incidents with a recorded resolution, used by the resolution suggester. */
    @EntityGraph(attributePaths = {"category", "affectedService"})
    @Query("""
            select i from Incident i
            where i.resolution.summary is not null and i.resolvedAt is not null
              and (i.closureCode is null or i.closureCode <> com.itsm.domain.incident.ClosureCode.DUPLICATE)
            """)
    List<Incident> findResolvedWithResolution();

    /** [reportedAt, resolvedAt] pairs for computing historical mean time to resolve. */
    @Query("""
            select i.reportedAt, i.resolvedAt from Incident i
            where i.resolvedAt is not null and i.priority = :priority
              and (:categoryId is null or i.category.id = :categoryId)
            """)
    List<Object[]> findResolutionDurations(@Param("priority") Priority priority, @Param("categoryId") Long categoryId);

    // ---------------------------------------------------------------- reporting aggregates

    @Query("select i.status, count(i) from Incident i group by i.status")
    List<Object[]> countByStatus();

    @Query("select i.priority, count(i) from Incident i where i.priority is not null group by i.priority")
    List<Object[]> countByPriority();

    @Query("select i.affectedService.name, count(i) from Incident i group by i.affectedService.name order by count(i) desc")
    List<Object[]> countByService();

    @Query("select coalesce(i.category.name, 'Unclassified'), count(i) from Incident i left join i.category group by i.category.name order by count(i) desc")
    List<Object[]> countByCategory();

    @Query("select count(i) from Incident i where i.sla.responseBreached = true or i.sla.resolutionBreached = true")
    long countSlaBreaches();

    @Query("select i.priority, count(i), sum(case when (i.sla.responseBreached = true or i.sla.resolutionBreached = true) then 1 else 0 end) from Incident i where i.priority is not null and i.resolvedAt is not null group by i.priority")
    List<Object[]> slaComplianceByPriority();

    @Query("select i.reportedAt, i.acknowledgedAt, i.resolvedAt from Incident i where i.reportedAt >= :since")
    List<Object[]> findTimelineSince(@Param("since") Instant since);

    @Query("select i.assignedTo.id, avg(i.feedback.rating), count(i) from Incident i where i.assignedTo is not null and i.resolvedAt is not null group by i.assignedTo.id")
    List<Object[]> technicianResolutionStats();
}
