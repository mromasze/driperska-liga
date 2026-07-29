package pl.romcio.driperska.match.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.DataDragonClient;

/**
 * Builds labelled champion portrait atlases used as visual references by the OCR model.
 *
 * <p>For a match that went through the internal draft the champions are already known and this is
 * belt-and-braces. For a manually recorded match it is the <b>only</b> way the champion column can be
 * filled: nothing in a LoL end-game screenshot spells the champion out, so the model has to match a
 * ~32px row portrait against a reference.
 *
 * <p>Which makes portrait size the whole ballgame. Vision models resize every input image to a fixed
 * budget (roughly a 1024px longest edge), so cramming 96 portraits onto one sheet left each one about
 * 50px of the model's attention — a needle-in-a-haystack comparison it has no chance at. Sheets are
 * now sized so that the sheet itself lands inside that budget and each portrait survives at close to
 * its native Data Dragon resolution. Fewer portraits per sheet, more sheets, each actually legible.
 */
@Service
public class ChampionReferenceAtlasService {

    private static final Logger log = LoggerFactory.getLogger(ChampionReferenceAtlasService.class);
    /** 8 x 5 at these cell sizes keeps a sheet at ~1024x820 — inside a vision model's resize budget. */
    private static final int COLUMNS = 8;
    private static final int ROWS = 5;
    private static final int CELL_WIDTH = 128;
    private static final int CELL_HEIGHT = 150;
    /** Data Dragon serves 120px icons; staying near native avoids throwing detail away twice. */
    private static final int ICON_SIZE = 104;
    private static final int HEADER_HEIGHT = 44;
    private static final int PER_SHEET = COLUMNS * ROWS;

    private final DataDragonClient dataDragon;
    private volatile CachedAtlas cached;

    public ChampionReferenceAtlasService(DataDragonClient dataDragon) {
        this.dataDragon = dataDragon;
    }

    public Atlas atlasFor(List<Champion> champions) {
        List<Champion> sorted = champions.stream()
                .filter(c -> c.getSlug() != null && c.getDdragonVersion() != null)
                .sorted(Comparator.comparing(Champion::getName))
                .toList();
        if (sorted.isEmpty()) return Atlas.empty();

        String key = sorted.stream()
                .map(c -> c.getId() + ":" + c.getSlug() + ":" + c.getDdragonVersion())
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
        CachedAtlas current = cached;
        if (current != null && current.key().equals(key)) return current.atlas();

        synchronized (this) {
            current = cached;
            if (current != null && current.key().equals(key)) return current.atlas();
            Atlas generated = generate(sorted);
            if (!generated.images().isEmpty()) cached = new CachedAtlas(key, generated);
            return generated;
        }
    }

    private Atlas generate(List<Champion> champions) {
        List<Portrait> portraits = new ArrayList<>();
        int failures = 0;
        for (Champion champion : champions) {
            BufferedImage image = loadPortrait(champion);
            if (image != null) {
                portraits.add(new Portrait(champion.getName(), image));
                continue;
            }
            failures++;
            // Push on through individual failures. The old rule — stop after three in a row, or after
            // any failure while the list was still empty — meant one champion whose slug 404s (a rename
            // between patches does that permanently) truncated the atlas to whatever came before it
            // alphabetically, or killed it outright. A handful of missing portraits is survivable; only
            // Data Dragon being wholly unreachable is not.
            if (portraits.isEmpty() && failures >= 5) {
                log.warn("Data Dragon unreachable after {} attempts; champion reference atlas skipped", failures);
                break;
            }
        }
        if (portraits.isEmpty()) {
            log.warn("Champion reference atlas is unavailable; OCR continues with text context only");
            return Atlas.empty();
        }
        if (failures > 0) {
            log.warn("Champion reference atlas is missing {} of {} portraits", failures, champions.size());
        }

        List<String> images = new ArrayList<>();
        int sheetCount = (portraits.size() + PER_SHEET - 1) / PER_SHEET;
        for (int from = 0, sheet = 1; from < portraits.size(); from += PER_SHEET, sheet++) {
            int to = Math.min(portraits.size(), from + PER_SHEET);
            images.add(renderSheet(portraits.subList(from, to), sheet, sheetCount));
        }
        String version = champions.getFirst().getDdragonVersion();
        log.info("Prepared {} champion reference atlas image(s) with {} portraits ({})",
                images.size(), portraits.size(), version);
        return new Atlas(List.copyOf(images), portraits.size(), version);
    }

    /** One retry: Data Dragon occasionally drops a connection, and a whole atlas is worth a second go. */
    private BufferedImage loadPortrait(Champion champion) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                byte[] bytes = dataDragon.fetchChampionIcon(champion.getDdragonVersion(), champion.getSlug());
                BufferedImage image = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null) {
                    return image;
                }
            } catch (Exception ex) {
                log.debug("Could not load champion portrait {} (attempt {}): {}",
                        champion.getSlug(), attempt, ex.getMessage());
            }
        }
        return null;
    }

    private static String renderSheet(List<Portrait> portraits, int sheet, int sheetCount) {
        int rows = (portraits.size() + COLUMNS - 1) / COLUMNS;
        BufferedImage canvas = new BufferedImage(
                COLUMNS * CELL_WIDTH, HEADER_HEIGHT + rows * CELL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(10, 16, 26));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setColor(new Color(91, 213, 255));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        g.drawString("CHAMPION REFERENCE ATLAS " + sheet + "/" + sheetCount
                + " - NOT A MATCH SCREENSHOT", 14, 28);

        for (int i = 0; i < portraits.size(); i++) {
            Portrait portrait = portraits.get(i);
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            int x = column * CELL_WIDTH;
            int y = HEADER_HEIGHT + row * CELL_HEIGHT;
            g.setColor(((row + column) & 1) == 0 ? new Color(18, 28, 43) : new Color(22, 34, 51));
            g.fillRect(x, y, CELL_WIDTH, CELL_HEIGHT);
            int imageX = x + (CELL_WIDTH - ICON_SIZE) / 2;
            g.drawImage(portrait.image(), imageX, y + 6, ICON_SIZE, ICON_SIZE, null);
            g.setColor(Color.WHITE);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 14);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            // Shrink rather than clip: a half-written "Nunu & Willu" is a name the model cannot return.
            for (float size = 14f; size > 8f && metrics.stringWidth(portrait.name()) > CELL_WIDTH - 6; size -= 1f) {
                font = font.deriveFont(size - 1f);
                g.setFont(font);
                metrics = g.getFontMetrics();
            }
            int textX = x + Math.max(2, (CELL_WIDTH - metrics.stringWidth(portrait.name())) / 2);
            g.drawString(portrait.name(), textX, y + ICON_SIZE + 28);
        }
        g.dispose();

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            // High quality: JPEG ringing around small icons and 14px labels is exactly the detail the
            // model needs to tell two similar portraits apart.
            params.setCompressionQuality(0.95f);
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(canvas, null, null), params);
            } finally {
                writer.dispose();
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not render champion reference atlas", ex);
        }
    }

    public record Atlas(List<String> images, int portraitCount, String version) {
        static Atlas empty() {
            return new Atlas(List.of(), 0, null);
        }
    }

    private record Portrait(String name, BufferedImage image) {}
    private record CachedAtlas(String key, Atlas atlas) {}
}
