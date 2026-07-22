package pl.romcio.driperska.diagnostics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.diagnostics.DiagnosticsService.ServiceHealth;

@RestController
@RequestMapping("/api/v1/admin/diagnostics")
@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
public class DiagnosticsController {
    private final DiagnosticsService service;

    public DiagnosticsController(DiagnosticsService service) {
        this.service = service;
    }

    @GetMapping("/ollama")
    public ServiceHealth ollama() { return service.checkOllama(); }

    @GetMapping("/discord")
    public ServiceHealth discord() { return service.checkDiscord(); }

    @GetMapping("/riot")
    public ServiceHealth riot() { return service.checkRiot(); }
}
