package pl.romcio.driperska.common.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * The Driperska Liga "DL" monogram as a Java2D shape, so the PNG cards we post to Discord carry the
 * same mark as the site instead of a bare text header.
 *
 * The geometry is the Mono variant from {@code frontend/src/components/brand/Logo.tsx}, on the same
 * 256×256 grid — keep the two in sync. Within that grid the letters occupy x 40→216 and y 68→188,
 * which is what {@link #widthFor(float)} is derived from.
 */
public final class BrandMark {

    /** Gold token (--gold, #f2c14e) as used by the marks. */
    public static final Color GOLD = new Color(0xF2, 0xC1, 0x4E);

    private static final float GRID_LEFT = 40f;
    private static final float GRID_TOP = 68f;
    private static final float GRID_W = 176f;
    private static final float GRID_H = 120f;

    private static final Path2D.Float LETTERS = buildLetters();

    private BrandMark() {
    }

    /** How wide the mark renders at a given height, so callers can lay text out after it. */
    public static float widthFor(float height) {
        return height * (GRID_W / GRID_H);
    }

    /**
     * Fills the monogram with its top-left corner at ({@code x}, {@code y}) — the corner of the
     * letters themselves, not of the 256-grid, so it aligns to a layout without invisible padding.
     */
    public static void draw(Graphics2D g, float x, float y, float height, Color color) {
        float scale = height / GRID_H;
        AffineTransform at = new AffineTransform();
        at.translate(x - GRID_LEFT * scale, y - GRID_TOP * scale);
        at.scale(scale, scale);
        Shape mark = at.createTransformedShape(LETTERS);
        Color previous = g.getColor();
        g.setColor(color);
        g.fill(mark);
        g.setColor(previous);
    }

    /**
     * The D (outer contour plus its counter, subtracted by the even-odd winding rule) and the L,
     * both at a constant 28-unit stroke weight with 45° chamfers.
     */
    private static Path2D.Float buildLetters() {
        Path2D.Float path = new Path2D.Float(Path2D.WIND_EVEN_ODD);

        // D — outer
        path.moveTo(40, 68);
        path.lineTo(104, 68);
        path.lineTo(130, 94);
        path.lineTo(130, 162);
        path.lineTo(104, 188);
        path.lineTo(40, 188);
        path.closePath();

        // D — counter
        path.moveTo(68, 96);
        path.lineTo(92, 96);
        path.lineTo(102, 106);
        path.lineTo(102, 150);
        path.lineTo(92, 160);
        path.lineTo(68, 160);
        path.closePath();

        // L
        path.moveTo(150, 68);
        path.lineTo(178, 68);
        path.lineTo(178, 160);
        path.lineTo(216, 160);
        path.lineTo(216, 188);
        path.lineTo(150, 188);
        path.closePath();

        return path;
    }
}
