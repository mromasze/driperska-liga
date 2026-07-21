package pl.romcio.driperska.player.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.player.domain.Player;

public final class PlayerDtos {
    private PlayerDtos() {}

    public record PlayerResponse(
            UUID id, String nickname, String realName, String riotId,
            Role mainRole, Role secondaryRole, String avatarUrl, String bio,
            String opggLink, List<Integer> favoriteChampionIds,
            boolean accountProvisioned, boolean active, Instant joinedAt) {
        public static PlayerResponse from(Player p) {
            return new PlayerResponse(p.getId(), p.getNickname(), p.getRealName(), p.getRiotId(),
                    p.getMainRole(), p.getSecondaryRole(), p.getAvatarUrl(), p.getBio(),
                    p.getOpggLink(), List.copyOf(p.getFavoriteChampionIds()),
                    p.getAccountId() != null, p.isActive(), p.getJoinedAt());
        }
    }

    public record CreatePlayerRequest(
            @NotBlank @Size(min = 2, max = 40) String nickname,
            @NotNull Role mainRole, Role secondaryRole,
            String realName, String riotId, String bio) {}

    public record UpdatePlayerRequest(
            @Size(min = 2, max = 40) String nickname,
            Role mainRole, Role secondaryRole, String realName, String riotId, String bio,
            @Size(max = 500) String opggLink,
            @Size(max = 5) List<Integer> favoriteChampionIds,
            Boolean active) {}

    public record SelfUpdatePlayerRequest(
            @NotNull Role mainRole, Role secondaryRole,
            @Size(max = 80) String riotId,
            @Size(max = 500) String bio,
            @Size(max = 500) String opggLink,
            @Size(max = 5) List<Integer> favoriteChampionIds) {}

    public record LoginCredentials(
            String login, String temporaryPassword, String loginUrl, String messageTemplate) {}
    public record CreatedPlayerResponse(PlayerResponse player, LoginCredentials credentials) {}
}