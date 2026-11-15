package com.itsm.exception;

/** An external system (SMTP server, SMS gateway, ...) failed or was unreachable (network failure). */
public class ExternalServiceException extends ItsmException {

    private final String serviceName;

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super("EXTERNAL_SERVICE_FAILURE", serviceName + " failure: " + message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public ErrorType type() {
        return ErrorType.EXTERNAL_SERVICE;
    }
}
