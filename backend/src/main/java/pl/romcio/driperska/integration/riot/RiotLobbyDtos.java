package pl.romcio.driperska.integration.riot;

import java.util.List;

public final class RiotLobbyDtos {
    private RiotLobbyDtos() {}

    public record LobbyMember(String playerId, String nickname, String puuid, boolean joined) {}
    public record LobbyStatusResponse(int joinedCount, int expectedCount, boolean gameStarted,
                                      List<LobbyMember> members,
                                      List<RiotApiClient.LobbyEvent> events) {}
}

