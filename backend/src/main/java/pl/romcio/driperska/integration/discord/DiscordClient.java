package pl.romcio.driperska.integration.discord;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DiscordClient {
    private static final String BASE = "https://discord.com/api/v10";
    private final DiscordProperties properties;
    private final RestClient client = RestClient.builder()
            .requestFactory(timeoutFactory()).build();

    public DiscordClient(DiscordProperties properties) {
        this.properties = properties;
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
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("payload_json",
                    new MessageRequest(caption, new AllowedMentions(List.of(), List.of())),
                    MediaType.APPLICATION_JSON);
            body.part("files[0]", new ByteArrayResource(png)).filename(filename)
                    .contentType(MediaType.IMAGE_PNG);
            client.post().uri(BASE + "/channels/" + properties.getResultsChannelId() + "/messages")
                    .header("Authorization", "Bot " + properties.getBotToken())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve().toBodilessEntity();
            return Delivery.sent(properties.getResultsChannelId());
        } catch (RestClientException ex) {
            return Delivery.failed("Discord odrzucił wysyłkę obrazka; sprawdź token bota i uprawnienia na kanale wyników");
        }
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
            return Delivery.failed(explainDmError(ex));
        } catch (RestClientException ex) {
            return Delivery.failed("Nie udało się połączyć z Discordem (timeout/sieć)");
        }
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

    private static class DiscordLookupException extends RuntimeException {
        DiscordLookupException(String message) { super(message); }
    }
}

