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

    public boolean enabled() {
        return StringUtils.hasText(secret) && StringUtils.hasText(siteKey);
    }
    public String getSiteKey() { return siteKey; }
    public void setSiteKey(String siteKey) { this.siteKey = siteKey; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
}
