package pl.romcio.driperska.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.common.AppInstance;
import pl.romcio.driperska.integration.turnstile.TurnstileProperties;

/** Public, unauthenticated runtime config the SPA needs before login (e.g. Turnstile site key). */
@RestController
@RequestMapping("/api/v1/config")
public class PublicConfigController {

    private final TurnstileProperties turnstile;
    private final AppInstance appInstance;

    public PublicConfigController(TurnstileProperties turnstile, AppInstance appInstance) {
        this.turnstile = turnstile;
        this.appInstance = appInstance;
    }

    /** {@code bootId} changes on every backend restart; the SPA polls it to detect a restart. */
    public record PublicConfig(boolean turnstileEnabled, String turnstileSiteKey, String bootId) {}

    @GetMapping
    public PublicConfig config() {
        return new PublicConfig(turnstile.enabled(),
                turnstile.enabled() ? turnstile.getSiteKey() : null,
                appInstance.bootId());
    }
}
