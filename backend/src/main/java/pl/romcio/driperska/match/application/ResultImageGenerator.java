package pl.romcio.driperska.match.application;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Renders a match scoreboard to a PNG (nicknames + personal stats) for sharing on Discord. */
@Component
public class ResultImageGenerator {

    public record Row(String nick, String role, String champion, int kills, int deaths, int assists,
                      int cs, Integer pr, boolean mvp) {}
    public record Card(String title, String subtitle, boolean blueWon, boolean decided,
                       int blueKills, int redKills, List<Row> blue, List<Row> red) {}

    private static final Color BG = new Color(0x0E, 0x0F, 0x13);
    private static final Color PANEL = new Color(0x16, 0x18, 0x1F);
    private static final Color LINE = new Color(0x2A, 0x2C, 0x36);
    private static final Color TEXT = new Color(0xE8, 0xE8, 0xEA);
    private static final Color MUTED = new Color(0x9A, 0x9A, 0xA0);
    private static final Color BLUE = new Color(0x4A, 0x90, 0xD9);
    private static final Color RED = new Color(0xD9, 0x57, 0x57);
    private static final Color GOLD = new Color(0xD4, 0xAF, 0x37);
    private static final Color LOSS = new Color(0xC0, 0x5A, 0x5A);

    private static final int W = 980;
    private static final int PAD = 32;
    private static final int HEADER = 108;
    private static final int TEAM_HEAD = 46;
    private static final int ROW = 58;

    public byte[] render(Card card) {
        int rows = Math.max(card.blue().size(), card.red().size());
        int panelH = TEAM_HEAD + rows * ROW + 16;
        int height = HEADER + panelH + PAD;
        BufferedImage img = new BufferedImage(W, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, W, height);

        // Header
        g.setColor(GOLD);
        g.setFont(font(Font.BOLD, 30));
        g.drawString(card.title(), PAD, 52);
        g.setColor(MUTED);
        g.setFont(font(Font.PLAIN, 16));
        g.drawString(card.subtitle(), PAD, 80);
        // Big score in header, right-aligned
        String score = card.blueKills() + " : " + card.redKills();
        g.setFont(font(Font.BOLD, 34));
        g.setColor(TEXT);
        int sw = g.getFontMetrics().stringWidth(score);
        g.drawString(score, W - PAD - sw, 62);

        int colW = (W - 3 * PAD) / 2;
        drawTeam(g, PAD, HEADER, colW, panelH, "NIEBIESCY", BLUE, card.decided() && card.blueWon(),
                card.decided(), card.blue());
        drawTeam(g, PAD * 2 + colW, HEADER, colW, panelH, "CZERWONI", RED, card.decided() && !card.blueWon(),
                card.decided(), card.red());

        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", out)) {
                throw new IllegalStateException("Brak kodera PNG w środowisku Java");
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void drawTeam(Graphics2D g, int x, int y, int w, int h, String label, Color accent,
                          boolean won, boolean decided, List<Row> rows) {
        g.setColor(PANEL);
        g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(accent.darker());
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 16, 16);

        // team header
        g.setColor(accent);
        g.setFont(font(Font.BOLD, 19));
        g.drawString(label, x + 18, y + 30);
        if (decided) {
            g.setFont(font(Font.BOLD, 13));
            g.setColor(won ? GOLD : MUTED);
            String tag = won ? "WYGRANA" : "PRZEGRANA";
            g.drawString(tag, x + 18 + g.getFontMetrics(font(Font.BOLD, 19)).stringWidth(label) + 14, y + 29);
        }
        g.setColor(LINE);
        g.drawLine(x + 14, y + TEAM_HEAD - 4, x + w - 14, y + TEAM_HEAD - 4);

        int ry = y + TEAM_HEAD + 8;
        for (Row r : rows) {
            drawRow(g, x + 16, ry, w - 32, r, accent);
            ry += ROW;
        }
    }

    private void drawRow(Graphics2D g, int x, int y, int w, Row r, Color accent) {
        // nick (MVP highlighted in gold with a text tag — emoji don't render in the base font)
        g.setFont(font(Font.BOLD, 17));
        g.setColor(r.mvp() ? GOLD : TEXT);
        String nick = safe(r.nick());
        g.drawString(nick, x, y + 20);
        if (r.mvp()) {
            int nx = x + g.getFontMetrics().stringWidth(nick) + 8;
            g.setFont(font(Font.BOLD, 11));
            g.setColor(GOLD);
            g.drawString("MVP", nx, y + 19);
        }
        // role · champion
        g.setColor(MUTED);
        g.setFont(font(Font.PLAIN, 13));
        g.drawString(safe(r.role()) + " · " + safe(r.champion()), x, y + 40);

        // PR badge far right
        int rightX = x + w;
        if (r.pr() != null) {
            String pr = String.valueOf(r.pr());
            g.setFont(font(Font.BOLD, 20));
            g.setColor(prColor(r.pr()));
            int pw = g.getFontMetrics().stringWidth(pr);
            g.drawString(pr, rightX - pw, y + 24);
            g.setColor(MUTED);
            g.setFont(font(Font.PLAIN, 10));
            String lbl = "PR";
            g.drawString(lbl, rightX - g.getFontMetrics().stringWidth(lbl), y + 40);
        }

        // KDA + CS in the middle-right
        String kda = r.kills() + " / " + r.deaths() + " / " + r.assists();
        g.setFont(font(Font.BOLD, 16));
        g.setColor(TEXT);
        int kdaW = g.getFontMetrics().stringWidth(kda);
        int kdaX = rightX - 92 - kdaW;
        g.drawString(kda, kdaX, y + 20);
        g.setColor(MUTED);
        g.setFont(font(Font.PLAIN, 12));
        String cs = r.cs() + " CS";
        g.drawString(cs, rightX - 92 - g.getFontMetrics().stringWidth(cs), y + 40);
    }

    private static Color prColor(int pr) {
        if (pr >= 75) return GOLD;
        if (pr >= 55) return new Color(0x6FCF97);
        if (pr >= 40) return TEXT;
        return LOSS;
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
    }

    private static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }
}
