package pl.romcio.driperska.common.error;

import org.springframework.http.HttpStatus;

/** A dependency failed in a way that may succeed when retried later. */
public class ExternalServiceException extends ApiException {
    public ExternalServiceException(String service, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "external-service",
                "%s: %s".formatted(service, message));
    }
}

