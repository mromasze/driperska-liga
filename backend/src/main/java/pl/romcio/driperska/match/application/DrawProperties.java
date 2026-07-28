package pl.romcio.driperska.match.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Squad-draw timing, editable at runtime from the admin panel. */
@Component
@ConfigurationProperties(prefix = "app.draw")
public class DrawProperties {

    /** Auto-confirm a drawn squad this many seconds after the draw if voting hasn't resolved. 0 = off. */
    private int autoConfirmSeconds = 60;

    public int getAutoConfirmSeconds() { return autoConfirmSeconds; }

    public void setAutoConfirmSeconds(int autoConfirmSeconds) {
        this.autoConfirmSeconds = Math.max(0, autoConfirmSeconds);
    }
}
