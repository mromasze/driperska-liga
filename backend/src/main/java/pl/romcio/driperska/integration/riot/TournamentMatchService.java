package pl.romcio.driperska.integration.riot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.riot.RiotLobbyDtos.LobbyMember;
import pl.romcio.driperska.integration.riot.RiotLobbyDtos.LobbyStatusResponse;
import pl.romcio.driperska.match.application.MatchEventRecorder;
import pl.romcio.driperska.match.domain.*;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class TournamentMatchService {
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final RiotRegistrationService registrationService;
    private final RiotApiClient client;
    private final RiotIdentityService identityService;
    private final MatchEventRecorder eventRecorder;
    private final ObjectMapper objectMapper;
    private final RiotProperties properties;

    public TournamentMatchService(MatchRepository matchRepository, PlayerRepository playerRepository,
                                  RiotRegistrationService registrationService, RiotApiClient client,
                                  RiotIdentityService identityService, MatchEventRecorder eventRecorder,
                                  ObjectMapper objectMapper, RiotProperties properties) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.registrationService = registrationService;
        this.client = client;
        this.identityService = identityService;
        this.eventRecorder = eventRecorder;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public Match createLobby(UUID matchId, UUID actor) {
        Match match = locked(matchId);
        if (match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Lobby Riot można utworzyć dopiero po zatwierdzeniu składów");
        }
        issueCode(match);
        match.transitionTo(MatchStatus.LOBBY_READY);
        eventRecorder.record(matchId, MatchEventType.DRAW_CONFIRMED, actor,
                Map.of("round", match.getDrawRound()));
        eventRecorder.record(matchId, MatchEventType.RIOT_LOBBY_CREATED, actor,
                Map.of("tournamentCode", match.getRiotTournamentCode()));
        return match;
    }

    @Transactional
    public Match start(UUID matchId, UUID actor) {
        Match match = locked(matchId);
        if (match.getStatus() != MatchStatus.LOBBY_READY) {
            throw new InvalidTransitionException("Mecz można uruchomić dopiero z gotowego lobby Riot");
        }
        match.transitionTo(MatchStatus.LIVE);
        match.setStartedAt(Instant.now());
        eventRecorder.record(matchId, MatchEventType.MATCH_STARTED, actor, null);
        return match;
    }

    /** Starts a match manually, without creating a Riot lobby — for matches recorded by hand. */
    @Transactional
    public Match startManual(UUID matchId, UUID actor) {
        Match match = locked(matchId);
        if (match.getStatus() != MatchStatus.TEAMS_DRAWN && match.getStatus() != MatchStatus.LOBBY_READY) {
            throw new InvalidTransitionException("Ręczne rozpoczęcie jest możliwe po wylosowaniu składów");
        }
        match.clearRiotLobby();
        match.transitionTo(MatchStatus.LIVE);
        match.setStartedAt(Instant.now());
        eventRecorder.record(matchId, MatchEventType.MATCH_STARTED, actor, Map.of("manual", true));
        return match;
    }

    @Transactional
    public Match replacePlayer(UUID matchId, UUID oldPlayerId, UUID newPlayerId, UUID actor) {
        Match match = locked(matchId);
        if (match.getStatus() != MatchStatus.DRAFT
                && match.getStatus() != MatchStatus.TEAMS_DRAWN
                && match.getStatus() != MatchStatus.LOBBY_READY) {
            throw new InvalidTransitionException(
                    "Skład można zmienić przed rozpoczęciem meczu; podczas gry i po wyniku jest zablokowany");
        }
        if (!match.getPoolPlayerIds().contains(oldPlayerId)) {
            throw new BusinessRuleException("Usuwany gracz nie należy do tego meczu");
        }
        if (match.getPoolPlayerIds().contains(newPlayerId)) {
            throw new BusinessRuleException("Nowy gracz już należy do tego meczu");
        }
        Player replacement = playerRepository.findById(newPlayerId)
                .filter(Player::isActive)
                .orElseThrow(() -> new BusinessRuleException("Nowy gracz nie istnieje lub jest nieaktywny"));
        if (replacement.getAccountId() == null) {
            throw new BusinessRuleException("Nowy gracz musi mieć konto portalu");
        }

        List<UUID> pool = new ArrayList<>(match.getPoolPlayerIds());
        pool.set(pool.indexOf(oldPlayerId), newPlayerId);
        match.setPoolPlayerIds(pool);

        if (!match.getParticipants().isEmpty()) {
            List<MatchParticipant> participants = match.getParticipants().stream()
                    .map(existing -> existing.getPlayerId().equals(oldPlayerId)
                            ? new MatchParticipant(newPlayerId, existing.getSide(), existing.getRole())
                            : new MatchParticipant(existing.getPlayerId(), existing.getSide(), existing.getRole()))
                    .toList();
            match.replaceParticipants(participants);
        }

        boolean reissued = match.getStatus() == MatchStatus.LOBBY_READY;
        if (reissued) {
            match.clearRiotLobby();
            issueCode(match);
            eventRecorder.record(matchId, MatchEventType.RIOT_LOBBY_CREATED, actor,
                    Map.of("tournamentCode", match.getRiotTournamentCode(), "reason", "player-replaced"));
        }
        eventRecorder.record(matchId, MatchEventType.PLAYER_REPLACED, actor,
                Map.of("removedPlayerId", oldPlayerId, "addedPlayerId", newPlayerId,
                        "riotCodeReissued", reissued));
        return match;
    }

    @Transactional
    public LobbyStatusResponse lobbyStatus(UUID matchId) {
        Match match = locked(matchId);
        if (match.getRiotTournamentCode() == null) {
            throw new BusinessRuleException("Ten mecz nie ma jeszcze lobby Riot");
        }
        List<Player> players = players(match);
        players.forEach(identityService::resolve);
        if (properties.isMock()) {
            // Mock lobby: everyone is "in" so the admin can start immediately.
            List<LobbyMember> mockMembers = players.stream()
                    .map(player -> new LobbyMember(player.getId().toString(), player.getNickname(),
                            player.getRiotPuuid(), true))
                    .toList();
            return new LobbyStatusResponse(mockMembers.size(), mockMembers.size(), true,
                    mockMembers, List.of());
        }
        RiotApiClient.LobbyEventList timeline = client.getLobbyEvents(match.getRiotTournamentCode());
        Set<String> joined = new HashSet<>();
        boolean gameStarted = false;
        for (RiotApiClient.LobbyEvent event : timeline.eventList()) {
            if ("PlayerJoinedGameEvent".equals(event.eventType())) joined.add(event.puuid());
            if ("PlayerQuitGameEvent".equals(event.eventType())) joined.remove(event.puuid());
            if (event.eventType() != null && (event.eventType().startsWith("ChampSelect")
                    || event.eventType().startsWith("GameAllocation")
                    || event.eventType().startsWith("GameAllocated"))) {
                gameStarted = true;
            }
        }
        List<LobbyMember> members = players.stream()
                .map(player -> new LobbyMember(player.getId().toString(), player.getNickname(),
                        player.getRiotPuuid(), joined.contains(player.getRiotPuuid())))
                .toList();
        return new LobbyStatusResponse((int) members.stream().filter(LobbyMember::joined).count(),
                members.size(), gameStarted, members, timeline.eventList());
    }

    private void issueCode(Match match) {
        List<Player> players = players(match);
        if (players.size() != 10) {
            throw new BusinessRuleException("Lobby Riot wymaga dokładnie 10 graczy");
        }
        players.forEach(identityService::resolve);
        String token = UUID.randomUUID().toString().replace("-", "");
        if (properties.isMock()) {
            // Fake but real-looking code so the UI and downstream flow work offline.
            match.markRiotLobby("MOCK-" + token.substring(0, 12).toUpperCase(java.util.Locale.ROOT), token);
            return;
        }
        RiotTournamentRegistration registration = registrationService.registration();
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                    "matchId", match.getId().toString(), "token", token));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nie można utworzyć metadanych callbacku Riot", ex);
        }
        String code = client.createTournamentCode(registration.getTournamentId(),
                players.stream().map(Player::getRiotPuuid).toList(), metadata);
        match.markRiotLobby(code, token);
    }

    private List<Player> players(Match match) {
        Map<UUID, Player> byId = new HashMap<>();
        playerRepository.findByIdIn(match.getPoolPlayerIds()).forEach(p -> byId.put(p.getId(), p));
        return match.getPoolPlayerIds().stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Match locked(UUID id) {
        return matchRepository.findForUpdate(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", id));
    }
}

