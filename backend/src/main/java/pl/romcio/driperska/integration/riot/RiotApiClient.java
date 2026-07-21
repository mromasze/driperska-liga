package pl.romcio.driperska.integration.riot;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.romcio.driperska.common.error.ExternalServiceException;

@Component
public class RiotApiClient {
    private static final int MAX_ATTEMPTS = 3;
    private final RiotProperties properties;
    private final RestClient client = RestClient.builder()
            .requestFactory(timeoutFactory()).build();

    public RiotApiClient(RiotProperties properties) {
        this.properties = properties;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return factory;
    }

    public long createProvider(String callbackUrl) {
        Long id = execute("rejestracja providera", () -> client.post()
                .uri(properties.tournamentBaseUrl() + "/lol/tournament/v5/providers")
                .header("X-Riot-Token", apiKey())
                .body(new ProviderRequest(properties.getProviderRegion(), callbackUrl))
                .retrieve().body(Long.class));
        return required(id, "Riot nie zwrócił identyfikatora providera");
    }

    public long createTournament(long providerId) {
        Long id = execute("rejestracja turnieju", () -> client.post()
                .uri(properties.tournamentBaseUrl() + "/lol/tournament/v5/tournaments")
                .header("X-Riot-Token", apiKey())
                .body(new TournamentRequest(properties.getTournamentName(), providerId))
                .retrieve().body(Long.class));
        return required(id, "Riot nie zwrócił identyfikatora turnieju");
    }

    public String createTournamentCode(long tournamentId, List<String> allowedParticipants,
                                       String metadata) {
        TournamentCodeRequest body = new TournamentCodeRequest(
                allowedParticipants, metadata, properties.getTeamSize(), properties.getPickType(),
                properties.getMapType(), properties.getSpectatorType());
        List<String> codes = execute("tworzenie kodu turniejowego", () -> client.post()
                .uri(builder -> builder.scheme("https").host(properties.getPlatform() + ".api.riotgames.com")
                        .path("/lol/tournament/v5/codes")
                        .queryParam("tournamentId", tournamentId).queryParam("count", 1).build())
                .header("X-Riot-Token", apiKey()).body(body).retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {}));
        if (codes == null || codes.size() != 1) {
            throw new ExternalServiceException("Riot Tournament API",
                    "oczekiwano jednego kodu lobby, otrzymano " + (codes == null ? 0 : codes.size()));
        }
        return codes.getFirst();
    }

    public List<TournamentGame> getGames(String tournamentCode) {
        List<TournamentGame> games = execute("pobieranie gier po kodzie", () -> client.get()
                .uri(builder -> builder.scheme("https").host(properties.getPlatform() + ".api.riotgames.com")
                        .path("/lol/tournament/v5/games/by-code/").pathSegment(tournamentCode).build())
                .header("X-Riot-Token", apiKey()).retrieve()
                .body(new ParameterizedTypeReference<List<TournamentGame>>() {}));
        return games == null ? List.of() : games;
    }

    /**
     * Builds a Match-v5 id ({@code PLATFORM_gameId}) for a tournament game. Every game runs on the
     * configured platform, so we prefix with it rather than trusting the region field Riot returns.
     */
    public String matchIdFor(String gameId) {
        if (gameId != null && gameId.contains("_")) return gameId;
        return properties.getPlatform().toUpperCase(Locale.ROOT) + "_" + gameId;
    }

    public LobbyEventList getLobbyEvents(String tournamentCode) {
        LobbyEventList events = execute("pobieranie zdarzeń lobby", () -> client.get()
                .uri(builder -> builder.scheme("https").host(properties.getPlatform() + ".api.riotgames.com")
                        .path("/lol/tournament/v5/lobby-events/by-code/")
                        .pathSegment(tournamentCode).build())
                .header("X-Riot-Token", apiKey()).retrieve().body(LobbyEventList.class));
        return events == null ? new LobbyEventList(List.of()) : events;
    }

    public RiotMatch getMatch(String matchId) {
        RiotMatch result = execute("pobieranie statystyk meczu", () -> client.get()
                .uri(builder -> builder.scheme("https")
                        .host(properties.getRegionalRoute() + ".api.riotgames.com")
                        .path("/lol/match/v5/matches/").pathSegment(matchId).build())
                .header("X-Riot-Token", apiKey()).retrieve().body(RiotMatch.class));
        if (result == null || result.info() == null) {
            throw new ExternalServiceException("Riot Match API", "odpowiedź meczu jest pusta");
        }
        return result;
    }

    public RiotAccount resolveRiotId(String gameName, String tagLine) {
        RiotAccount account = execute("weryfikacja Riot ID", () -> client.get()
                .uri(builder -> builder.scheme("https")
                        .host(properties.getRegionalRoute() + ".api.riotgames.com")
                        .path("/riot/account/v1/accounts/by-riot-id/")
                        .pathSegment(gameName).pathSegment(tagLine).build())
                .header("X-Riot-Token", apiKey()).retrieve().body(RiotAccount.class));
        if (account == null || account.puuid() == null) {
            throw new ExternalServiceException("Riot Account API", "nie znaleziono Riot ID");
        }
        return account;
    }

    public RiotSummoner resolveSummoner(String puuid) {
        RiotSummoner summoner = execute("pobieranie identyfikatora przywoływacza", () -> client.get()
                .uri(builder -> builder.scheme("https").host(properties.getPlatform() + ".api.riotgames.com")
                        .path("/lol/summoner/v4/summoners/by-puuid/").pathSegment(puuid).build())
                .header("X-Riot-Token", apiKey()).retrieve().body(RiotSummoner.class));
        if (summoner == null || summoner.id() == null) {
            throw new ExternalServiceException("Riot Summoner API", "nie znaleziono konta LoL");
        }
        return summoner;
    }

    private String apiKey() {
        if (!properties.configured()) {
            throw new ExternalServiceException("Riot API",
                    "brak RIOT_API_KEY w konfiguracji serwera");
        }
        return properties.getApiKey();
    }

    private <T> T execute(String operation, Supplier<T> action) {
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RestClientResponseException ex) {
                last = ex;
                int status = ex.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw external(operation, status);
                }
                long retryAfter = parseRetryAfter(ex.getResponseHeaders());
                sleep(Duration.ofSeconds(Math.max(retryAfter, attempt)));
            } catch (RestClientException ex) {
                last = ex;
                if (attempt == MAX_ATTEMPTS) break;
                sleep(Duration.ofMillis(250L * attempt * attempt));
            }
        }
        throw new ExternalServiceException("Riot API",
                operation + " nie powiodło się po " + MAX_ATTEMPTS + " próbach"
                        + (last == null ? "" : " (" + last.getClass().getSimpleName() + ")"));
    }

    private ExternalServiceException external(String operation, int status) {
        String hint = switch (status) {
            case 401, 403 -> "sprawdź ważność klucza i dostęp do Tournament API";
            case 404 -> "zasób nie istnieje lub nie jest jeszcze dostępny";
            case 429 -> "przekroczono limit zapytań; spróbuj ponownie po chwili";
            default -> "Riot zwrócił HTTP " + status;
        };
        return new ExternalServiceException("Riot API", operation + ": " + hint);
    }

    private static long parseRetryAfter(HttpHeaders headers) {
        if (headers == null) return 1;
        try { return Long.parseLong(headers.getFirst("Retry-After")); }
        catch (RuntimeException ignored) { return 1; }
    }

    private static void sleep(Duration duration) {
        try { Thread.sleep(duration); }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Riot API", "operacja została przerwana");
        }
    }

    private static long required(Long value, String message) {
        if (value == null) throw new ExternalServiceException("Riot Tournament API", message);
        return value;
    }

    public record ProviderRequest(String region, String url) {}
    public record TournamentRequest(String name, long providerId) {}
    public record TournamentCodeRequest(List<String> allowedParticipants, String metadata,
                                        int teamSize, String pickType, String mapType,
                                        String spectatorType) {}
    public record LobbyEventList(List<LobbyEvent> eventList) {}
    public record LobbyEvent(String timestamp, String eventType, String puuid) {}
    public record TournamentGame(long gameId, String region, String metaData, String shortCode) {}
    public record RiotAccount(String puuid, String gameName, String tagLine) {}
    public record RiotSummoner(String id, String accountId, String puuid) {}
    public record RiotMatch(MatchMetadata metadata, MatchInfo info) {}
    public record MatchMetadata(String matchId, List<String> participants) {}
    public record MatchInfo(long gameDuration, String gameVersion,
                            List<RiotParticipant> participants, List<RiotTeam> teams) {}
    public record RiotParticipant(String puuid, String riotIdGameName, String riotIdTagline,
                                  int teamId, int championId, int kills, int deaths, int assists,
                                  int totalMinionsKilled, int neutralMinionsKilled, int goldEarned,
                                  int totalDamageDealtToChampions, int visionScore,
                                  int largestMultiKill, String teamPosition,
                                  String individualPosition) {}
    public record RiotTeam(int teamId, boolean win) {}
}

