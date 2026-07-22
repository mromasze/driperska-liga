package pl.romcio.driperska.common.error;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps exceptions to RFC 7807 {@code application/problem+json} responses with a consistent shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE = "https://driperska.liga/errors/";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("Request failed because a dependency is unavailable: {}", ex.getMessage(), ex);
        }
        return problem(ex.getStatus(), ex.getType(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, "validation",
                "Walidacja nie powiodła się", null);
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "bad-credentials", "Nieprawidłowy login lub hasło", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Brak uprawnień do tej operacji", null);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal",
                "Wystąpił nieoczekiwany błąd", null);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail, String title) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(BASE + type));
        pd.setTitle(title != null ? title : status.getReasonPhrase());
        return pd;
    }

    private static FieldViolation toViolation(FieldError fe) {
        return new FieldViolation(fe.getField(), fe.getDefaultMessage());
    }

    public record FieldViolation(String field, String message) {}
}
