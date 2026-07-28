package pl.romcio.driperska.integration.discord;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DiscordClient {
    private static final String BASE = "https://discord.com/api/v10";
    private static final Logger log = LoggerFactory.getLogger(DiscordClient.class);
    private final DiscordProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client = RestClient.builder()
            .requestFactory(timeoutFactory()).build();

    public DiscordClient(DiscordProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return factory;
    }

    /** Posts a PNG image (with caption) to the configured results channel. */
    public Delivery sendResultImage(String caption, byte[] png, String filename) {
        if (!properties.resultsChannelConfigured()) {
            return Delivery.failed("Kanał wyników Discord nie jest skonfigurowany (DISCORD_RESULTS_CHANNEL_ID)");
        }
        try {
            // Plain servlet multipart (LinkedMultiValueMap) — avoids MultipartBodyBuilder, which
            // drags in reactive-streams (WebFlux) that isn't on the classpath.
            String payloadJson = objectMapper.writeValueAsString(
                    new MessageRequest(caption, new AllowedMentions(List.of(), List.of())));
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

            ByteArrayResource fileResource = new ByteArrayResource(png) {
                @Override public String getFilename() { return filename; }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.IMAGE_PNG);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("payload_json", new HttpEntity<>(payloadJson, jsonHeaders));
            body.add("files[0]", new HttpEntity<>(fileResource, fileHeaders));

            client.post().uri(BASE + "/channels/" + properties.getResultsChannelId() + "/messages")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve().toBodilessEntity();
            return Delivery.resultSent(properties.getResultsChannelId());
        } catch (JsonProcessingException ex) {
            return Delivery.failed("Nie udało się przygotować wiadomości Discord");
        } catch (RestClientResponseException ex) {
            log.warn("Discord result upload failed with HTTP {}: {}", ex.getStatusCode().value(),
                    responseExcerpt(ex));
            return Delivery.failed(explainResultError(ex));
        } catch (RestClientException ex) {
            log.warn("Discord result upload failed before a response was received", ex);
            return Delivery.failed("Nie udało się połączyć z Discordem (timeout/sieć)");
        }
    }

    /** Posts a patch-notes image (with caption + @everyone) to the dedicated patch-notes channel. */
    public Delivery sendPatchNotesImage(String caption, byte[] png, String filename) {
        if (!properties.patchNotesChannelConfigured()) {
            return Delivery.failed("Kanał patch notes Discord nie jest skonfigurowany (DISCORD_PATCH_CHANNEL_ID)");
        }
        try {
            String payloadJson = objectMapper.writeValueAsString(
                    new MessageRequest(caption, new AllowedMentions(List.of("everyone"), List.of())));
            HttpHeaders jsonHeaders = new HttpHeaders();
            jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
            ByteArrayResource fileResource = new ByteArrayResource(png) {
                @Override public String getFilename() { return filename; }
            };
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.IMAGE_PNG);
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("payload_json", new HttpEntity<>(payloadJson, jsonHeaders));
            body.add("files[0]", new HttpEntity<>(fileResource, fileHeaders));

            client.post().uri(BASE + "/channels/" + properties.patchNotesChannel() + "/messages")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve().toBodilessEntity();
            return Delivery.resultSent(properties.patchNotesChannel());
        } catch (JsonProcessingException ex) {
            return Delivery.failed("Nie udało się przygotować wiadomości Discord");
        } catch (RestClientResponseException ex) {
            log.warn("Discord patch-notes upload failed with HTTP {}: {}", ex.getStatusCode().value(),
                    responseExcerpt(ex));
            return Delivery.failed(explainResultError(ex));
        } catch (RestClientException ex) {
            log.warn("Discord patch-notes upload failed before a response was received", ex);
            return Delivery.failed("Nie udało się połączyć z Discordem (timeout/sieć)");
        }
    }

    /** Posts a plain announcement (with @everyone) to the announcements channel. */
    public Delivery sendAnnouncement(String content) {
        if (!properties.announceChannelConfigured()) {
            return Delivery.failed("Brak kanału ogłoszeń Discord (DISCORD_ANNOUNCE_CHANNEL_ID / DISCORD_RESULTS_CHANNEL_ID)");
        }
        try {
            client.post().uri(BASE + "/channels/" + properties.announceChannel() + "/messages")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .body(new MessageRequest(content, new AllowedMentions(List.of("everyone"), List.of())))
                    .retrieve().toBodilessEntity();
            return Delivery.resultSent(properties.announceChannel());
        } catch (RestClientResponseException ex) {
            int s = ex.getStatusCode().value();
            String msg = (s == 401) ? "Nieprawidłowy token bota"
                    : (s == 403) ? "Bot nie ma uprawnień do pisania na kanale ogłoszeń"
                    : "Discord odrzucił ogłoszenie (HTTP " + s + ")";
            return Delivery.failed(msg);
        } catch (RestClientException ex) {
            return Delivery.failed("Brak połączenia z Discordem");
        }
    }

    /**
     * Posts an RSVP vote message (Tak/Nie/Może buttons) for a planned match to the vote channel.
     * The button custom_id carries the planned-match id — the gateway listener
     * ({@link DiscordRsvpGateway}) turns clicks into RSVP votes of linked players.
     */
    public Delivery sendRsvpMessage(String content, java.util.UUID plannedMatchId) {
        if (!properties.voteChannelConfigured()) {
            return Delivery.failed("Kanał głosowań Discord nie jest skonfigurowany (DISCORD_VOTE_CHANNEL_ID)");
        }
        try {
            ComponentRow row = new ComponentRow(1, List.of(
                    new ButtonComponent(2, 3, "Będę", rsvpId(plannedMatchId, "YES"), new Emoji("✅")),
                    new ButtonComponent(2, 4, "Nie będę", rsvpId(plannedMatchId, "NO"), new Emoji("❌")),
                    new ButtonComponent(2, 2, "Może", rsvpId(plannedMatchId, "MAYBE"), new Emoji("❓"))));
            client.post().uri(BASE + "/channels/" + properties.voteChannel() + "/messages")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .body(new VoteMessageRequest(content,
                            new AllowedMentions(List.of(), List.of()), List.of(row)))
                    .retrieve().toBodilessEntity();
            return Delivery.resultSent(properties.voteChannel());
        } catch (RestClientResponseException ex) {
            int s = ex.getStatusCode().value();
            String msg = (s == 401) ? "Nieprawidłowy token bota"
                    : (s == 403) ? "Bot nie ma uprawnień do pisania na kanale głosowań"
                    : (s == 404) ? "Kanał głosowań nie istnieje — sprawdź DISCORD_VOTE_CHANNEL_ID"
                    : "Discord odrzucił wiadomość głosowania (HTTP " + s + ")";
            return Delivery.failed(msg);
        } catch (RestClientException ex) {
            return Delivery.failed("Brak połączenia z Discordem");
        }
    }

    /** custom_id of an RSVP vote button: "rsvp:{plannedMatchId}:{YES|NO|MAYBE}". */
    public static String rsvpId(java.util.UUID plannedMatchId, String response) {
        return "rsvp:" + plannedMatchId + ":" + response;
    }

    public Delivery sendLoginMessage(String discordName, String knownUserId, String message) {
        if (!properties.configured()) {
            return Delivery.failed("Bot Discord nie jest skonfigurowany (DISCORD_BOT_TOKEN/DISCORD_GUILD_ID)");
        }
        try {
            String userId = knownUserId != null ? knownUserId : resolveUserId(discordName);
            DmChannel channel = client.post().uri(BASE + "/users/@me/channels")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .body(new CreateDmRequest(userId)).retrieve().body(DmChannel.class);
            if (channel == null || channel.id() == null) {
                return Delivery.failed("Discord nie zwrócił kanału DM");
            }
            client.post().uri(BASE + "/channels/" + channel.id() + "/messages")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .body(new MessageRequest(message, new AllowedMentions(List.of(), List.of())))
                    .retrieve().toBodilessEntity();
            return Delivery.sent(userId);
        } catch (DiscordLookupException ex) {
            return Delivery.failed(ex.getMessage());
        } catch (RestClientResponseException ex) {
            log.warn("Discord DM failed with HTTP {}: {}", ex.getStatusCode().value(),
                    responseExcerpt(ex));
            return Delivery.failed(explainDmError(ex));
        } catch (RestClientException ex) {
            log.warn("Discord DM failed before a response was received", ex);
            return Delivery.failed("Nie udało się połączyć z Discordem (timeout/sieć)");
        }
    }

    private static String explainResultError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String body = ex.getResponseBodyAsString();
        if (status == 401) return "Discord odrzucił token bota (HTTP 401) — ustaw poprawny DISCORD_BOT_TOKEN.";
        if (status == 403) return "Bot nie może pisać ani dodawać plików na kanale wyników (HTTP 403).";
        if (status == 404 || body.contains("10003")) {
            return "Kanał wyników Discord nie istnieje albo bot go nie widzi — sprawdź DISCORD_RESULTS_CHANNEL_ID.";
        }
        if (status == 413) return "Wygenerowany obraz wyniku przekracza limit plików serwera Discord.";
        if (status == 429) return "Discord ograniczył liczbę wiadomości — spróbuj ponownie za chwilę.";
        return "Discord odrzucił obraz wyniku (HTTP " + status + ").";
    }

    private static String responseExcerpt(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString().replaceAll("[\\r\\n]+", " ");
        return body.substring(0, Math.min(body.length(), 500));
    }

    /** Turns a Discord API error into an actionable message for the admin. */
    private static String explainDmError(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        int status = ex.getStatusCode().value();
        if (body.contains("50007")) {
            return "Discord blokuje DM do tej osoby — musi mieć włączone „Wiadomości od członków serwera”"
                    + " (Ustawienia prywatności serwera) i być na wspólnym serwerze z botem. Użyj przycisku"
                    + " kopiowania i wyślij dane ręcznie.";
        }
        if (status == 404) {
            return "Nieznany użytkownik Discord — sprawdź nazwę albo podaj numeryczny Discord User ID.";
        }
        if (status == 401 || status == 403) {
            return "Bot nie ma uprawnień (HTTP " + status + ") — sprawdź token bota i obecność na serwerze.";
        }
        return "Discord odrzucił wiadomość DM (HTTP " + status + ").";
    }

    private String resolveUserId(String rawName) {
        String query = normalize(rawName);
        if (query.matches("\\d{15,22}")) return query; // already a numeric user id — most reliable
        List<GuildMember> members;
        try {
            members = client.get()
                    .uri(builder -> builder.scheme("https").host("discord.com")
                            .path("/api/v10/guilds/").pathSegment(properties.getGuildId())
                            .path("/members/search").queryParam("query", query)
                            .queryParam("limit", 20).build())
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .retrieve().body(new ParameterizedTypeReference<List<GuildMember>>() {});
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 403) {
                throw new DiscordLookupException(
                        "Wyszukiwanie po nazwie wymaga włączenia „Server Members Intent” w portalu Discord"
                        + " (Bot → Privileged Gateway Intents). Alternatywnie podaj numeryczny Discord User ID.");
            }
            throw new DiscordLookupException("Discord nie pozwolił wyszukać osoby (HTTP "
                    + ex.getStatusCode().value() + "); podaj numeryczny Discord User ID.");
        }
        List<GuildMember> exact = (members == null ? List.<GuildMember>of() : members).stream()
                .filter(member -> matches(member, query)).toList();
        if (exact.isEmpty()) {
            throw new DiscordLookupException("Nie znaleziono dokładnie takiej osoby na serwerze Discord");
        }
        if (exact.size() > 1) {
            throw new DiscordLookupException("Nazwa Discord jest niejednoznaczna; podaj numeryczny Discord User ID");
        }
        return exact.getFirst().user().id();
    }

    private static boolean matches(GuildMember member, String query) {
        if (member == null || member.user() == null) return false;
        return same(member.nick(), query) || same(member.user().username(), query)
                || same(member.user().globalName(), query);
    }

    private static boolean same(String value, String expected) {
        return value != null && normalize(value).equals(normalize(expected));
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim();
        while (result.startsWith("@")) result = result.substring(1);
        return result.toLowerCase(Locale.ROOT);
    }

    public record Delivery(boolean sent, String discordUserId, String message) {
        static Delivery sent(String userId) {
            return new Delivery(true, userId, "Dane logowania wysłane na Discord DM");
        }
        static Delivery resultSent(String channelId) {
            return new Delivery(true, channelId, "Wynik wysłany na kanał Discord");
        }
        static Delivery failed(String message) {
            return new Delivery(false, null, message);
        }
    }
    public record GuildMember(String nick, DiscordUser user) {}
    public record DiscordUser(String id, String username,
                              @JsonProperty("global_name") String globalName) {}
    public record CreateDmRequest(@JsonProperty("recipient_id") String recipientId) {}
    public record DmChannel(String id) {}
    public record AllowedMentions(List<String> parse, List<String> users) {}
    public record MessageRequest(String content,
                                 @JsonProperty("allowed_mentions") AllowedMentions allowedMentions) {}
    public record VoteMessageRequest(String content,
                                     @JsonProperty("allowed_mentions") AllowedMentions allowedMentions,
                                     List<ComponentRow> components) {}
    public record ComponentRow(int type, List<ButtonComponent> components) {}
    public record ButtonComponent(int type, int style, String label,
                                  @JsonProperty("custom_id") String customId, Emoji emoji) {}
    public record Emoji(String name) {}

    private static class DiscordLookupException extends RuntimeException {
        DiscordLookupException(String message) { super(message); }
    }
}

