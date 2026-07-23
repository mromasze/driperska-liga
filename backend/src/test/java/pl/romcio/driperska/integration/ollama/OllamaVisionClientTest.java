package pl.romcio.driperska.integration.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaVisionClientTest {

    @Test
    void extractsJsonFromMarkdownFence() {
        String response = """
                ```json
                {"players":[]}
                ```
                """;

        assertThat(OllamaVisionClient.extractJson(response)).isEqualTo("{\"players\":[]}");
    }

    @Test
    void extractsJsonSurroundedByModelCommentary() {
        String response = "Result: {\"players\":[]} End.";

        assertThat(OllamaVisionClient.extractJson(response)).isEqualTo("{\"players\":[]}");
    }
}
