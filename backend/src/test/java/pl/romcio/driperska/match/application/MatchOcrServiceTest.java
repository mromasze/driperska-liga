package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MatchOcrServiceTest {

    @Test
    void systemPromptSeparatesReferenceAtlasFromMatchEvidence() {
        assertThat(MatchOcrService.systemPrompt())
                .contains("CHAMPION REFERENCE ATLASES")
                .contains("Never treat atlas labels")
                .contains("Output raw JSON only");
    }
    @Test
    void downscalesOversizedScreenshotToTheDimensionCap() throws Exception {
        // Wider than the 2560px cap → downscaled to 2560x1440 and re-encoded as JPEG.
        BufferedImage screenshot = new BufferedImage(3200, 1800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = screenshot.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, screenshot.getWidth(), screenshot.getHeight());
        graphics.dispose();

        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(screenshot, "png", original);

        byte[] encoded = Base64.getDecoder().decode(
                MatchOcrService.downscaleToBase64(original.toByteArray()));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(encoded));

        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(2560);
        assertThat(result.getHeight()).isEqualTo(1440);
        assertThat(encoded[0]).isEqualTo((byte) 0xff);
        assertThat(encoded[1]).isEqualTo((byte) 0xd8);
    }

    @Test
    void sendsNormalScreenshotUntouchedToPreservePortraitDetail() throws Exception {
        // A ~1080p PNG under the size threshold must be passed through as-is (no JPEG re-encode).
        BufferedImage screenshot = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        ImageIO.write(screenshot, "png", original);
        byte[] pngBytes = original.toByteArray();

        byte[] encoded = Base64.getDecoder().decode(MatchOcrService.downscaleToBase64(pngBytes));

        assertThat(encoded).isEqualTo(pngBytes); // identical bytes → sent untouched
    }
}
