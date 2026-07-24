package pl.romcio.driperska.common;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Identifies a single running process. The {@code bootId} is generated once per JVM start, so it
 * changes on every backend restart/redeploy. It is embedded in every JWT (claim {@code bid}) and
 * exposed via the public config endpoint, which lets us invalidate all sessions on restart and lets
 * the SPA detect a restart after a technical break.
 */
@Component
public class AppInstance {

    private final String bootId = UUID.randomUUID().toString();

    public String bootId() {
        return bootId;
    }
}
