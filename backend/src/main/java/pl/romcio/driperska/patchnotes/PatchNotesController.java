package pl.romcio.driperska.patchnotes;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.integration.discord.DiscordClient.Delivery;

@RestController
@RequestMapping("/api/v1/admin/patch-notes")
@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
public class PatchNotesController {

    private final PatchNotesService service;

    public PatchNotesController(PatchNotesService service) {
        this.service = service;
    }

    public record AnnounceRequest(@NotBlank String version, @NotBlank String title,
                                  String date, List<String> changes) {}

    public record AnnounceResponse(boolean sent, String message) {}

    @PostMapping("/announce")
    public AnnounceResponse announce(@RequestBody AnnounceRequest req) {
        Delivery delivery = service.announce(req.version(), req.title(), req.date(), req.changes());
        return new AnnounceResponse(delivery.sent(), delivery.message());
    }
}
