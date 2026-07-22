package pl.romcio.driperska.common.error;

import org.springframework.http.HttpStatus;

/** Base class for domain exceptions carrying an HTTP status and a problem type slug. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String type;

    protected ApiException(HttpStatus status, String type, String message) {
        super(message);
        this.status = status;
        this.type = type;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }
}
