package pl.romcio.driperska.player.application;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Stores a player's avatar image and returns the public URL to reference it by. */
public interface AvatarStorage {

    String store(UUID playerId, MultipartFile file);
}
