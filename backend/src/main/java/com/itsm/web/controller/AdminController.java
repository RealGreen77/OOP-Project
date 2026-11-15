package com.itsm.web.controller;

import com.itsm.domain.audit.AuditAction;
import com.itsm.service.AuditService;
import com.itsm.service.CatalogService;
import com.itsm.service.NotificationService;
import com.itsm.service.SettingsService;
import com.itsm.service.SlaMonitoringService;
import com.itsm.service.SlaPolicyService;
import com.itsm.service.UserService;
import com.itsm.web.dto.CatalogDtos.CategoryDto;
import com.itsm.web.dto.CatalogDtos.CategoryRequest;
import com.itsm.web.dto.CatalogDtos.ServiceDto;
import com.itsm.web.dto.CatalogDtos.ServiceRequest;
import com.itsm.web.dto.CatalogDtos.SlaPolicyDto;
import com.itsm.web.dto.CatalogDtos.SlaPolicyRequest;
import com.itsm.web.dto.CatalogDtos.StrategySettings;
import com.itsm.web.dto.CatalogDtos.StrategySettingsRequest;
import com.itsm.web.dto.CommonDtos.PageResponse;
import com.itsm.web.dto.CommonDtos.UserRef;
import com.itsm.web.dto.DtoMapper;
import com.itsm.web.dto.IncidentDtos.AuditEntryDto;
import com.itsm.web.dto.IncidentDtos.NotificationDto;
import com.itsm.web.dto.UserDtos.CreateUserRequest;
import com.itsm.web.dto.UserDtos.UpdateUserRequest;
import com.itsm.web.dto.UserDtos.UserDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Administrator / IT manager configuration and oversight (URL-secured to ADMIN / IT_MANAGER roles). */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administration", description = "Users, technicians, services, categories, SLA policies, strategies, audit")
public class AdminController {

    private final UserService userService;
    private final CatalogService catalogService;
    private final SlaPolicyService slaPolicyService;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SlaMonitoringService slaMonitoringService;
    private final DtoMapper mapper;

    public AdminController(UserService userService, CatalogService catalogService, SlaPolicyService slaPolicyService,
                           SettingsService settingsService, AuditService auditService,
                           NotificationService notificationService, SlaMonitoringService slaMonitoringService,
                           DtoMapper mapper) {
        this.userService = userService;
        this.catalogService = catalogService;
        this.slaPolicyService = slaPolicyService;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.slaMonitoringService = slaMonitoringService;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- users & technicians

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public List<UserDetail> users(@RequestParam(required = false) String q) {
        return userService.list(q);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserDetail user(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    @Operation(summary = "Create any type of user (employee, technician, team lead, admin, IT manager)")
    public UserDetail createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserDetail updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/users/{id}/active")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public UserDetail setActive(@PathVariable Long id, @RequestParam boolean value) {
        return userService.setActive(id, value);
    }

    @GetMapping("/team-leads")
    public List<UserRef> teamLeads() {
        return userService.teamLeads();
    }

    // ---------------------------------------------------------------- catalogue

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return catalogService.categories(true);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public CategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return catalogService.services(true);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public ServiceDto createService(@Valid @RequestBody ServiceRequest request) {
        return catalogService.createService(request);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public ServiceDto updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return catalogService.updateService(id, request);
    }

    // ---------------------------------------------------------------- SLA & strategies

    @GetMapping("/sla-policies")
    public List<SlaPolicyDto> slaPolicies() {
        return slaPolicyService.list();
    }

    @PostMapping("/sla-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MANAGE_SLA')")
    public SlaPolicyDto createSlaPolicy(@Valid @RequestBody SlaPolicyRequest request) {
        return slaPolicyService.create(request);
    }

    @PutMapping("/sla-policies/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SLA')")
    public SlaPolicyDto updateSlaPolicy(@PathVariable Long id, @Valid @RequestBody SlaPolicyRequest request) {
        return slaPolicyService.update(id, request);
    }

    @PostMapping("/sla/scan")
    @PreAuthorize("hasAuthority('MANAGE_SLA')")
    @Operation(summary = "Run the SLA monitor immediately (it also runs automatically in the background)")
    public SlaMonitoringService.ScanResult runSlaScan() {
        return slaMonitoringService.scan();
    }

    @GetMapping("/settings/strategies")
    public StrategySettings strategies() {
        return settingsService.current();
    }

    @PutMapping("/settings/strategies")
    @PreAuthorize("hasAuthority('CONFIGURE_STRATEGY')")
    @Operation(summary = "Switch the active priority / assignment strategy at runtime")
    public StrategySettings updateStrategies(@Valid @RequestBody StrategySettingsRequest request) {
        return settingsService.update(request);
    }

    // ---------------------------------------------------------------- oversight

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('VIEW_AUDIT')")
    public PageResponse<AuditEntryDto> audit(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = auditService.search(entityType, action, actorId, q,
                from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
        return PageResponse.of(result, mapper::audit);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Delivery log of all notifications across channels")
    public PageResponse<NotificationDto> notificationLog(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return notificationService.deliveryLog(page, size);
    }
}
