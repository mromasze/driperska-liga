package pl.romcio.driperska.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.integration.turnstile.TurnstileProperties;

/** Public, unauthenticated runtime config the SPA needs before login (e.g. Turnstile site key). */
@RestController
@RequestMapping("/api/v1/config")
public class PublicConfigController {

    private final TurnstileProperties turnstile;

    public PublicConfigController(TurnstileProperties turnstile) {
        this.turnstile = turnstile;
    }

    public record PublicConfig(boolean turnstileEnabled, String turnstileSiteKey) {}

    @GetMapping
    public PublicConfig config() {
        return new PublicConfig(turnstile.enabled(),
                turnstile.enabled() ? turnstile.getSiteKey() : null);
    }
}
