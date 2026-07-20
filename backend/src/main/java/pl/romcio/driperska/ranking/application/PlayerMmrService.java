package pl.romcio.driperska.ranking.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.ranking.infra.PlayerSeasonStatsRepository;

/** Read model exposing current MMR and games played per player in a season, for team balancing. */
@Service
public class PlayerMmrService {

    private final PlayerSeasonStatsRepository statsRepository;
    private final ScoringConfigProvider configProvider;

    public PlayerMmrService(PlayerSeasonStatsRepository statsRepository, ScoringConfigProvider configProvider) {
        this.statsRepository = statsRepository;
        this.configProvider = configProvider;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Double> currentMmr(UUID seasonId, List<UUID> playerIds) {
        ScoringConfig cfg = configProvider.forSeason(seasonId);
        Map<UUID, Double> result = new HashMap<>();
        for (UUID playerId : playerIds) {
            double mmr = statsRepository.findByPlayerIdAndSeasonId(playerId, seasonId)
                    .map(s -> s.getMmr())
                    .orElse(cfg.mmrStart());
            result.put(playerId, mmr);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> gamesPlayed(UUID seasonId, List<UUID> playerIds) {
        Map<UUID, Integer> result = new HashMap<>();
        for (UUID playerId : playerIds) {
            int games = statsRepository.findByPlayerIdAndSeasonId(playerId, seasonId)
                    .map(s -> s.getGames())
                    .orElse(0);
            result.put(playerId, games);
        }
        return result;
    }
}
