package pl.romcio.driperska.common.error;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated but not allowed to touch this resource (HTTP 403).
 *
 * <p>Spring's own {@code AccessDeniedException} answers with a fixed generic message; this one
 * carries a reason worth showing, e.g. that a moderator may only edit their own submission.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "forbidden", message);
    }
}
