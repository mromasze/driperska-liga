package pl.romcio.driperska.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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

    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

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
        Map<String, Object> body = Map.of(
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
                    .uri(properties.getBaseUrl().replaceAll("/$", "") + "/api/chat")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ExternalServiceException("Ollama", "model nie zwrócił treści");
            }
            return objectMapper.readTree(content);
        } catch (RestClientResponseException ex) {
            String hint = ex.getStatusCode().value() == 404
                    ? "model '" + properties.getVisionModel() + "' niedostępny na koncie Ollama — ustaw OLLAMA_VISION_MODEL"
                    : ex.getStatusCode().value() == 401 ? "nieprawidłowy OLLAMA_API_KEY"
                    : "HTTP " + ex.getStatusCode().value();
            throw new ExternalServiceException("Ollama", hint);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Ollama", "brak połączenia lub przekroczono limit czasu");
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ExternalServiceException("Ollama", "nie udało się odczytać odpowiedzi modelu");
        }
    }
}
