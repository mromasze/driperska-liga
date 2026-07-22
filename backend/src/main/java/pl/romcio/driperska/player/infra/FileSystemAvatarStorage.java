package pl.romcio.driperska.player.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.player.application.AvatarStorage;

/** Stores avatars on the local filesystem (Docker volume), served by nginx under {@code /media}. */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class FileSystemAvatarStorage implements AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(FileSystemAvatarStorage.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int SIZE = 512;

    private final StorageProperties properties;

    public FileSystemAvatarStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(UUID playerId, MultipartFile file, String previousUrl) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Plik jest pusty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("Plik jest za duży (max 5 MB)");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessRuleException("Dozwolone formaty: PNG, JPEG, WEBP");
        }
        try {
            Path dir = Path.of(properties.mediaDir(), "avatars");
            Files.createDirectories(dir);
            // Remove any previous image for this player so stale files never linger on disk.
            deletePlayerAvatars(dir, playerId);
            // Unique file name per upload → the public URL changes, so browsers fetch the new image
            // instead of serving a cached copy under an unchanged URL.
            String fileName = playerId + "-" + Long.toString(System.currentTimeMillis(), 36) + ".png";
            Path target = dir.resolve(fileName);
            try (InputStream in = file.getInputStream()) {
                Thumbnails.of(in)
                        .size(SIZE, SIZE)
                        .outputFormat("png")
                        .toFile(target.toFile());
            }
            return properties.publicBaseUrl() + "/avatars/" + fileName;
        } catch (IOException ex) {
            throw new BusinessRuleException("Nie udało się zapisać obrazu: " + ex.getMessage());
        }
    }

    /** Deletes every stored avatar file belonging to the player (old {@code <id>.png} and versioned names). */
    private void deletePlayerAvatars(Path dir, UUID playerId) {
        String prefix = playerId.toString();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.equals(prefix + ".png") || name.startsWith(prefix + "-");
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
        } catch (IOException | UncheckedIOException ex) {
            // Best-effort cleanup: a leftover old file must not block uploading a new avatar.
            log.warn("Nie udało się usunąć starego avatara gracza {}: {}", playerId, ex.getMessage());
        }
    }
}
