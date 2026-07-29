package pl.romcio.driperska.champion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.romcio.driperska.champion.domain.Champion;

/**
 * The OCR pipeline is only as good as this lookup: whatever the vision model writes has to land on a
 * champion id, or the admin sees an empty column and retypes ten rows by hand.
 */
class ChampionNameResolverTest {

    /** Slug and display name differ on purpose for the awkward ones (Wukong, Nunu, Mundo). */
    private static final List<Champion> ROSTER = List.of(
            new Champion(266, "Aatrox", "Aatrox"),
            new Champion(103, "Ahri", "Ahri"),
            new Champion(32, "Amumu", "Amumu"),
            new Champion(136, "AurelionSol", "Aurelion Sol"),
            new Champion(53, "Blitzcrank", "Blitzcrank"),
            new Champion(51, "Caitlyn", "Caitlyn"),
            new Champion(31, "Chogath", "Cho'Gath"),
            new Champion(36, "DrMundo", "Dr. Mundo"),
            new Champion(28, "Evelynn", "Evelynn"),
            new Champion(41, "Gangplank", "Gangplank"),
            new Champion(59, "JarvanIV", "Jarvan IV"),
            new Champion(145, "Kaisa", "Kai'Sa"),
            new Champion(10, "Kayle", "Kayle"),
            new Champion(897, "KSante", "K'Sante"),
            new Champion(64, "LeeSin", "Lee Sin"),
            new Champion(11, "MasterYi", "Master Yi"),
            new Champion(21, "MissFortune", "Miss Fortune"),
            new Champion(20, "Nunu", "Nunu & Willump"),
            new Champion(61, "Orianna", "Orianna"),
            new Champion(421, "RekSai", "Rek'Sai"),
            new Champion(888, "Renata", "Renata Glasc"),
            new Champion(223, "TahmKench", "Tahm Kench"),
            new Champion(4, "TwistedFate", "Twisted Fate"),
            new Champion(62, "MonkeyKing", "Wukong"),
            new Champion(5, "XinZhao", "Xin Zhao"),
            new Champion(157, "Yasuo", "Yasuo"),
            new Champion(777, "Yone", "Yone"));

    private final ChampionNameResolver resolver = ChampionNameResolver.of(ROSTER);

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({
            // Exact canonical names, with and without the punctuation the model may drop.
            "Aatrox, 266",
            "Kai'Sa, 145",
            "Kaisa, 145",
            "Cho'Gath, 31",
            "cho gath, 31",
            "K'Sante, 897",
            "Master Yi, 11",
            // Data Dragon slugs — the model echoes these from image filenames.
            "MonkeyKing, 62",
            "DrMundo, 36",
            "JarvanIV, 59",
            // Shorthand the model writes instead of the full name.
            "Nunu, 20",
            "Willump, 20",
            "Mundo, 36",
            "Renata, 888",
            "asol, 136",
            "mf, 21",
            "j4, 59",
            "tf, 4",
            "yi, 11",
            "kench, 223",
            // OCR slips of one or two characters.
            "Kayie, 10",
            "Aatrix, 266",
            "Blitzcrenk, 53",
            "Xin Zhoa, 5",
            "Twisted Fale, 4",
            "Orianne, 61",
    })
    void resolvesWhatAVisionModelActuallyWrites(String written, int expectedId) {
        assertThat(resolver.resolve(written)).isEqualTo(expectedId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "???", "Player 4", "unknown",
            // Not on the roster and not close to anything on it.
            "Zeri", "Smolder",
    })
    void returnsNullRatherThanGuessing(String written) {
        assertThat(resolver.resolve(written)).isNull();
    }

    @Test
    void handlesNull() {
        assertThat(resolver.resolve(null)).isNull();
    }

    /**
     * Yasuo and Yone are one edit apart from a shared neighbourhood; a two-letter query must not be
     * allowed to pick one at random. A wrong champion an admin approves is worse than a blank field.
     */
    @Test
    void refusesAmbiguousShortQueries()  {
        assertThat(resolver.resolve("yo")).isNull();
        assertThat(resolver.resolve("y")).isNull();
    }

    @Test
    void anEmptyRosterResolvesNothing() {
        assertThat(ChampionNameResolver.of(List.of()).resolve("Ahri")).isNull();
        assertThat(ChampionNameResolver.of(null).resolve("Ahri")).isNull();
    }

    @Test
    void editDistanceStopsCountingOnceOverBudget() {
        assertThat(ChampionNameResolver.editDistance("kayle", "kayle", 2)).isZero();
        assertThat(ChampionNameResolver.editDistance("kayle", "kayie", 2)).isEqualTo(1);
        assertThat(ChampionNameResolver.editDistance("kayle", "orianna", 2)).isGreaterThan(2);
    }
}
