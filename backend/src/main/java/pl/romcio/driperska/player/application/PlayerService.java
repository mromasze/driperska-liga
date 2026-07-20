package pl.romcio.driperska.player.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.player.api.PlayerDtos.CreatePlayerRequest;
import pl.romcio.driperska.player.api.PlayerDtos.UpdatePlayerRequest;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class PlayerService {

    private final PlayerRepository repository;
    private final AvatarStorage avatarStorage;

    public PlayerService(PlayerRepository repository, AvatarStorage avatarStorage) {
        this.repository = repository;
        this.avatarStorage = avatarStorage;
    }

    @Transactional(readOnly = true)
    public Page<Player> list(Boolean active, Role role, String search, Pageable pageable) {
        if (StringUtils.hasText(search)) {
            return repository.findByNicknameContainingIgnoreCase(search, pageable);
        }
        if (role != null) {
            return repository.findByActiveTrueAndMainRole(role, pageable);
        }
        if (Boolean.TRUE.equals(active)) {
            return repository.findByActiveTrue(pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Player get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Player", id));
    }

    @Transactional(readOnly = true)
    public List<Player> getAll(List<UUID> ids) {
        return repository.findByIdIn(ids);
    }

    @Transactional
    public Player create(CreatePlayerRequest req) {
        if (repository.existsByNicknameIgnoreCase(req.nickname())) {
            throw new BusinessRuleException("Gracz o takim nicku już istnieje");
        }
        Player player = new Player(req.nickname(), req.mainRole());
        player.setSecondaryRole(req.secondaryRole());
        player.setRealName(req.realName());
        player.setRiotId(req.riotId());
        player.setBio(req.bio());
        return repository.save(player);
    }

    @Transactional
    public Player update(UUID id, UpdatePlayerRequest req) {
        Player player = get(id);
        if (StringUtils.hasText(req.nickname())) {
            player.setNickname(req.nickname());
        }
        if (req.mainRole() != null) {
            player.setMainRole(req.mainRole());
        }
        if (req.secondaryRole() != null) {
            player.setSecondaryRole(req.secondaryRole());
        }
        if (req.realName() != null) {
            player.setRealName(req.realName());
        }
        if (req.riotId() != null) {
            player.setRiotId(req.riotId());
        }
        if (req.bio() != null) {
            player.setBio(req.bio());
        }
        if (req.active() != null) {
            player.setActive(req.active());
        }
        return player;
    }

    @Transactional
    public Player updateAvatar(UUID id, MultipartFile file) {
        Player player = get(id);
        String url = avatarStorage.store(player.getId(), file);
        player.setAvatarUrl(url);
        return player;
    }

    @Transactional
    public void softDelete(UUID id) {
        get(id).setActive(false);
    }
}
