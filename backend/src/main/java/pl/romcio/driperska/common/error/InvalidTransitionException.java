package pl.romcio.driperska.common.error;

import org.springframework.http.HttpStatus;

/** Illegal match lifecycle transition or a conflicting state change (HTTP 409). */
public class InvalidTransitionException extends ApiException {

    public InvalidTransitionException(String message) {
        super(HttpStatus.CONFLICT, "invalid-transition", message);
    }
}
