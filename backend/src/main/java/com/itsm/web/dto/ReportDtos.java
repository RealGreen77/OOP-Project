package com.itsm.web.dto;

import com.itsm.web.dto.CommonDtos.UserRef;

import java.util.List;
import java.util.Map;

/** Dashboard and analytics DTOs. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record Overview(
            long totalIncidents,
            long openIncidents,
            long criticalOpen,
            long resolvedIncidents,
            long closedIncidents,
            long slaViolations,
            long escalatedOpen,
            long unassignedOpen,
            Double meanTimeToAcknowledgeMinutes,
            Double meanTimeToResolveMinutes,
            Double slaCompliancePercent,
            Double averageRating) {}

    public record LabelCount(String label, long count) {}

    public record TechnicianWorkloadRow(UserRef technician, String availability, int activeIncidents, int maxIncidents,
                                        long resolvedTotal, Double averageRating) {}

    public record SlaComplianceRow(String priority, long resolved, long breached, double compliancePercent) {}

    public record TrendPoint(String date, long reported, long resolved) {}

    public record Dashboard(
            Overview overview,
            List<LabelCount> byStatus,
            List<LabelCount> byPriority,
            List<LabelCount> byCategory,
            List<LabelCount> byService,
            List<TechnicianWorkloadRow> technicianWorkload,
            List<SlaComplianceRow> slaCompliance,
            List<TrendPoint> trend,
            Map<String, Object> generatedAt) {}
}
