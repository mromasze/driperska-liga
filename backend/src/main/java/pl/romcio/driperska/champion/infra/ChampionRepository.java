package pl.romcio.driperska.champion.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.champion.domain.Champion;

public interface ChampionRepository extends JpaRepository<Champion, Integer> {

    List<Champion> findAllByOrderByNameAsc();
}
