package pl.romcio.driperska.match.application;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Renders a match scoreboard to a PNG designed to mirror the web scoreboard: dark rounded team
 * panels, champion icons, KDA, CS and coloured PR pills.
 */
@Component
public class ResultImageGenerator {

    public record Row(String nick, String role, String champion, String championIconUrl,
                      int kills, int deaths, int assists, int cs, Integer pr, boolean mvp) {}
    public record Card(String title, String subtitle, boolean blueWon, boolean decided,
                       int blueKills, int redKills, List<Row> blue, List<Row> red) {}

    private static final Color BG = new Color(0x0E, 0x0F, 0x13);
    private static final Color PANEL = new Color(0x15, 0x17, 0x1F);
    private static final Color LINE = new Color(0x2A, 0x2C, 0x36);
    private static final Color ICON_BG = new Color(0x24, 0x27, 0x31);
    private static final Color TEXT = new Color(0xEC, 0xEC, 0xF0);
    private static final Color MUTED = new Color(0x8B, 0x8D, 0x98);
    private static final Color BLUE = new Color(0x4A, 0x9E, 0xFF);
    private static final Color RED = new Color(0xFF, 0x5B, 0x6A);
    private static final Color GOLD = new Color(0xE4B84A);
    private static final Color WIN = new Color(0x5FD08A);
    private static final Color DEATH = new Color(0xE0, 0x6A, 0x6A);
    private static final Color PILL_TEAL = new Color(0x1E, 0x6E, 0x66);
    private static final Color PILL_PURPLE = new Color(0x59, 0x4B, 0xA6);
    private static final Color PILL_GRAY = new Color(0x2E, 0x31, 0x3D);

    private static final int W = 960;
    private static final int PAD = 24;
    private static final int HEADER = 96;
    private static final int TEAM_HEAD = 48;
    private static final int ROW = 58;

    // Icons are fetched once and reused; small bounded cache.
    private final Map<String, BufferedImage> iconCache = new ConcurrentHashMap<>();

    public byte[] render(Card card) {
        int rows = Math.max(card.blue().size(), card.red().size());
        int panelH = TEAM_HEAD + rows * ROW + 14;
        int height = HEADER + panelH + PAD;
        BufferedImage img = new BufferedImage(W, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(BG);
        g.fillRect(0, 0, W, height);

        drawHeader(g, card);

        int colW = (W - 3 * PAD) / 2;
        drawTeam(g, PAD, HEADER, colW, panelH, "Niebiescy", BLUE, card.decided() && card.blueWon(),
                card.decided(), card.blueKills(), card.blue());
        drawTeam(g, PAD * 2 + colW, HEADER, colW, panelH, "Czerwoni", RED, card.decided() && !card.blueWon(),
                card.decided(), card.redKills(), card.red());

        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void drawHeader(Graphics2D g, Card card) {
        int cx = W / 2;
        Color dim = new Color(0xC9, 0xCC, 0xD4);

        // title at the very top, centred and dim
        g.setFont(font(Font.BOLD, 12));
        g.setColor(new Color(0x63, 0x66, 0x73));
        centered(g, card.title(), cx, 20);

        // centre: clock + VS (fits in the gap between the two side blocks)
        g.setFont(font(Font.PLAIN, 13));
        g.setColor(MUTED);
        centered(g, card.subtitle(), cx, 44);
        g.setFont(font(Font.BOLD, 22));
        g.setColor(MUTED);
        centered(g, "VS", cx, 76);

        // blue side (right-aligned, left of centre)
        g.setFont(font(Font.BOLD, 13));
        g.setColor(BLUE);
        rightText(g, "NIEBIESCY", cx - 100, 44);
        g.setFont(font(Font.BOLD, 42));
        g.setColor(card.decided() && card.blueWon() ? TEXT : dim);
        rightText(g, String.valueOf(card.blueKills()), cx - 100, 82);

        // red side (left-aligned, right of centre)
        g.setFont(font(Font.BOLD, 13));
        g.setColor(RED);
        g.drawString("CZERWONI", cx + 100, 44);
        if (card.decided() && !card.blueWon()) drawCrown(g, cx + 100 + g.getFontMetrics().stringWidth("CZERWONI") + 8, 33, 14);
        g.setFont(font(Font.BOLD, 42));
        g.setColor(card.decided() && !card.blueWon() ? RED : dim);
        g.drawString(String.valueOf(card.redKills()), cx + 100, 82);
    }

    private void drawTeam(Graphics2D g, int x, int y, int w, int h, String label, Color accent,
                          boolean won, boolean decided, int kills, List<Row> rows) {
        g.setColor(PANEL);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 18, 18));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, 18, 18));

        // header
        g.setColor(accent);
        g.fillOval(x + 16, y + 19, 9, 9);
        g.setFont(font(Font.BOLD, 17));
        g.drawString(label, x + 33, y + 28);
        int afterLabel = x + 33 + g.getFontMetrics().stringWidth(label) + 12;
        if (decided) {
            g.setFont(font(Font.BOLD, 11));
            g.setColor(won ? WIN : MUTED);
            g.drawString(won ? "WYGRANA" : "PRZEGRANA", afterLabel, y + 27);
        }
        g.setFont(font(Font.PLAIN, 13));
        g.setColor(MUTED);
        String kd = kills + " zabójstw";
        g.drawString(kd, x + w - 16 - g.getFontMetrics().stringWidth(kd), y + 28);

        g.setColor(LINE);
        g.drawLine(x + 14, y + TEAM_HEAD - 2, x + w - 14, y + TEAM_HEAD - 2);

        int ry = y + TEAM_HEAD + 4;
        for (Row r : rows) {
            drawRow(g, x + 14, ry, w - 28, r);
            ry += ROW;
        }
    }

    private void drawRow(Graphics2D g, int x, int y, int w, Row r) {
        int mid = y + ROW / 2;
        int icon = 40;
        int iconY = mid - icon / 2;
        // champion icon (rounded)
        BufferedImage champ = icon(r.championIconUrl());
        java.awt.Shape clip = g.getClip();
        g.setColor(ICON_BG);
        g.fill(new RoundRectangle2D.Float(x, iconY, icon, icon, 8, 8));
        if (champ != null) {
            g.setClip(new RoundRectangle2D.Float(x, iconY, icon, icon, 8, 8));
            g.drawImage(champ, x, iconY, icon, icon, null);
            g.setClip(clip);
        }

        int textX = x + icon + 12;
        // nickname (+ crown)
        g.setFont(font(Font.BOLD, 16));
        g.setColor(r.mvp() ? GOLD : TEXT);
        g.drawString(safe(r.nick()), textX, mid - 2);
        if (r.mvp()) {
            int nx = textX + g.getFontMetrics().stringWidth(safe(r.nick())) + 8;
            drawCrown(g, nx, mid - 14, 14);
        }
        // role · champion
        g.setFont(font(Font.PLAIN, 12));
        g.setColor(MUTED);
        g.drawString((safe(r.role()) + " · " + safe(r.champion())).toUpperCase(java.util.Locale.ROOT),
                textX, mid + 15);

        int right = x + w;
        // PR pill
        if (r.pr() != null) {
            int pw = 46, ph = 30;
            int px = right - pw, py = mid - ph / 2;
            g.setColor(pillColor(r.pr()));
            g.fill(new RoundRectangle2D.Float(px, py, pw, ph, 15, 15));
            g.setFont(font(Font.BOLD, 16));
            g.setColor(r.pr() >= 45 ? Color.WHITE : new Color(0xC0, 0xC3, 0xCE));
            String prs = String.valueOf(r.pr());
            g.drawString(prs, px + (pw - g.getFontMetrics().stringWidth(prs)) / 2, py + 20);
        }
        // KDA + CS (to the left of the pill)
        int statsRight = right - 46 - 16;
        g.setFont(font(Font.BOLD, 15));
        String k = r.kills() + " ", slash1 = "/ ", d = r.deaths() + " ", slash2 = "/ ", a = String.valueOf(r.assists());
        int kdaW = g.getFontMetrics().stringWidth(k + slash1 + d + slash2 + a);
        int kx = statsRight - kdaW;
        g.setColor(TEXT); g.drawString(k, kx, mid - 2); kx += g.getFontMetrics().stringWidth(k);
        g.setColor(MUTED); g.drawString(slash1, kx, mid - 2); kx += g.getFontMetrics().stringWidth(slash1);
        g.setColor(DEATH); g.drawString(d, kx, mid - 2); kx += g.getFontMetrics().stringWidth(d);
        g.setColor(MUTED); g.drawString(slash2, kx, mid - 2); kx += g.getFontMetrics().stringWidth(slash2);
        g.setColor(TEXT); g.drawString(a, kx, mid - 2);
        g.setFont(font(Font.PLAIN, 12));
        g.setColor(MUTED);
        String cs = r.cs() + " CS";
        g.drawString(cs, statsRight - g.getFontMetrics().stringWidth(cs), mid + 15);
    }

    private static void drawCrown(Graphics2D g, int x, int y, int s) {
        g.setColor(GOLD);
        Path2D p = new Path2D.Float();
        p.moveTo(x, y + s);
        p.lineTo(x, y + s * 0.35);
        p.lineTo(x + s * 0.25, y + s * 0.6);
        p.lineTo(x + s * 0.5, y + s * 0.2);
        p.lineTo(x + s * 0.75, y + s * 0.6);
        p.lineTo(x + s, y + s * 0.35);
        p.lineTo(x + s, y + s);
        p.closePath();
        g.fill(p);
    }

    private static Color pillColor(int pr) {
        if (pr >= 70) return PILL_PURPLE;
        if (pr >= 45) return PILL_TEAL;
        return PILL_GRAY;
    }

    private BufferedImage icon(String url) {
        if (url == null || url.isBlank()) return null;
        return iconCache.computeIfAbsent(url, u -> {
            try {
                return ImageIO.read(URI.create(u).toURL());
            } catch (Exception ex) {
                return null;
            }
        });
    }

    private static void centered(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    private static void rightText(Graphics2D g, String s, int rightX, int y) {
        g.drawString(s, rightX - g.getFontMetrics().stringWidth(s), y);
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
    }

    private static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }
}
