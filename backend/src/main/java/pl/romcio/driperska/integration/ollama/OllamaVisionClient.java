package pl.romcio.driperska.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
    /** Rebuilt whenever the configured timeout changes, since it is baked into the request factory. */
    private volatile RestClient client;
    private volatile int clientTimeoutSeconds = -1;

    /** Host root for the native Ollama API — tolerates a base URL ending with the OpenAI-compat {@code /v1}. */
    public static String nativeBase(String baseUrl) {
        if (baseUrl == null) return "https://ollama.com";
        return baseUrl.replaceAll("/+$", "").replaceAll("/v1$", "");
    }

    public OllamaVisionClient(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * HTTP client for the currently configured timeout. The admin panel can change the timeout at
     * runtime, so it cannot be captured once at construction.
     */
    private RestClient http() {
        int timeout = Math.max(30, properties.getTimeoutSeconds());
        RestClient current = client;
        if (current != null && clientTimeoutSeconds == timeout) {
            return current;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeout).toMillis());
        RestClient rebuilt = RestClient.builder().requestFactory(factory).build();
        client = rebuilt;
        clientTimeoutSeconds = timeout;
        return rebuilt;
    }

    /**
     * Sends a prompt plus one or more base64-encoded images and a JSON schema; returns the parsed
     * JSON object the model produced.
     *
     * @param systemPrompt instructions that define the model's role and parsing rules
     * @param base64Images raw base64 (no {@code data:} prefix)
     * @param schema       JSON schema object the response must conform to
     */
    public ChatResult chatJson(String systemPrompt, String prompt, List<String> base64Images,
                               Map<String, Object> schema) {
        if (!properties.configured()) {
            throw new ExternalServiceException("Ollama",
                    "brak konfiguracji (OLLAMA_API_KEY / OLLAMA_VISION_MODEL)");
        }
        Map<String, Object> requestBody = Map.of(
                "model", properties.getVisionModel(),
                "stream", false,
                // Disable chain-of-thought for "thinking" models (minimax, gemma, …) — for OCR it
                // only adds minutes of latency (and can blow the read timeout) with no accuracy gain.
                "think", false,
                "format", schema,
                "options", Map.of("temperature", 0),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt),
                        Map.of("role", "user",
                                "content", prompt,
                                "images", base64Images)));
        long startedAt = System.nanoTime();
        try {
            String raw = http().post()
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
            JsonNode data = objectMapper.readTree(extractJson(content));
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            return new ChatResult(data, content, properties.getVisionModel(), elapsedMillis,
                    base64Images.size());
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
     * Model names available to the configured account, newest first as Ollama returns them. Used by
     * the admin panel to offer a picker instead of a free-text field.
     */
    public List<String> listModels() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new ExternalServiceException("Ollama", "brak OLLAMA_API_KEY");
        }
        try {
            String raw = http().get()
                    .uri(nativeBase(properties.getBaseUrl()) + "/api/tags")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .retrieve()
                    .body(String.class);
            JsonNode models = objectMapper.readTree(raw == null ? "{}" : raw).path("models");
            List<String> names = new ArrayList<>();
            for (JsonNode model : models) {
                String name = model.path("name").asText(model.path("model").asText(""));
                if (!name.isBlank() && !names.contains(name)) {
                    names.add(name);
                }
            }
            return names;
        } catch (RestClientResponseException ex) {
            throw new ExternalServiceException("Ollama", ex.getStatusCode().value() == 401
                    ? "nieprawidłowy OLLAMA_API_KEY"
                    : "lista modeli zwróciła HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Ollama", "brak połączenia lub przekroczono limit czasu");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ExternalServiceException("Ollama", "nie udało się odczytać listy modeli");
        }
    }

    /**
     * Sends a one-line prompt to {@code model} and reports how it went. Lets an admin try a model
     * before switching the OCR pipeline over to it.
     */
    public PingResult ping(String model, String prompt) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            return new PingResult(false, model, 0, null, "Brak OLLAMA_API_KEY");
        }
        if (!StringUtils.hasText(model)) {
            return new PingResult(false, model, 0, null, "Nie wybrano modelu");
        }
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", false,
                "think", false,
                "options", Map.of("temperature", 0),
                "messages", List.of(Map.of("role", "user", "content",
                        StringUtils.hasText(prompt) ? prompt : "Odpowiedz jednym słowem: OK")));
        long startedAt = System.nanoTime();
        try {
            String raw = http().post()
                    .uri(nativeBase(properties.getBaseUrl()) + "/api/chat")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            long elapsed = (System.nanoTime() - startedAt) / 1_000_000;
            String content = objectMapper.readTree(raw == null ? "{}" : raw)
                    .path("message").path("content").asText("");
            return new PingResult(true, model, elapsed, shorten(content),
                    "Model odpowiedział w " + elapsed + " ms");
        } catch (RestClientResponseException ex) {
            long elapsed = (System.nanoTime() - startedAt) / 1_000_000;
            String hint = switch (ex.getStatusCode().value()) {
                case 404 -> "model '" + model + "' jest niedostępny na tym koncie";
                case 401 -> "nieprawidłowy OLLAMA_API_KEY";
                default -> "Ollama zwróciła HTTP " + ex.getStatusCode().value();
            };
            return new PingResult(false, model, elapsed, null, hint);
        } catch (RestClientException ex) {
            long elapsed = (System.nanoTime() - startedAt) / 1_000_000;
            return new PingResult(false, model, elapsed, null,
                    "Brak połączenia lub przekroczono limit czasu (" + properties.getTimeoutSeconds() + " s)");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return new PingResult(false, model, 0, null, "Nie udało się odczytać odpowiedzi modelu");
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

    public record ChatResult(JsonNode data, String rawContent, String model,
                             long elapsedMillis, int imageCount) {}

    /** Outcome of a one-shot model check from the admin panel. */
    public record PingResult(boolean ok, String model, long elapsedMillis, String reply, String message) {}
}
