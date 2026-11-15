package com.itsm.service;

import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.user.Technician;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.TechnicianRepository;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.ReportDtos.Dashboard;
import com.itsm.web.dto.ReportDtos.LabelCount;
import com.itsm.web.dto.ReportDtos.Overview;
import com.itsm.web.dto.ReportDtos.SlaComplianceRow;
import com.itsm.web.dto.ReportDtos.TechnicianWorkloadRow;
import com.itsm.web.dto.ReportDtos.TrendPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Operational dashboards and analytics: incident overview, technician workload, service analysis,
 * category/priority breakdowns, SLA compliance and a daily trend.
 * // [MVP: Search & Reporting] [Innovation: analytics and visualisation]
 * // [Collections] EnumMap for status/priority counts, TreeMap for date-ordered trend buckets.
 */
@Service
public class ReportService {

    private static final int TREND_DAYS = 14;

    private final IncidentRepository incidentRepository;
    private final TechnicianRepository technicianRepository;
    private final DtoMapper mapper;
    private final Clock clock;

    public ReportService(IncidentRepository incidentRepository, TechnicianRepository technicianRepository,
                         DtoMapper mapper, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.technicianRepository = technicianRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Instant now = clock.instant();

        Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
        for (Object[] row : incidentRepository.countByStatus()) byStatus.put((IncidentStatus) row[0], (Long) row[1]);
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Object[] row : incidentRepository.countByPriority()) byPriority.put((Priority) row[0], (Long) row[1]);

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long open = IncidentStatus.ACTIVE.stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
        long resolved = byStatus.getOrDefault(IncidentStatus.RESOLVED, 0L) + byStatus.getOrDefault(IncidentStatus.VERIFIED, 0L)
                + byStatus.getOrDefault(IncidentStatus.CLOSED, 0L);
        long closed = byStatus.getOrDefault(IncidentStatus.CLOSED, 0L);

        var activeIncidents = incidentRepository.findByStatusIn(IncidentStatus.ACTIVE);
        long criticalOpen = activeIncidents.stream().filter(i -> i.getPriority() == Priority.P1).count();
        long escalatedOpen = activeIncidents.stream().filter(i -> i.getEscalationLevel() > 0).count();
        long unassignedOpen = activeIncidents.stream().filter(i -> i.getAssignedTo() == null).count();

        // Mean time to acknowledge / resolve over the last 90 days
        List<Object[]> timeline = incidentRepository.findTimelineSince(now.minus(Duration.ofDays(90)));
        double mtta = timeline.stream().filter(r -> r[1] != null)
                .mapToLong(r -> Duration.between((Instant) r[0], (Instant) r[1]).toMinutes()).average().orElse(Double.NaN);
        double mttr = timeline.stream().filter(r -> r[2] != null)
                .mapToLong(r -> Duration.between((Instant) r[0], (Instant) r[2]).toMinutes()).average().orElse(Double.NaN);

        List<SlaComplianceRow> compliance = new ArrayList<>();
        long resolvedWithSla = 0, breachedResolved = 0;
        for (Object[] row : incidentRepository.slaComplianceByPriority()) {
            long count = (Long) row[1];
            long breached = ((Number) row[2]).longValue();
            resolvedWithSla += count;
            breachedResolved += breached;
            compliance.add(new SlaComplianceRow(((Priority) row[0]).name(), count, breached,
                    round1(count == 0 ? 100 : 100.0 * (count - breached) / count)));
        }
        compliance.sort((a, b) -> a.priority().compareTo(b.priority()));

        // Technician workload & ratings
        Map<Long, Integer> workload = new HashMap<>();
        for (Object[] row : incidentRepository.countWorkloadByTechnician(IncidentStatus.TECHNICIAN_WORKLOAD)) {
            workload.put((Long) row[0], ((Long) row[1]).intValue());
        }
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] row : incidentRepository.technicianResolutionStats()) stats.put((Long) row[0], row);
        double ratingSum = 0;
        long ratingCount = 0;
        List<TechnicianWorkloadRow> workloadRows = new ArrayList<>();
        for (Technician t : technicianRepository.findAllActiveWithSkills()) {
            Object[] s = stats.get(t.getId());
            Double avg = s == null || s[1] == null ? null : round1(((Number) s[1]).doubleValue());
            long resolvedCount = s == null ? 0 : (Long) s[2];
            workloadRows.add(new TechnicianWorkloadRow(mapper.userRef(t), t.getAvailability().name(),
                    workload.getOrDefault(t.getId(), 0), t.getMaxConcurrentIncidents(), resolvedCount, avg));
            if (avg != null) { ratingSum += avg * resolvedCount; ratingCount += resolvedCount; }
        }
        workloadRows.sort((a, b) -> Integer.compare(b.activeIncidents(), a.activeIncidents()));

        Overview overview = new Overview(total, open, criticalOpen, resolved, closed, incidentRepository.countSlaBreaches(),
                escalatedOpen, unassignedOpen, nanToNull(mtta), nanToNull(mttr),
                resolvedWithSla == 0 ? null : round1(100.0 * (resolvedWithSla - breachedResolved) / resolvedWithSla),
                ratingCount == 0 ? null : round1(ratingSum / ratingCount));

        return new Dashboard(overview,
                byStatus.entrySet().stream().map(e -> new LabelCount(e.getKey().name(), e.getValue())).toList(),
                byPriority.entrySet().stream().map(e -> new LabelCount(e.getKey().name(), e.getValue())).toList(),
                toLabelCounts(incidentRepository.countByCategory()),
                toLabelCounts(incidentRepository.countByService()),
                workloadRows, compliance, trend(now), Map.of("at", now.toString()));
    }

    private List<TrendPoint> trend(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate start = today.minusDays(TREND_DAYS - 1L);
        TreeMap<LocalDate, long[]> buckets = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) buckets.put(d, new long[2]);
        for (Object[] row : incidentRepository.findTimelineSince(start.atStartOfDay().toInstant(ZoneOffset.UTC))) {
            long[] reported = buckets.get(LocalDate.ofInstant((Instant) row[0], ZoneOffset.UTC));
            if (reported != null) reported[0]++;
            if (row[2] != null) {
                long[] resolvedBucket = buckets.get(LocalDate.ofInstant((Instant) row[2], ZoneOffset.UTC));
                if (resolvedBucket != null) resolvedBucket[1]++;
            }
        }
        return buckets.entrySet().stream()
                .map(e -> new TrendPoint(e.getKey().toString(), e.getValue()[0], e.getValue()[1])).toList();
    }

    private static List<LabelCount> toLabelCounts(List<Object[]> rows) {
        return rows.stream().map(r -> new LabelCount(String.valueOf(r[0]), (Long) r[1])).toList();
    }

    private static Double nanToNull(double v) {
        return Double.isNaN(v) ? null : round1(v);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
