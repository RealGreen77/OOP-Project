package com.itsm.service;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.exception.InvalidInputException;
import com.itsm.exception.ResourceAlreadyExistsException;
import com.itsm.exception.ResourceNotFoundException;
import com.itsm.exception.ServiceNotFoundException;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.security.CurrentUserProvider;
import com.itsm.web.dto.CatalogDtos.CategoryDto;
import com.itsm.web.dto.CatalogDtos.CategoryRequest;
import com.itsm.web.dto.CatalogDtos.ServiceDto;
import com.itsm.web.dto.CatalogDtos.ServiceRequest;
import com.itsm.web.dto.DtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * IT service catalogue and incident categories (administrator managed).
 * New categories and services are pure data, so they can be introduced at runtime without code changes.
 */
@Service
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ITServiceRepository serviceRepository;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final DtoMapper mapper;

    public CatalogService(CategoryRepository categoryRepository, ITServiceRepository serviceRepository,
                          AuditService auditService, CurrentUserProvider currentUser, DtoMapper mapper) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------- categories

    @Transactional(readOnly = true)
    public List<CategoryDto> categories(boolean includeInactive) {
        var list = includeInactive ? categoryRepository.findAllByOrderByName() : categoryRepository.findByActiveTrueOrderByName();
        return list.stream().map(mapper::category).toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest r) {
        if (categoryRepository.existsByCodeIgnoreCase(r.code())) {
            throw new ResourceAlreadyExistsException("Category", "code", r.code());
        }
        Category c = categoryRepository.save(new Category(r.code(), r.name(), r.description(), r.keywords()));
        audit("Created category " + c.getCode(), c.getId(), c.getCode());
        return mapper.category(c);
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryRequest r) {
        Category c = findCategory(id);
        categoryRepository.findByCodeIgnoreCase(r.code()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ResourceAlreadyExistsException("Category", "code", r.code());
        });
        c.rename(r.code(), r.name(), r.description());
        c.replaceKeywords(r.keywords());
        if (r.active() != null) { if (r.active()) c.activate(); else c.deactivate(); }
        audit("Updated category " + c.getCode(), c.getId(), c.getCode());
        return mapper.category(c);
    }

    public Category findCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    // ---------------------------------------------------------------- services

    @Transactional(readOnly = true)
    public List<ServiceDto> services(boolean includeInactive) {
        var list = includeInactive ? serviceRepository.findAllWithDependencies() : serviceRepository.findActiveWithDependencies();
        return list.stream().map(mapper::service).toList();
    }

    @Transactional
    public ServiceDto createService(ServiceRequest r) {
        if (serviceRepository.existsByCodeIgnoreCase(r.code())) {
            throw new ResourceAlreadyExistsException("IT service", "code", r.code());
        }
        ITService s = new ITService(r.code(), r.name(), r.description(), r.criticality(), r.ownerTeam(),
                r.defaultCategoryId() == null ? null : findCategory(r.defaultCategoryId()));
        serviceRepository.save(s);
        applyDependencies(s, r.dependencyIds());
        audit("Created IT service " + s.getCode() + " (" + s.getCriticality() + ")", s.getId(), s.getCode());
        return mapper.service(s);
    }

    @Transactional
    public ServiceDto updateService(Long id, ServiceRequest r) {
        ITService s = findService(id);
        serviceRepository.findByCodeIgnoreCase(r.code()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ResourceAlreadyExistsException("IT service", "code", r.code());
        });
        s.update(r.code(), r.name(), r.description(), r.criticality(), r.ownerTeam(),
                r.defaultCategoryId() == null ? null : findCategory(r.defaultCategoryId()));
        applyDependencies(s, r.dependencyIds());
        if (r.active() != null) { if (r.active()) s.activate(); else s.deactivate(); }
        audit("Updated IT service " + s.getCode(), s.getId(), s.getCode());
        return mapper.service(s);
    }

    public ITService findService(Long id) {
        return serviceRepository.findById(id).orElseThrow(() -> new ServiceNotFoundException(id));
    }

    public ITService findActiveService(Long id) {
        ITService s = findService(id);
        if (!s.isActive()) throw new ServiceNotFoundException(id + " (inactive)");
        return s;
    }

    private void applyDependencies(ITService service, Set<Long> dependencyIds) {
        if (dependencyIds == null) return;
        service.clearDependencies();
        for (Long depId : dependencyIds) {
            if (depId.equals(service.getId())) throw new InvalidInputException("A service cannot depend on itself");
            service.addDependency(findService(depId));
        }
    }

    private void audit(String description, Long id, String ref) {
        auditService.recordAction(currentUser.currentUser(), AuditAction.CONFIGURATION_CHANGED, AuditService.CONFIG,
                id, ref, null, null, description);
    }
}
