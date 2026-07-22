package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResultImageGeneratorTest {
    @Test
    void rendersAValidPngWithText() {
        var row = new ResultImageGenerator.Row(
                "Gracz", "MID", "Ahri", null, 10, 2, 8, 220, 78, true);
        var card = new ResultImageGenerator.Card(
                "Driperska Liga — Wynik meczu", "Czas: 32:10 · patch 26.14",
                true, true, 31, 18, List.of(row), List.of(row));

        byte[] png = new ResultImageGenerator().render(card);

        assertThat(png).hasSizeGreaterThan(1_000);
        assertThat(png).startsWith(
                (byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    }
}
