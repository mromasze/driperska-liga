package pl.romcio.driperska.integration.turnstile;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Verifies Cloudflare Turnstile tokens server-side. */
@Service
public class TurnstileService {
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final TurnstileProperties properties;
    private final RestClient client;

    public TurnstileService(TurnstileProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.client = RestClient.builder().requestFactory(f).build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Returns true when the token is valid (or when Turnstile is disabled). */
    public boolean verify(String token, String remoteIp) {
        if (!properties.enabled()) {
            return true;
        }
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", properties.getSecret());
            form.add("response", token);
            if (StringUtils.hasText(remoteIp)) {
                form.add("remoteip", remoteIp);
            }
            JsonNode result = client.post().uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            return result != null && result.path("success").asBoolean(false);
        } catch (RestClientException ex) {
            return false;
        }
    }
}
