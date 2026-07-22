package pl.romcio.driperska.highlight.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ExternalServiceException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.player.infra.StorageProperties;

/** Stores homepage highlight clips in the persistent media volume. */
@Service
public class HighlightService {
    private static final long MAX_BYTES = 400L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("mp4", "webm");
    private static final Pattern STORED_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(mp4|webm)");

    private final StorageProperties properties;

    public HighlightService(StorageProperties properties) {
        this.properties = properties;
    }

    public List<HighlightVideo> list() {
        Path dir = directory();
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> STORED_NAME.matcher(path.getFileName().toString()).matches())
                    .map(this::describe)
                    .sorted(Comparator.comparing(HighlightVideo::uploadedAt).reversed())
                    .toList();
        } catch (IOException ex) {
            throw storageFailure("nie udało się odczytać listy klipów", ex);
        }
    }

    public HighlightVideo store(MultipartFile file) {
        validate(file);
        String extension = extension(file.getOriginalFilename());
        String id = UUID.randomUUID() + "." + extension;
        Path dir = directory();
        Path target = dir.resolve(id);
        try {
            Files.createDirectories(dir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return describe(target);
        } catch (IOException ex) {
            throw storageFailure("nie udało się zapisać klipu", ex);
        }
    }

    public void delete(String id) {
        if (id == null || !STORED_NAME.matcher(id).matches()) {
            throw new BusinessRuleException("Nieprawidłowy identyfikator klipu");
        }
        Path target = directory().resolve(id).normalize();
        if (!target.getParent().equals(directory())) {
            throw new BusinessRuleException("Nieprawidłowy identyfikator klipu");
        }
        try {
            if (!Files.deleteIfExists(target)) {
                throw ResourceNotFoundException.of("Highlight", id);
            }
        } catch (IOException ex) {
            throw storageFailure("nie udało się usunąć klipu", ex);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Plik wideo jest pusty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("Klip jest za duży (maksymalnie 400 MB)");
        }
        String extension = extension(file.getOriginalFilename());
        if (!EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException("Dozwolone formaty klipów: MP4 i WebM");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            boolean valid = extension.equals("mp4") && header.length >= 8
                    && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            valid |= extension.equals("webm") && header.length >= 4
                    && (header[0] & 0xff) == 0x1a && (header[1] & 0xff) == 0x45
                    && (header[2] & 0xff) == 0xdf && (header[3] & 0xff) == 0xa3;
            if (!valid) {
                throw new BusinessRuleException("Plik nie zawiera prawidłowego wideo " + extension.toUpperCase(Locale.ROOT));
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("Nie udało się odczytać przesłanego klipu");
        }
    }

    private HighlightVideo describe(Path path) {
        try {
            String id = path.getFileName().toString();
            return new HighlightVideo(id, properties.publicBaseUrl() + "/highlights/" + id,
                    Files.size(path), Files.getLastModifiedTime(path).toInstant());
        } catch (IOException ex) {
            throw storageFailure("nie udało się odczytać informacji o klipie", ex);
        }
    }

    private Path directory() {
        return Path.of(properties.mediaDir(), "highlights").toAbsolutePath().normalize();
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static ExternalServiceException storageFailure(String message, IOException ex) {
        return new ExternalServiceException("Magazyn multimediów", message + ": " + ex.getMessage());
    }

    public record HighlightVideo(String id, String url, long sizeBytes, Instant uploadedAt) {}
}
