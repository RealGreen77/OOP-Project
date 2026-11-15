package com.itsm.web.controller;

import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.sla.SlaStatus;
import com.itsm.repository.IncidentSearchCriteria;
import com.itsm.service.IncidentService;
import com.itsm.web.dto.CommonDtos.PageResponse;
import com.itsm.web.dto.IncidentDtos.AssignRequest;
import com.itsm.web.dto.IncidentDtos.AuditEntryDto;
import com.itsm.web.dto.IncidentDtos.ClassifyRequest;
import com.itsm.web.dto.IncidentDtos.CloseRequest;
import com.itsm.web.dto.IncidentDtos.DuplicateRequest;
import com.itsm.web.dto.IncidentDtos.FeedbackRequest;
import com.itsm.web.dto.IncidentDtos.IncidentDetail;
import com.itsm.web.dto.IncidentDtos.IncidentSummary;
import com.itsm.web.dto.IncidentDtos.NoteRequest;
import com.itsm.web.dto.IncidentDtos.ReportIncidentRequest;
import com.itsm.web.dto.IncidentDtos.RequiredCommentRequest;
import com.itsm.web.dto.IncidentDtos.ResolveRequest;
import com.itsm.web.dto.IncidentDtos.UpdateIncidentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** Incident lifecycle API. Object-level checks live in the service (IncidentAccessPolicy). */
@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents", description = "Report, search and work incidents through their lifecycle")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REPORT_INCIDENT')")
    @Operation(summary = "Report an incident (auto classify, prioritise, apply SLA, detect duplicates, assign)")
    public IncidentDetail report(@Valid @RequestBody ReportIncidentRequest request) {
        return incidentService.report(request);
    }

    @GetMapping
    @Operation(summary = "Search incidents (employees only see their own)")
    public PageResponse<IncidentSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Set<IncidentStatus> status,
            @RequestParam(required = false) Set<Priority> priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) Boolean escalated,
            @RequestParam(required = false) Boolean unassigned,
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return incidentService.search(criteria(q, from, to, status, priority, categoryId, serviceId, technicianId,
                reporterId, slaStatus, escalated, unassigned), pageable);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('VIEW_ALL_INCIDENTS')")
    @Operation(summary = "Export the filtered incident list as CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Set<IncidentStatus> status,
            @RequestParam(required = false) Set<Priority> priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) Boolean escalated) {
        String csv = incidentService.exportCsv(criteria(q, from, to, status, priority, categoryId, serviceId,
                technicianId, null, slaStatus, escalated, null));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"incidents.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/my-queue")
    @PreAuthorize("hasAuthority('WORK_INCIDENT')")
    @Operation(summary = "Technician's own active incidents, most urgent first")
    public List<IncidentSummary> myQueue() {
        return incidentService.myQueue();
    }

    @GetMapping("/{id}")
    public IncidentDetail get(@PathVariable Long id) {
        return incidentService.get(id);
    }

    @GetMapping("/number/{number}")
    public IncidentDetail getByNumber(@PathVariable String number) {
        return incidentService.getByNumber(number);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Complete audit trail of the incident")
    public List<AuditEntryDto> history(@PathVariable Long id) {
        return incidentService.history(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update details (re-prioritises automatically)")
    public IncidentDetail update(@PathVariable Long id, @Valid @RequestBody UpdateIncidentRequest request) {
        return incidentService.update(id, request);
    }

    @PostMapping("/{id}/classify")
    public IncidentDetail classify(@PathVariable Long id, @Valid @RequestBody ClassifyRequest request) {
        return incidentService.classify(id, request.categoryId());
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign to a technician, or omit technicianId to use the active assignment strategy")
    public IncidentDetail assign(@PathVariable Long id, @RequestBody(required = false) AssignRequest request) {
        return incidentService.assign(id, request == null ? null : request.technicianId());
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('WORK_INCIDENT')")
    public IncidentDetail acknowledge(@PathVariable Long id) {
        return incidentService.acknowledge(id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('WORK_INCIDENT')")
    public IncidentDetail start(@PathVariable Long id) {
        return incidentService.startWork(id);
    }

    @PostMapping("/{id}/request-info")
    @PreAuthorize("hasAuthority('WORK_INCIDENT')")
    public IncidentDetail requestInfo(@PathVariable Long id, @Valid @RequestBody RequiredCommentRequest request) {
        return incidentService.requestInformation(id, request.comment());
    }

    @PostMapping("/{id}/provide-info")
    public IncidentDetail provideInfo(@PathVariable Long id, @Valid @RequestBody RequiredCommentRequest request) {
        return incidentService.provideInformation(id, request.comment());
    }

    @PostMapping("/{id}/notes")
    public IncidentDetail addNote(@PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        return incidentService.addNote(id, request.content(), request.internal(), request.type());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('WORK_INCIDENT')")
    public IncidentDetail resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request) {
        return incidentService.resolve(id, request.summary(), request.rootCause(), request.workaround());
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Reporter confirms the resolution")
    public IncidentDetail verify(@PathVariable Long id) {
        return incidentService.verify(id);
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Reporter rejects the resolution")
    public IncidentDetail reopen(@PathVariable Long id, @Valid @RequestBody RequiredCommentRequest request) {
        return incidentService.reopen(id, request.comment());
    }

    @PostMapping("/{id}/close")
    public IncidentDetail close(@PathVariable Long id, @Valid @RequestBody(required = false) CloseRequest request) {
        return incidentService.close(id, request == null ? null : request.closureCode(), request == null ? null : request.notes());
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Close as a duplicate of another incident")
    public IncidentDetail markDuplicate(@PathVariable Long id, @Valid @RequestBody DuplicateRequest request) {
        return incidentService.markDuplicate(id, request.parentIncidentId());
    }

    @PostMapping("/{id}/feedback")
    public IncidentDetail feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        return incidentService.submitFeedback(id, request.rating(), request.comment());
    }

    private static IncidentSearchCriteria criteria(String q, LocalDate from, LocalDate to, Set<IncidentStatus> status,
                                                   Set<Priority> priority, Long categoryId, Long serviceId,
                                                   Long technicianId, Long reporterId, SlaStatus slaStatus,
                                                   Boolean escalated, Boolean unassigned) {
        return IncidentSearchCriteria.builder()
                .text(q)
                .reportedFrom(from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC))
                .reportedTo(to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
                .statuses(status).priorities(priority).categoryId(categoryId).serviceId(serviceId)
                .technicianId(technicianId).reporterId(reporterId).slaStatus(slaStatus)
                .escalatedOnly(escalated).unassignedOnly(unassigned)
                .build();
    }
}
