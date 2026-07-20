package pl.romcio.driperska.player.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.player.domain.Player;

public final class PlayerDtos {

    private PlayerDtos() {
    }

    public record PlayerResponse(
            UUID id,
            String nickname,
            String realName,
            String riotId,
            Role mainRole,
            Role secondaryRole,
            String avatarUrl,
            String bio,
            boolean active,
            Instant joinedAt) {

        public static PlayerResponse from(Player p) {
            return new PlayerResponse(p.getId(), p.getNickname(), p.getRealName(), p.getRiotId(),
                    p.getMainRole(), p.getSecondaryRole(), p.getAvatarUrl(), p.getBio(),
                    p.isActive(), p.getJoinedAt());
        }
    }

    public record CreatePlayerRequest(
            @NotBlank @Size(min = 2, max = 40) String nickname,
            @NotNull Role mainRole,
            Role secondaryRole,
            String realName,
            String riotId,
            String bio) {
    }

    public record UpdatePlayerRequest(
            @Size(min = 2, max = 40) String nickname,
            Role mainRole,
            Role secondaryRole,
            String realName,
            String riotId,
            String bio,
            Boolean active) {
    }
}
