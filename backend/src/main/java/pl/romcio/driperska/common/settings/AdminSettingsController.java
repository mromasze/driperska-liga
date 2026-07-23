package pl.romcio.driperska.common.settings;

import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
public class AdminSettingsController {

    private final AppSettingService settings;

    public AdminSettingsController(AppSettingService settings) {
        this.settings = settings;
    }

    public record SettingsResponse(boolean riotEnabled) {}
    public record UpdateSettingsRequest(@NotNull Boolean riotEnabled) {}

    @GetMapping
    public SettingsResponse get() {
        return new SettingsResponse(settings.isRiotEnabled());
    }

    @PutMapping
    public SettingsResponse update(@RequestBody UpdateSettingsRequest request) {
        settings.setRiotEnabled(Boolean.TRUE.equals(request.riotEnabled()));
        return new SettingsResponse(settings.isRiotEnabled());
    }
}
