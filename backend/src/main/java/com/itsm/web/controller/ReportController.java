package com.itsm.web.controller;

import com.itsm.service.ReportService;
import com.itsm.web.dto.ReportDtos.Dashboard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Operational dashboard and analytics")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    @Operation(summary = "Overview, workload, service/category/priority analysis, SLA compliance, 14-day trend")
    public Dashboard dashboard() {
        return reportService.dashboard();
    }
}
