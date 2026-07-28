package pl.romcio.driperska.match.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Draft timing. Read per step rather than captured at startup, so an admin can shorten or lengthen
 * the ban/pick clock from the panel between matches.
 */
@Component
@ConfigurationProperties(prefix = "app.draft")
public class DraftProperties {

    /** Seconds each ban/pick step waits before a champion is auto-assigned. */
    private int stepSeconds = 30;

    public int getStepSeconds() { return stepSeconds; }

    public void setStepSeconds(int stepSeconds) {
        this.stepSeconds = stepSeconds > 0 ? stepSeconds : 30;
    }
}
