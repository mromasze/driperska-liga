package pl.romcio.driperska.match.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * Chat for the draft: one channel for everybody in the lobby, one per team.
 *
 * <p>Nothing is written to the database — deliberately. These are ten people coordinating bans for a
 * few minutes, not a record anyone will want next season, and storing it would mean a schema, a
 * retention question and a moderation question for something with no lasting value. Messages live in a
 * short in-memory buffer per match so a player who refreshes still sees the last few lines, and they
 * are gone when the process restarts or the match leaves the draft.
 *
 * <p>Delivery rides the existing SSE stream ({@link DrawRealtimeService}) rather than a second
 * connection: one socket per player, one reconnect path, nothing new in the proxy configuration. A
 * team message is only ever sent to the accounts on that side, so the wire itself keeps the secret.
 */
@Service
public class DraftChatService {

    public enum Scope { ALL, TEAM }

    /** Enough to catch up after a refresh, small enough to be irrelevant to memory. */
    private static final int BUFFER_SIZE = 60;
    private static final int MAX_LENGTH = 300;
    /** Minimum gap between two messages from the same account — stops a stuck key flooding ten clients. */
    private static final Duration COOLDOWN = Duration.ofMillis(400);
    /** Phases in which the chat exists at all. */
    private static final EnumSet<MatchStatus> CHAT_PHASES =
            EnumSet.of(MatchStatus.DRAFT_READY, MatchStatus.DRAFTING, MatchStatus.DRAFTED);

    private final MatchService matchService;
    private final PlayerRepository playerRepository;
    private final DrawRealtimeService realtime;
    private final Map<UUID, Deque<ChatMessage>> buffers = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastMessageAt = new ConcurrentHashMap<>();

    public DraftChatService(MatchService matchService, PlayerRepository playerRepository,
                            DrawRealtimeService realtime) {
        this.matchService = matchService;
        this.playerRepository = playerRepository;
        this.realtime = realtime;
    }

    /**
     * @param side  the team a TEAM message belongs to; null for ALL
     * @param admin true when it came from the admin panel rather than a player in the lobby
     */
    public record ChatMessage(UUID id, UUID matchId, Scope scope, Side side, UUID playerId,
                              String nickname, String text, boolean admin, Instant at) {}

    /** A player in the lobby says something. */
    @Transactional(readOnly = true)
    public ChatMessage sendAsPlayer(UUID matchId, UUID accountId, Scope scope, String text) {
        Match match = requireChatPhase(matchId);
        Player player = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        MatchParticipant me = match.getParticipants().stream()
                .filter(p -> p.getPlayerId().equals(player.getId())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Tylko uczestnik meczu może pisać w tym chacie"));
        requireCooldown(accountId);
        ChatMessage message = new ChatMessage(UUID.randomUUID(), matchId, scope,
                scope == Scope.TEAM ? me.getSide() : null, player.getId(), player.getNickname(),
                clean(text), false, Instant.now());
        return dispatch(match, message);
    }

    /** The admin talking to the lobby. Always to everyone — a private admin channel makes no sense. */
    @Transactional(readOnly = true)
    public ChatMessage sendAsAdmin(UUID matchId, UUID accountId, String username, String text) {
        Match match = requireChatPhase(matchId);
        requireCooldown(accountId);
        ChatMessage message = new ChatMessage(UUID.randomUUID(), matchId, Scope.ALL, null, null,
                username == null || username.isBlank() ? "admin" : username, clean(text), true,
                Instant.now());
        return dispatch(match, message);
    }

    /**
     * Recent messages this account is allowed to see: the whole all-chat plus its own team's lines.
     * Fetched once when the draft screen mounts, because the stream only carries what happens next.
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> history(UUID matchId, UUID accountId, boolean admin) {
        Deque<ChatMessage> buffer = buffers.get(matchId);
        if (buffer == null) return List.of();
        Side mySide = admin ? null : sideOf(matchId, accountId);
        return buffer.stream()
                .filter(message -> message.scope() == Scope.ALL || admin || message.side() == mySide)
                .toList();
    }

    /** Frees the buffer once the draft is behind us; also called when a draft is reset. */
    public void clear(UUID matchId) {
        buffers.remove(matchId);
    }

    // --- internals ---------------------------------------------------------

    private ChatMessage dispatch(Match match, ChatMessage message) {
        buffers.computeIfAbsent(message.matchId(), ignored -> new ArrayDeque<>()).add(message);
        Deque<ChatMessage> buffer = buffers.get(message.matchId());
        synchronized (buffer) {
            while (buffer.size() > BUFFER_SIZE) buffer.removeFirst();
        }
        realtime.broadcast(recipients(match, message), DrawRealtimeService.EVENT_CHAT, message);
        return message;
    }

    /** ALL goes to the ten players; TEAM only to the five accounts on that side. */
    private List<UUID> recipients(Match match, ChatMessage message) {
        List<UUID> playerIds = match.getParticipants().stream()
                .filter(p -> message.scope() == Scope.ALL || p.getSide() == message.side())
                .map(MatchParticipant::getPlayerId)
                .toList();
        return playerRepository.findByIdIn(playerIds).stream()
                .map(Player::getAccountId).filter(Objects::nonNull).toList();
    }

    private Side sideOf(UUID matchId, UUID accountId) {
        return playerRepository.findByAccountId(accountId)
                .flatMap(player -> matchService.get(matchId).getParticipants().stream()
                        .filter(p -> p.getPlayerId().equals(player.getId()))
                        .findFirst())
                .map(MatchParticipant::getSide)
                .orElse(null);
    }

    private Match requireChatPhase(UUID matchId) {
        Match match = matchService.get(matchId);
        if (!CHAT_PHASES.contains(match.getStatus())) {
            throw new BusinessRuleException("Chat działa tylko wokół draftu");
        }
        return match;
    }

    private void requireCooldown(UUID accountId) {
        Instant now = Instant.now();
        Instant previous = lastMessageAt.put(accountId, now);
        if (previous != null && Duration.between(previous, now).compareTo(COOLDOWN) < 0) {
            throw new BusinessRuleException("Zwolnij trochę — jedna wiadomość na chwilę");
        }
    }

    private static String clean(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("Pusta wiadomość");
        }
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }
}
