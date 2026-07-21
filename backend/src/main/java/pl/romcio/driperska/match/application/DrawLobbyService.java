package pl.romcio.driperska.match.application;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.*;
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

    public DrawLobbyService(MatchRepository matchRepository, MatchService matchService,
                            DrawService drawService, DrawVoteRepository voteRepository,
                            PlayerRepository playerRepository, DrawRealtimeService realtime) {
        this.matchRepository = matchRepository;
        this.matchService = matchService;
        this.drawService = drawService;
        this.voteRepository = voteRepository;
        this.playerRepository = playerRepository;
        this.realtime = realtime;
    }

    @Transactional
    public DrawService.DrawResult adminDraw(UUID matchId, UUID actor) {
        DrawService.DrawResult result = drawService.draw(matchId, actor);
        publish(matchService.get(matchId));
        return result;
    }

    @Transactional
    public Match adminConfirm(UUID matchId, UUID actor) {
        Match match = drawService.confirm(matchId, actor);
        publish(match);
        return match;
    }

    @Transactional(readOnly = true)
    public DrawLobbyResponse active(UUID accountId) {
        Player player = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        return matchRepository.findForPlayerAndStatus(
                        player.getId(), MatchStatus.TEAMS_DRAWN, PageRequest.of(0, 1))
                .stream().findFirst().map(this::toResponse).orElse(null);
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
        playerRepository.findByIdIn(match.getPoolPlayerIds()).forEach(p -> players.put(p.getId(), p));
        List<DrawVote> votes = voteRepository.findByMatchIdAndDrawRound(match.getId(), match.getDrawRound());
        List<LobbyPlayer> slots = match.getParticipants().stream().map(p -> {
            Player player = players.get(p.getPlayerId());
            return new LobbyPlayer(p.getPlayerId(), player.getNickname(), player.getAvatarUrl(),
                    p.getRole(), p.getSide());
        }).toList();
        List<UUID> accepted = votes.stream().filter(v -> v.getDecision() == DrawVoteDecision.ACCEPT)
                .map(DrawVote::getPlayerId).toList();
        List<UUID> rejected = votes.stream().filter(v -> v.getDecision() == DrawVoteDecision.REJECT)
                .map(DrawVote::getPlayerId).toList();
        return new DrawLobbyResponse(match.getId(), match.getStatus(), match.getDrawRound(),
                REQUIRED_ACCEPTS, accepted.size(), rejected.size(), accepted, rejected,
                slots.stream().filter(p -> p.side() == Side.BLUE).toList(),
                slots.stream().filter(p -> p.side() == Side.RED).toList(), Instant.now());
    }

    private void publish(Match match) { publish(match, toResponse(match)); }
    private void publish(Match match, DrawLobbyResponse response) {
        List<UUID> accountIds = playerRepository.findByIdIn(match.getPoolPlayerIds())
                .stream().map(Player::getAccountId).filter(Objects::nonNull).toList();
        realtime.broadcast(accountIds, response);
    }
}