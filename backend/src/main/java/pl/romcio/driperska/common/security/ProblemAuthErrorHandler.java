package pl.romcio.driperska.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Turns Spring Security's own rejections into the same {@code application/problem+json} bodies the
 * rest of the API returns — and, crucially, into the right status codes.
 *
 * <p>Without an explicit entry point Spring falls back to {@code Http403ForbiddenEntryPoint}: a
 * request carrying no token (or an expired one) is anonymous, which the authorization filter reports
 * as an *authorization* failure, so the client saw 403 "Brak uprawnień do tej operacji" instead of
 * 401. The web client only refreshes its token — and only bounces to the login screen — on a 401, so
 * an expired session left every action failing with a permissions error until the user logged in by
 * hand. The two cases are now told apart:
 *
 * <ul>
 *   <li><b>401</b> — nobody is authenticated: no token, malformed token, expired token, or a token
 *       from a previous backend boot. Recoverable, and the client retries after refreshing.</li>
 *   <li><b>403</b> — authenticated, but this account may not do this. Not recoverable by
 *       re-authenticating, so the client must not try.</li>
 * </ul>
 */
@Component
public class ProblemAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String BASE = "https://driperska.pl/problems/";

    private final ObjectMapper objectMapper;

    public ProblemAuthErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No usable authentication → 401, so the client refreshes or shows the login screen. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "session-expired",
                "Sesja wygasła lub nie jesteś zalogowany — zaloguj się ponownie");
    }

    /** Authenticated but not allowed → 403. Logging in again would not help. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "access-denied",
                "Brak uprawnień do tej operacji");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String type, String detail) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(BASE + type));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
