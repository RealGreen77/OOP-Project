package com.itsm.web.controller;

import com.itsm.service.CatalogService;
import com.itsm.service.UserService;
import com.itsm.web.dto.CatalogDtos.CategoryDto;
import com.itsm.web.dto.CatalogDtos.ServiceDto;
import com.itsm.web.dto.UserDtos.AvailabilityRequest;
import com.itsm.web.dto.UserDtos.TechnicianWorkload;
import com.itsm.web.dto.UserDtos.UserDetail;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only catalogue for all authenticated users, plus technician directory/self-service. */
@RestController
@RequestMapping("/api")
@Tag(name = "Catalog & technicians")
public class CatalogController {

    private final CatalogService catalogService;
    private final UserService userService;

    public CatalogController(CatalogService catalogService, UserService userService) {
        this.catalogService = catalogService;
        this.userService = userService;
    }

    @GetMapping("/catalog/services")
    public List<ServiceDto> services() {
        return catalogService.services(false);
    }

    @GetMapping("/catalog/categories")
    public List<CategoryDto> categories() {
        return catalogService.categories(false);
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAuthority('VIEW_ALL_INCIDENTS')")
    public List<TechnicianWorkload> technicians() {
        return userService.technicianWorkloads();
    }

    @PatchMapping("/technicians/me/availability")
    @PreAuthorize("hasAuthority('WORK_INCIDENT')")
    public UserDetail availability(@Valid @RequestBody AvailabilityRequest request) {
        return userService.changeMyAvailability(request.availability());
    }
}
