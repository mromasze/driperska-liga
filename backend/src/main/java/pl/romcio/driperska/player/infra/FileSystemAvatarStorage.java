package pl.romcio.driperska.player.infra;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.player.application.AvatarStorage;

/** Stores avatars on the local filesystem (Docker volume), served by nginx under {@code /media}. */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class FileSystemAvatarStorage implements AvatarStorage {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int SIZE = 512;

    private final StorageProperties properties;

    public FileSystemAvatarStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(UUID playerId, MultipartFile file) {
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
            String fileName = playerId + ".png";
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
}
