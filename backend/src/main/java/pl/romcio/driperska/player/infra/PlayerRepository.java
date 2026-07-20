package pl.romcio.driperska.player.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.player.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    boolean existsByNicknameIgnoreCase(String nickname);

    Page<Player> findByActiveTrue(Pageable pageable);

    Page<Player> findByActiveTrueAndMainRole(Role role, Pageable pageable);

    Page<Player> findByNicknameContainingIgnoreCase(String search, Pageable pageable);

    List<Player> findByActiveTrue();

    List<Player> findByIdIn(List<UUID> ids);
}
