package pl.romcio.driperska.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.romcio.driperska.common.error.ExternalServiceException;

/** Calls an Ollama vision model to extract structured data from images. */
@Component
public class OllamaVisionClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaVisionClient.class);

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    /** Host root for the native Ollama API — tolerates a base URL ending with the OpenAI-compat {@code /v1}. */
    public static String nativeBase(String baseUrl) {
        if (baseUrl == null) return "https://ollama.com";
        return baseUrl.replaceAll("/+$", "").replaceAll("/v1$", "");
    }

    public OllamaVisionClient(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())).toMillis());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Sends a prompt plus one or more base64-encoded images and a JSON schema; returns the parsed
     * JSON object the model produced.
     *
     * @param base64Images raw base64 (no {@code data:} prefix)
     * @param schema       JSON schema object the response must conform to
     */
    public JsonNode chatJson(String prompt, List<String> base64Images, Map<String, Object> schema) {
        if (!properties.configured()) {
            throw new ExternalServiceException("Ollama",
                    "brak konfiguracji (OLLAMA_API_KEY / OLLAMA_VISION_MODEL)");
        }
        Map<String, Object> requestBody = Map.of(
                "model", properties.getVisionModel(),
                "stream", false,
                "format", schema,
                "options", Map.of("temperature", 0),
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt,
                        "images", base64Images)));
        try {
            String raw = client.post()
                    .uri(nativeBase(properties.getBaseUrl()) + "/api/chat")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ExternalServiceException("Ollama", "model nie zwrócił treści");
            }
            return objectMapper.readTree(extractJson(content));
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("Ollama {} — {}", ex.getStatusCode().value(), body);
            String hint = switch (ex.getStatusCode().value()) {
                case 404 -> "model '" + properties.getVisionModel() + "' niedostępny na koncie Ollama — ustaw OLLAMA_VISION_MODEL";
                case 401 -> "nieprawidłowy OLLAMA_API_KEY";
                case 400 -> body != null && body.contains("too large")
                        ? "zrzut(y) za duże dla Ollama — zmniejsz liczbę/rozmiar screenshotów"
                        : "odrzucone przez Ollama (400): " + shorten(body);
                default -> "HTTP " + ex.getStatusCode().value();
            };
            throw new ExternalServiceException("Ollama", hint);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Ollama", "brak połączenia lub przekroczono limit czasu");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ExternalServiceException("Ollama", "nie udało się odczytać odpowiedzi modelu");
        }
    }

    /**
     * Pulls the JSON object out of a model reply. Some models (that don't honour Ollama's structured
     * {@code format}) wrap the JSON in ```json fences or add prose — take the first balanced {...}.
     */
    static String extractJson(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        return (start >= 0 && end > start) ? s.substring(start, end + 1) : s;
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
