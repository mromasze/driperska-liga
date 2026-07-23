package pl.romcio.driperska.patchnotes;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Renders a patch-notes card (version, title, bullet list) as a PNG, styled like the result card. */
@Component
public class PatchNotesImageGenerator {

    private static final int W = 1000;
    private static final int PAD = 56;
    private static final int TEXT_W = W - PAD * 2;

    private static final Color BG = new Color(0x0B1118);
    private static final Color PANEL = new Color(0x121C2B);
    private static final Color GOLD = new Color(0xE4B84A);
    private static final Color CYAN = new Color(0x5BD5FF);
    private static final Color TEXT = new Color(0xE6EDF5);
    private static final Color TEXT_LO = new Color(0x9FB0C3);

    public byte[] render(String version, String title, String date, List<String> changes) {
        Font brandFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 38);
        Font metaFont = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
        Font bulletFont = new Font(Font.SANS_SERIF, Font.PLAIN, 20);

        // First pass on a throwaway graphics to measure wrapped bullet lines and compute height.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(bulletFont);
        FontMetrics bfm = pg.getFontMetrics();
        int bulletIndent = 30;
        List<List<String>> wrapped = new ArrayList<>();
        for (String change : changes) {
            wrapped.add(wrap(change, bfm, TEXT_W - bulletIndent));
        }
        pg.dispose();

        int lineH = bfm.getHeight() + 4;
        int headerBottom = PAD + 30 + 54; // brand row + title
        int bodyTop = headerBottom + 40;
        int bulletsHeight = 0;
        for (List<String> lines : wrapped) bulletsHeight += lines.size() * lineH + 12;
        int height = bodyTop + bulletsHeight + PAD;

        BufferedImage img = new BufferedImage(W, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, W, height);
        g.setColor(PANEL);
        g.fillRoundRect(PAD / 2, PAD / 2, W - PAD, height - PAD, 28, 28);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(PAD / 2, PAD / 2, W - PAD, height - PAD, 28, 28);

        // Brand row + version pill
        g.setFont(brandFont);
        g.setColor(GOLD);
        g.drawString("DRIPERSKA LIGA · PATCH NOTES", PAD, PAD + 20);
        g.setFont(metaFont);
        FontMetrics mfm = g.getFontMetrics();
        String pill = version;
        int pillW = mfm.stringWidth(pill) + 28;
        int pillX = W - PAD - pillW;
        g.setColor(new Color(0x1E2E45));
        g.fillRoundRect(pillX, PAD, pillW, 30, 16, 16);
        g.setColor(CYAN);
        g.drawString(pill, pillX + 14, PAD + 21);

        // Title + date
        g.setFont(titleFont);
        g.setColor(TEXT);
        g.drawString(title, PAD, PAD + 66);
        if (date != null && !date.isBlank()) {
            g.setFont(metaFont);
            g.setColor(TEXT_LO);
            g.drawString(date, PAD, PAD + 66 + 26);
        }

        // Bullets
        g.setFont(bulletFont);
        int y = bodyTop + bfm.getAscent();
        for (List<String> lines : wrapped) {
            g.setColor(GOLD);
            g.fillOval(PAD + 2, y - bfm.getAscent() + 6, 8, 8);
            g.setColor(TEXT);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(lines.get(i), PAD + bulletIndent, y);
                y += lineH;
            }
            y += 12;
        }
        g.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Nie udało się wygenerować obrazu patch notes", ex);
        }
    }

    private static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.isEmpty() ? List.of("") : lines;
    }
}
