package pl.romcio.driperska.integration.ollama;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.common.error.ExternalServiceException;
import pl.romcio.driperska.integration.ollama.OllamaVisionClient.PingResult;

/**
 * AI panel: which model is running, what else the account offers, and a one-click check that a
 * candidate model actually answers before it is made the default for reading screenshots.
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AiAdminController {

    private final OllamaProperties properties;
    private final OllamaVisionClient client;

    public AiAdminController(OllamaProperties properties, OllamaVisionClient client) {
        this.properties = properties;
        this.client = client;
    }

    /**
     * @param models  every model name the account can use, with the active one guaranteed present
     * @param message why the list is empty / partial, when it is
     */
    public record ModelsResponse(List<String> models, String activeModel, boolean ok, String message) {}

    public record TestModelRequest(String model, String prompt) {}

    @GetMapping("/models")
    public ModelsResponse models() {
        String active = properties.getVisionModel();
        try {
            List<String> models = new ArrayList<>(client.listModels());
            // Keep the running model selectable even if the account listing no longer reports it.
            if (active != null && !active.isBlank() && !models.contains(active)) {
                models.add(0, active);
            }
            return new ModelsResponse(models, active, true,
                    models.isEmpty() ? "Konto Ollama nie zwróciło żadnych modeli." : null);
        } catch (ExternalServiceException ex) {
            List<String> fallback = active == null || active.isBlank() ? List.of() : List.of(active);
            return new ModelsResponse(fallback, active, false, ex.getMessage());
        }
    }

    /** Runs a trivial prompt against {@code model} (or the active one) without changing any setting. */
    @PostMapping("/test")
    public PingResult test(@RequestBody(required = false) TestModelRequest request) {
        String model = request == null || request.model() == null || request.model().isBlank()
                ? properties.getVisionModel()
                : request.model().trim();
        return client.ping(model, request == null ? null : request.prompt());
    }
}
