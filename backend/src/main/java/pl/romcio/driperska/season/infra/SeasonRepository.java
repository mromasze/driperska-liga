package pl.romcio.driperska.season.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.season.domain.Season;
import pl.romcio.driperska.season.domain.SeasonStatus;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

    Optional<Season> findFirstByStatus(SeasonStatus status);
}
