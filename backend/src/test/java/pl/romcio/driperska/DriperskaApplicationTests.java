package pl.romcio.driperska;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long-ok!!",
        "app.ddragon.sync-on-startup=false"
})
class DriperskaApplicationTests {

    @Test
    void contextLoads() {
    }
}
