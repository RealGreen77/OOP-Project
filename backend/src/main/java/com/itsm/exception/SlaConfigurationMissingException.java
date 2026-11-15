package com.itsm.exception;

import com.itsm.domain.incident.Priority;

/** Neither a service-specific nor a global SLA policy exists for the given priority. */
public class SlaConfigurationMissingException extends ItsmException {

    public SlaConfigurationMissingException(Priority priority, String serviceName) {
        super("SLA_CONFIGURATION_MISSING",
                "No SLA policy configured for priority " + priority + " (service: " + serviceName
                        + ") and no global default exists. An administrator must configure one.");
    }

    @Override
    public ErrorType type() {
        return ErrorType.BUSINESS_RULE;
    }
}
