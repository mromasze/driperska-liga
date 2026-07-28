package pl.romcio.driperska.common.settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.common.settings.RuntimeConfigService.GroupView;

/**
 * Live view of (and editor for) the {@code .env} configuration.
 *
 * <p>Admin-only: even masked, this endpoint enumerates every integration the deployment has, and the
 * write side accepts API keys.
 */
@RestController
@RequestMapping("/api/v1/admin/config")
@PreAuthorize("hasRole('ADMIN')")
public class RuntimeConfigController {

    private final RuntimeConfigService config;

    public RuntimeConfigController(RuntimeConfigService config) {
        this.config = config;
    }

    public record ConfigResponse(List<GroupView> groups) {}

    /**
     * @param values key → new value. Omit a key to leave it alone; send {@code null} to drop the
     *               override and go back to what {@code .env} shipped.
     */
    public record UpdateConfigRequest(Map<String, String> values) {}

    public record ResetConfigRequest(List<String> keys) {}

    @GetMapping
    public ConfigResponse get() {
        return new ConfigResponse(config.describe());
    }

    @PutMapping
    public ConfigResponse update(@RequestBody UpdateConfigRequest request) {
        Map<String, String> values = request.values() == null ? Map.of() : new HashMap<>(request.values());
        return new ConfigResponse(config.update(values));
    }

    @PostMapping("/reset")
    public ConfigResponse reset(@RequestBody ResetConfigRequest request) {
        List<String> keys = request.keys() == null ? List.of() : request.keys();
        return new ConfigResponse(config.reset(keys));
    }
}
