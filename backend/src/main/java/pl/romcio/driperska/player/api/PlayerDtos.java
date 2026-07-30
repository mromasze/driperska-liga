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
            String discordName,
            Role mainRole, Role secondaryRole, String avatarUrl, String bio,
            String opggLink, List<Integer> favoriteChampionIds,
            boolean accountProvisioned,
            /**
             * True when the linked account may record past matches. Only filled in on the admin
             * listing and on {@code /players/me} — the public player list always reports false, so
             * the site does not advertise which accounts are privileged.
             */
            boolean moderator,
            boolean active, Instant joinedAt) {

        public static PlayerResponse of(Player p, boolean moderator) {
            return new PlayerResponse(p.getId(), p.getNickname(), p.getRealName(), p.getRiotId(),
                    p.getDiscordName(), p.getMainRole(), p.getSecondaryRole(), p.getAvatarUrl(), p.getBio(),
                    p.getOpggLink(), List.copyOf(p.getFavoriteChampionIds()),
                    p.getAccountId() != null, moderator, p.isActive(), p.getJoinedAt());
        }

        /** Public view — no permission details. */
        public static PlayerResponse from(Player p) {
            return of(p, false);
        }
    }

    public record CreatePlayerRequest(
            @NotBlank @Size(min = 2, max = 40) String nickname,
            @NotNull Role mainRole, Role secondaryRole,
            String realName, String riotId, String bio,
            @NotBlank @Size(max = 80) String discordName) {}

    public record UpdatePlayerRequest(
            @Size(min = 2, max = 40) String nickname,
            Role mainRole, Role secondaryRole, String realName, String riotId, String bio,
            @Size(max = 500) String opggLink,
            @Size(max = 5) List<Integer> favoriteChampionIds,
            @Size(max = 80) String discordName,
            Boolean active) {}

    public record SelfUpdatePlayerRequest(
            @NotNull Role mainRole, Role secondaryRole,
            @Size(max = 80) String riotId,
            @Size(max = 500) String bio,
            @Size(max = 500) String opggLink,
            @Size(max = 5) List<Integer> favoriteChampionIds) {}

    /** Admin decision on the moderator permission of a player's login account. */
    public record SetModeratorRequest(@NotNull Boolean moderator) {}

    public record LoginCredentials(
            String login, String temporaryPassword, String loginUrl, String messageTemplate) {}
    public record DiscordDelivery(boolean sent, String message) {}
    public record CreatedPlayerResponse(PlayerResponse player, LoginCredentials credentials,
                                        DiscordDelivery discordDelivery) {}
}