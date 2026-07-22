package pl.romcio.driperska.common.error;

import org.springframework.http.HttpStatus;

/** Semantic validation failure (HTTP 422) — the request is well-formed but violates a domain rule. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "business-rule", message);
    }
}
