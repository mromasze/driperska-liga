package pl.romcio.driperska.player.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.account.application.AccountService;
import pl.romcio.driperska.account.application.AccountService.ProvisionedAccount;
import pl.romcio.driperska.common.config.AppCoreProperties;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.discord.DiscordClient;
import pl.romcio.driperska.integration.discord.DiscordClient.Delivery;
import pl.romcio.driperska.player.api.PlayerDtos.*;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class PlayerService {
    private final PlayerRepository repository;
    private final AvatarStorage avatarStorage;
    private final AccountService accountService;
    private final DiscordClient discordClient;
    private final AppCoreProperties app;

    public PlayerService(PlayerRepository repository, AvatarStorage avatarStorage,
                         AccountService accountService, DiscordClient discordClient,
                         AppCoreProperties app) {
        this.repository = repository;
        this.avatarStorage = avatarStorage;
        this.accountService = accountService;
        this.discordClient = discordClient;
        this.app = app;
    }

    @Transactional(readOnly = true)
    public Page<Player> list(Boolean active, Role role, String search, Pageable pageable) {
        if (StringUtils.hasText(search)) return repository.findByNicknameContainingIgnoreCase(search, pageable);
        if (role != null) return repository.findByActiveTrueAndMainRole(role, pageable);
        if (Boolean.TRUE.equals(active)) return repository.findByActiveTrue(pageable);
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Player get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Player", id));
    }

    @Transactional(readOnly = true)
    public Player getByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Konto nie jest połączone z graczem"));
    }

    @Transactional(readOnly = true)
    public List<Player> getAll(List<UUID> ids) { return repository.findByIdIn(ids); }

    /** One player with the moderator permission of their linked account resolved. */
    @Transactional(readOnly = true)
    public PlayerResponse withModeratorFlag(Player player) {
        return PlayerResponse.of(player, player.getAccountId() != null
                && accountService.isModerator(player.getAccountId()));
    }

    /** Same for a page of players, resolving all accounts in a single query. */
    @Transactional(readOnly = true)
    public Page<PlayerResponse> withModeratorFlags(Page<Player> page) {
        Set<UUID> moderators = accountService.moderatorsAmong(page.getContent().stream()
                .map(Player::getAccountId)
                .filter(java.util.Objects::nonNull)
                .toList());
        return page.map(player -> PlayerResponse.of(player,
                player.getAccountId() != null && moderators.contains(player.getAccountId())));
    }

    /**
     * Grants or revokes the moderator permission of the player's login account. Requires an account —
     * the permission lives there, not on the player row.
     */
    @Transactional
    public PlayerResponse setModerator(UUID playerId, boolean moderator) {
        Player player = get(playerId);
        if (player.getAccountId() == null) {
            throw new BusinessRuleException(
                    "Najpierw utwórz konto logowania dla tego gracza — moderatorem jest konto, nie profil");
        }
        accountService.setModerator(player.getAccountId(), moderator);
        return PlayerResponse.of(player, moderator);
    }

    @Transactional
    public Player create(CreatePlayerRequest req) {
        if (repository.existsByNicknameIgnoreCase(req.nickname())) {
            throw new BusinessRuleException("Gracz o takim nicku już istnieje");
        }
        Player player = new Player(req.nickname(), req.mainRole(), req.discordName().trim());
        player.setSecondaryRole(req.secondaryRole());
        player.setRealName(req.realName());
        player.setRiotId(req.riotId());
        player.setBio(req.bio());
        return repository.save(player);
    }

    @Transactional
    public CreatedPlayerResponse createWithAccount(CreatePlayerRequest req) {
        return provision(create(req));
    }

    @Transactional
    public CreatedPlayerResponse provisionExisting(UUID playerId) {
        Player player = get(playerId);
        if (player.getAccountId() != null) {
            throw new BusinessRuleException("Ten gracz ma już konto logowania");
        }
        return provision(player);
    }

    private CreatedPlayerResponse provision(Player player) {
        ProvisionedAccount provisioned = accountService.provisionPlayer(player.getNickname());
        player.setAccountId(provisioned.account().getId());
        return deliver(player, provisioned);
    }

    @Transactional
    public CreatedPlayerResponse resendCredentials(UUID playerId) {
        Player player = get(playerId);
        if (player.getAccountId() == null) {
            throw new BusinessRuleException("Ten gracz nie ma konta logowania");
        }
        return deliver(player, accountService.resetTemporaryPassword(player.getAccountId()));
    }

    private CreatedPlayerResponse deliver(Player player, ProvisionedAccount provisioned) {
        String loginUrl = app.publicUrl() + "/login";
        String template = "Siema! Twoje konto Driperskiej Ligi jest gotowe 🎮\n\n"
                + "Strona: " + loginUrl + "\n"
                + "Login: " + player.getNickname() + "\n"
                + "Hasło: " + provisioned.temporaryPassword() + "\n\n"
                + "Po zalogowaniu możesz uzupełnić profil i głosować podczas losowania drużyn.";
        LoginCredentials credentials = new LoginCredentials(
                player.getNickname(), provisioned.temporaryPassword(), loginUrl, template);
        Delivery delivery = discordClient.sendLoginMessage(
                player.getDiscordName(), player.getDiscordUserId(), template);
        if (delivery.sent() && delivery.discordUserId() != null) {
            player.setDiscordUserId(delivery.discordUserId());
        }
        return new CreatedPlayerResponse(PlayerResponse.from(player), credentials,
                new DiscordDelivery(delivery.sent(), delivery.message()));
    }

    @Transactional
    public Player update(UUID id, UpdatePlayerRequest req) {
        Player player = get(id);
        if (StringUtils.hasText(req.nickname())) player.setNickname(req.nickname());
        if (req.mainRole() != null) player.setMainRole(req.mainRole());
        if (req.secondaryRole() != null) player.setSecondaryRole(req.secondaryRole());
        if (req.realName() != null) player.setRealName(req.realName());
        if (req.riotId() != null) player.setRiotId(req.riotId());
        if (req.bio() != null) player.setBio(req.bio());
        if (req.opggLink() != null) player.setOpggLink(req.opggLink());
        if (StringUtils.hasText(req.discordName())
                && !req.discordName().trim().equalsIgnoreCase(player.getDiscordName())) {
            player.setDiscordName(req.discordName().trim());
            player.setDiscordUserId(null);
        }
        if (req.favoriteChampionIds() != null) player.setFavoriteChampionIds(req.favoriteChampionIds());
        if (req.active() != null) player.setActive(req.active());
        return player;
    }

    @Transactional
    public Player updateSelf(UUID accountId, SelfUpdatePlayerRequest req) {
        Player player = getByAccountId(accountId);
        player.setMainRole(req.mainRole());
        player.setSecondaryRole(req.secondaryRole());
        player.setRiotId(req.riotId());
        player.setBio(req.bio());
        player.setOpggLink(req.opggLink());
        player.setFavoriteChampionIds(req.favoriteChampionIds());
        return player;
    }

    @Transactional
    public Player updateAvatar(UUID id, MultipartFile file) {
        Player player = get(id);
        player.setAvatarUrl(avatarStorage.store(player.getId(), file, player.getAvatarUrl()));
        return player;
    }

    @Transactional
    public Player updateSelfAvatar(UUID accountId, MultipartFile file) {
        Player player = getByAccountId(accountId);
        player.setAvatarUrl(avatarStorage.store(player.getId(), file, player.getAvatarUrl()));
        return player;
    }

    @Transactional
    public void softDelete(UUID id) { get(id).setActive(false); }
}