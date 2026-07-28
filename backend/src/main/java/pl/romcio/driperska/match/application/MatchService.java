package pl.romcio.driperska.match.application;

import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.*;
import pl.romcio.driperska.match.domain.*;
import pl.romcio.driperska.match.infra.MatchEventRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;
import pl.romcio.driperska.season.application.SeasonService;

@Service
public class MatchService {
    public static final int REQUIRED_POOL_SIZE = 10;

    private final MatchRepository matchRepository;
    private final MatchEventRepository eventRepository;
    private final MatchEventRecorder eventRecorder;
    private final PlayerRepository playerRepository;
    private final SeasonService seasonService;

    public MatchService(MatchRepository matchRepository, MatchEventRepository eventRepository,
                        MatchEventRecorder eventRecorder, PlayerRepository playerRepository,
                        SeasonService seasonService) {
        this.matchRepository = matchRepository;
        this.eventRepository = eventRepository;
        this.eventRecorder = eventRecorder;
        this.playerRepository = playerRepository;
        this.seasonService = seasonService;
    }

    @Transactional
    public Match create(UUID seasonId, DrawMode drawMode, List<UUID> playerIds, UUID actor) {
        seasonService.get(seasonId);
        Set<UUID> distinct = Set.copyOf(playerIds);
        if (distinct.size() != REQUIRED_POOL_SIZE) {
            throw new BusinessRuleException(
                    "Pula musi zawierać dokładnie %d różnych graczy".formatted(REQUIRED_POOL_SIZE));
        }
        List<Player> players = playerRepository.findByIdIn(List.copyOf(distinct));
        if (players.size() != REQUIRED_POOL_SIZE) {
            throw new BusinessRuleException("Niektórzy gracze z puli nie istnieją");
        }
        if (players.stream().anyMatch(player -> player.getAccountId() == null)) {
            throw new BusinessRuleException(
                    "Każdy uczestnik musi mieć konto logowania przed rozpoczęciem losowania");
        }
        Match match = new Match(seasonId, drawMode == null ? DrawMode.PURE_RANDOM : drawMode, actor);
        match.setPoolPlayerIds(List.copyOf(distinct));
        Match saved = matchRepository.save(match);
        eventRecorder.record(saved.getId(), MatchEventType.CREATED, actor,
                Map.of("pool", distinct, "drawMode", saved.getDrawMode().name()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Match get(UUID id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", id));
    }

    @Transactional(readOnly = true)
    public Page<Match> list(MatchStatus status, UUID seasonId, Pageable pageable) {
        if (status != null && seasonId != null) return matchRepository.findByStatusAndSeasonId(status, seasonId, pageable);
        if (status != null) return matchRepository.findByStatus(status, pageable);
        if (seasonId != null) return matchRepository.findBySeasonId(seasonId, pageable);
        return matchRepository.findAllForListing(pageable);
    }

    @Transactional(readOnly = true)
    public List<Match> recentApproved() {
        return matchRepository.findByStatusOrderByCompletedAtDesc(MatchStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public List<MatchEvent> events(UUID id) {
        return eventRepository.findByMatchIdOrderByCreatedAtAsc(id);
    }

    @Transactional
    public Match cancel(UUID id, UUID actor) {
        Match match = get(id);
        match.transitionTo(MatchStatus.CANCELLED);
        eventRecorder.record(id, MatchEventType.CANCELLED, actor, null);
        return match;
    }
}