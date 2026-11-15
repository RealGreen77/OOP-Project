package com.itsm.engine.sla;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.exception.SlaConfigurationMissingException;
import com.itsm.repository.SlaPolicyRepository;
import org.springframework.stereotype.Component;

/**
 * Identifies the applicable SLA: a service-specific policy wins over the global default for the priority.
 * // [MVP: SLA Management] "Identify the applicable SLA."
 */
@Component
public class SlaPolicyResolver {

    private final SlaPolicyRepository repository;

    public SlaPolicyResolver(SlaPolicyRepository repository) {
        this.repository = repository;
    }

    public SlaPolicy resolve(ITService service, Priority priority) {
        if (service != null && service.getId() != null) {
            var specific = repository.findFirstByServiceIdAndPriorityAndActiveTrue(service.getId(), priority);
            if (specific.isPresent()) return specific.get();
        }
        return repository.findFirstByServiceIsNullAndPriorityAndActiveTrue(priority)
                .orElseThrow(() -> new SlaConfigurationMissingException(priority,
                        service == null ? "n/a" : service.getName()));
    }
}
