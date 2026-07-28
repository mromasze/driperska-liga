package pl.romcio.driperska.match.application;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.*;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftView;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DrawLobbyResponse;
import pl.romcio.driperska.match.api.DrawLobbyDtos.LobbyPlayer;
import pl.romcio.driperska.match.domain.*;
import pl.romcio.driperska.match.infra.DrawVoteRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class DrawLobbyService {
    public static final int REQUIRED_ACCEPTS = 6;

    private final MatchRepository matchRepository;
    private final MatchService matchService;
    private final DrawService drawService;
    private final DrawVoteRepository voteRepository;
    private final PlayerRepository playerRepository;
    private final DrawRealtimeService realtime;
    private final DraftService draftService;
    private final DrawProperties drawProperties;

    public DrawLobbyService(MatchRepository matchRepository, MatchService matchService,
                            DrawService drawService, DrawVoteRepository voteRepository,
                            PlayerRepository playerRepository, DrawRealtimeService realtime,
                            DraftService draftService,
                            DrawProperties drawProperties) {
        this.matchRepository = matchRepository;
        this.matchService = matchService;
        this.drawService = drawService;
        this.voteRepository = voteRepository;
        this.playerRepository = playerRepository;
        this.realtime = realtime;
        this.draftService = draftService;
        this.drawProperties = drawProperties;
    }

    @Transactional
    public DrawService.DrawResult adminDraw(UUID matchId, UUID actor) {
        DrawService.DrawResult result = drawService.draw(matchId, actor);
        publish(matchService.get(matchId));
        return result;
    }

    @Transactional
    public DrawService.DrawResult adminManualDraw(UUID matchId, UUID actor,
                                                  List<DrawService.ManualSlot> assignment) {
        DrawService.DrawResult result = drawService.manualDraw(matchId, actor, assignment);
        publish(matchService.get(matchId));
        return result;
    }

    @Transactional
    public Match adminConfirm(UUID matchId, UUID actor) {
        Match match = drawService.confirm(matchId, actor);
        publish(match);
        return match;
    }

    /** Auto-confirms a draw that has been sitting unvoted past the timeout. No-op if it already moved on. */
    @Transactional
    public void autoConfirm(UUID matchId) {
        Match current = matchRepository.findById(matchId).orElse(null);
        if (current == null || current.getStatus() != MatchStatus.TEAMS_DRAWN) {
            return;
        }
        Match match = drawService.confirm(matchId, current.getCreatedBy());
        publish(match);
    }

    @Transactional(readOnly = true)
    public DrawLobbyResponse active(UUID accountId) {
        Player player = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        // Only ongoing matches appear in the player's panel; once results are submitted the match
        // "disappears" and the post-match survey takes over.
        return matchRepository.findForPlayerAndStatuses(player.getId(),
                        EnumSet.of(MatchStatus.TEAMS_DRAWN, MatchStatus.DRAFT_READY, MatchStatus.DRAFTING,
                                MatchStatus.DRAFTED, MatchStatus.LOBBY_READY, MatchStatus.LIVE,
                                MatchStatus.RESULTS_SUBMITTED),
                        PageRequest.of(0, 1))
                .stream().findFirst().map(this::toResponse).orElse(null);
    }

    /** Draw state (vote tally + teams) for a specific match — used by the admin control panel. */
    @Transactional(readOnly = true)
    public DrawLobbyResponse stateForMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        return toResponse(match);
    }

    @Transactional
    public DrawLobbyResponse vote(UUID matchId, DrawVoteDecision decision, UUID accountId) {
        Match match = matchRepository.findForUpdate(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        if (match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Głosowanie dla tej rundy jest już zakończone");
        }
        Player player = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        if (!match.getPoolPlayerIds().contains(player.getId())) {
            throw new BusinessRuleException("Tylko uczestnik meczu może głosować");
        }
        if (voteRepository.existsByMatchIdAndDrawRoundAndPlayerId(
                matchId, match.getDrawRound(), player.getId())) {
            throw new BusinessRuleException("Twój głos w tej rundzie został już oddany");
        }

        voteRepository.saveAndFlush(new DrawVote(
                matchId, match.getDrawRound(), player.getId(), accountId, decision));
        List<DrawVote> votes = voteRepository.findByMatchIdAndDrawRound(matchId, match.getDrawRound());
        long accepts = votes.stream().filter(v -> v.getDecision() == DrawVoteDecision.ACCEPT).count();
        long rejects = votes.stream().filter(v -> v.getDecision() == DrawVoteDecision.REJECT).count();

        if (accepts >= REQUIRED_ACCEPTS) {
            drawService.confirm(matchId, accountId);
        } else if (rejects >= MatchService.REQUIRED_POOL_SIZE - REQUIRED_ACCEPTS + 1) {
            drawService.draw(matchId, accountId);
        }

        Match updated = matchService.get(matchId);
        DrawLobbyResponse response = toResponse(updated);
        publish(updated, response);
        return response;
    }

    private DrawLobbyResponse toResponse(Match match) {
        Map<UUID, Player> players = new HashMap<>();
        // Look up by participant id as well as by pool id: after a substitution the two can disagree,
        // and a missing entry used to NPE here, which took the whole draft screen down with a 500.
        List<UUID> ids = new ArrayList<>(match.getPoolPlayerIds());
        match.getParticipants().forEach(p -> ids.add(p.getPlayerId()));
        playerRepository.findByIdIn(ids.stream().distinct().toList())
                .forEach(p -> players.put(p.getId(), p));
        List<DrawVote> votes = voteRepository.findByMatchIdAndDrawRound(match.getId(), match.getDrawRound());
        DraftView draft = draftService.view(match);
        Set<UUID> captains = new HashSet<>();
        if (draft != null) {
            if (draft.blueCaptain() != null) captains.add(draft.blueCaptain());
            if (draft.redCaptain() != null) captains.add(draft.redCaptain());
        }
        List<LobbyPlayer> slots = match.getParticipants().stream().map(p -> {
            Player player = players.get(p.getPlayerId());
            return new LobbyPlayer(p.getPlayerId(),
                    player != null ? player.getNickname() : "?",
                    player != null ? player.getAvatarUrl() : null,
                    p.getRole(), p.getSide(), p.getChampionId(), captains.contains(p.getPlayerId()));
        }).toList();
        List<UUID> accepted = votes.stream().filter(v -> v.getDecision() == DrawVoteDecision.ACCEPT)
                .map(DrawVote::getPlayerId).toList();
        List<UUID> rejected = votes.stream().filter(v -> v.getDecision() == DrawVoteDecision.REJECT)
                .map(DrawVote::getPlayerId).toList();
        Instant voteDeadline = (match.getStatus() == MatchStatus.TEAMS_DRAWN
                && match.getTeamsDrawnAt() != null && drawProperties.getAutoConfirmSeconds() > 0)
                ? match.getTeamsDrawnAt().plusSeconds(drawProperties.getAutoConfirmSeconds()) : null;
        return new DrawLobbyResponse(match.getId(), match.getStatus(), match.getDrawRound(),
                REQUIRED_ACCEPTS, accepted.size(), rejected.size(), accepted, rejected,
                slots.stream().filter(p -> p.side() == Side.BLUE).toList(),
                slots.stream().filter(p -> p.side() == Side.RED).toList(), Instant.now(),
                match.getRiotTournamentCode(), match.getRiotImportError(), voteDeadline, draft);
    }

    private void publish(Match match) { publish(match, toResponse(match)); }

    /** Reloads the match inside a transaction so lazy collections resolve, then broadcasts over SSE. */
    @Transactional
    public void publishUpdate(UUID matchId) { publish(matchService.get(matchId)); }

    private void publish(Match match, DrawLobbyResponse response) {
        List<UUID> accountIds = playerRepository.findByIdIn(match.getPoolPlayerIds())
                .stream().map(Player::getAccountId).filter(Objects::nonNull).toList();
        realtime.broadcast(accountIds, response);
    }
}