package pl.romcio.driperska.match.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.player.infra.StorageProperties;

/** Stores a match's League replay (.rofl) on the media volume, served under {@code /media/replays}. */
@Service
public class MatchReplayService {
    private static final long MAX_BYTES = 120L * 1024 * 1024; // LoL replays are tens of MB

    private final MatchService matchService;
    private final StorageProperties properties;
    private final MatchEventRecorder eventRecorder;

    public MatchReplayService(MatchService matchService, StorageProperties properties,
                              MatchEventRecorder eventRecorder) {
        this.matchService = matchService;
        this.properties = properties;
        this.eventRecorder = eventRecorder;
    }

    @Transactional
    public Match store(UUID matchId, MultipartFile file, UUID actor) {
        Match match = matchService.get(matchId);
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Plik powtórki jest pusty");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".rofl")) {
            throw new BusinessRuleException("Powtórka musi być plikiem .rofl z klienta League of Legends");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("Powtórka jest za duża (max 120 MB)");
        }
        try {
            Path dir = Path.of(properties.mediaDir(), "replays");
            Files.createDirectories(dir);
            Path target = dir.resolve(matchId + ".rofl");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            match.setReplayUrl(properties.publicBaseUrl() + "/replays/" + matchId + ".rofl");
        } catch (IOException ex) {
            throw new BusinessRuleException("Nie udało się zapisać powtórki: " + ex.getMessage());
        }
        eventRecorder.record(matchId, MatchEventType.REPLAY_UPLOADED, actor, null);
        return match;
    }
}
