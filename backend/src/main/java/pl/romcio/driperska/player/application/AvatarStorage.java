package pl.romcio.driperska.player.application;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Stores a player's avatar image and returns the public URL to reference it by. */
public interface AvatarStorage {

    /**
     * Stores a new avatar, removing the player's previous image first, and returns a fresh
     * public URL. The URL changes on every upload so browsers never serve a cached old image.
     *
     * @param playerId    owner of the avatar
     * @param file        uploaded image
     * @param previousUrl the currently stored avatar URL (may be {@code null})
     */
    String store(UUID playerId, MultipartFile file, String previousUrl);
}
