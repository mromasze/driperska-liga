package pl.romcio.driperska.integration.turnstile;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Cloudflare Turnstile settings. When {@code secret} is set, login requires a valid token. */
@Component
@ConfigurationProperties(prefix = "app.turnstile")
public class TurnstileProperties {
    private String siteKey;
    private String secret;
    /**
     * Local/test escape hatch: when set, a login whose token equals this value skips Cloudflare
     * verification. Lets curl-based test scripts log in while Turnstile is enabled. MUST stay empty
     * in production — leaving it unset disables the bypass entirely.
     */
    private String bypassToken;

    public boolean enabled() {
        return StringUtils.hasText(secret) && StringUtils.hasText(siteKey);
    }
    public boolean matchesBypass(String token) {
        return StringUtils.hasText(bypassToken) && bypassToken.equals(token);
    }
    public String getSiteKey() { return siteKey; }
    public void setSiteKey(String siteKey) { this.siteKey = siteKey; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getBypassToken() { return bypassToken; }
    public void setBypassToken(String bypassToken) { this.bypassToken = bypassToken; }
}
