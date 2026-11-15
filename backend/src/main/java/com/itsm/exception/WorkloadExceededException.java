package com.itsm.exception;

import java.util.Map;

/** Assigning would push a technician above their maximum concurrent workload. */
public class WorkloadExceededException extends ItsmException {

    private final String technicianCode;
    private final int activeIncidents;
    private final int maxIncidents;

    public WorkloadExceededException(String technicianCode, int activeIncidents, int maxIncidents) {
        super("WORKLOAD_EXCEEDED", "Technician " + technicianCode + " already has " + activeIncidents
                + " active incidents (maximum " + maxIncidents + ")");
        this.technicianCode = technicianCode;
        this.activeIncidents = activeIncidents;
        this.maxIncidents = maxIncidents;
    }

    @Override
    public ErrorType type() {
        return ErrorType.BUSINESS_RULE;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("technician", technicianCode, "active", activeIncidents, "max", maxIncidents);
    }
}
