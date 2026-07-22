package pl.romcio.driperska.champion.api;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.champion.application.ChampionSyncService;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.error.ResourceNotFoundException;

@RestController
@RequestMapping("/api/v1/champions")
public class ChampionController {

    private final ChampionRepository repository;
    private final ChampionSyncService syncService;

    public ChampionController(ChampionRepository repository, ChampionSyncService syncService) {
        this.repository = repository;
        this.syncService = syncService;
    }

    @GetMapping
    public List<ChampionResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(ChampionResponse::from).toList();
    }

    @GetMapping("/{key}")
    public ChampionResponse get(@PathVariable Integer key) {
        return repository.findById(key)
                .map(ChampionResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Champion", key));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncResult sync(@RequestBody(required = false) SyncRequest req) {
        int count = syncService.sync(req == null ? null : req.version());
        return new SyncResult(count);
    }

    public record SyncRequest(String version) {
    }

    public record SyncResult(int synced) {
    }

    public record ChampionResponse(
            Integer id,
            String slug,
            String name,
            String title,
            List<String> tags,
            String iconUrl,
            String splashUrl,
            String loadingUrl) {

        static ChampionResponse from(Champion c) {
            List<String> tags = (c.getTags() == null || c.getTags().isBlank())
                    ? List.of()
                    : List.of(c.getTags().split(","));
            return new ChampionResponse(c.getId(), c.getSlug(), c.getName(), c.getTitle(),
                    tags, c.getIconUrl(), c.getSplashUrl(), c.getLoadingUrl());
        }
    }
}
