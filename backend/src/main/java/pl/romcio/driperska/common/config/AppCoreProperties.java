package pl.romcio.driperska.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Top-level {@code app.*} values that are read on every use (not just at startup), so the admin
 * panel can change them without a restart. Keep this bean free of anything consumed once during
 * bootstrap — those belong in their own startup-only properties.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppCoreProperties {

    /** Public origin used in Discord messages and login links, e.g. https://driperska.pl */
    private String publicUrl = "https://driperska.pl";

    public String getPublicUrl() { return publicUrl; }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim();
    }

    /** Public URL without a trailing slash — what callers building links actually want. */
    public String publicUrl() {
        return publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }
}
