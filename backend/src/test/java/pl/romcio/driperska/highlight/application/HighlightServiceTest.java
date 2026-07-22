package pl.romcio.driperska.highlight.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.player.infra.StorageProperties;

class HighlightServiceTest {
    @TempDir Path tempDir;

    @Test
    void storesListsAndDeletesMp4Clip() {
        HighlightService service = new HighlightService(
                new StorageProperties(tempDir.toString(), "/media"));
        byte[] mp4 = new byte[] {0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        var stored = service.store(new MockMultipartFile(
                "file", "play.mp4", "video/mp4", mp4));

        assertThat(stored.url()).startsWith("/media/highlights/");
        assertThat(service.list()).extracting(HighlightService.HighlightVideo::id)
                .containsExactly(stored.id());

        service.delete(stored.id());
        assertThat(service.list()).isEmpty();
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchExtension() {
        HighlightService service = new HighlightService(
                new StorageProperties(tempDir.toString(), "/media"));

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                "file", "fake.mp4", "video/mp4", new byte[] {1, 2, 3, 4, 5, 6, 7, 8})))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("prawidłowego wideo");
    }
}
