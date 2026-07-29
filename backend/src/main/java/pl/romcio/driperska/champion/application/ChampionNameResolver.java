package pl.romcio.driperska.champion.application;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import pl.romcio.driperska.champion.domain.Champion;

/**
 * Maps a champion name written by something imprecise — a vision model reading a 32px portrait, or an
 * admin typing quickly — onto a champion id.
 *
 * <p>The OCR pipeline used to look champions up with a single exact match on the normalised display
 * name, so anything short of a perfect answer silently produced no champion at all: "Nunu" (the model
 * rarely writes "Nunu & Willump"), "Mundo", "MonkeyKing", or a one-letter OCR slip like "Kayle" →
 * "Kayie". The admin then saw an empty champion column and concluded nothing had been recognised.
 *
 * <p>Resolution runs widest-confidence-first and <b>gives up rather than guesses</b>: a blank field an
 * admin notices is much better than a plausible wrong champion they approve without looking. Every
 * step therefore requires an unambiguous winner.
 *
 * <ol>
 *   <li>exact match on the display name ("Kai'Sa") or the Data Dragon slug ("MonkeyKing");</li>
 *   <li>a known shorthand ("asol", "mf", "j4") — community abbreviations no distance metric reaches;</li>
 *   <li>a unique prefix or containment hit ("Nunu" → Nunu &amp; Willump, "Mundo" → Dr. Mundo);</li>
 *   <li>a unique closest edit-distance match within a length-scaled budget, for OCR typos.</li>
 * </ol>
 *
 * Names are compared with punctuation, spaces and case stripped, so "Cho'Gath", "cho gath" and
 * "chogath" are one and the same.
 */
public final class ChampionNameResolver {

    /**
     * Community shorthand and nicknames a vision model may echo from a screenshot's chat or from its
     * own training data. Keys and values are already normalised.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("asol", "aurelionsol"),
            Map.entry("blitz", "blitzcrank"),
            Map.entry("cait", "caitlyn"),
            Map.entry("cho", "chogath"),
            Map.entry("eve", "evelynn"),
            Map.entry("gp", "gangplank"),
            Map.entry("j4", "jarvaniv"),
            Map.entry("jarvan", "jarvaniv"),
            Map.entry("kass", "kassadin"),
            Map.entry("kench", "tahmkench"),
            Map.entry("kog", "kogmaw"),
            Map.entry("lee", "leesin"),
            Map.entry("mf", "missfortune"),
            Map.entry("monkeyking", "wukong"),
            Map.entry("morde", "mordekaiser"),
            Map.entry("mumu", "amumu"),
            Map.entry("mundo", "drmundo"),
            Map.entry("nunu", "nunuwillump"),
            Map.entry("ori", "orianna"),
            Map.entry("panth", "pantheon"),
            Map.entry("rek", "reksai"),
            Map.entry("renata", "renataglasc"),
            Map.entry("sej", "sejuani"),
            Map.entry("tf", "twistedfate"),
            Map.entry("trist", "tristana"),
            Map.entry("vlad", "vladimir"),
            Map.entry("voli", "volibear"),
            Map.entry("willump", "nunuwillump"),
            Map.entry("xin", "xinzhao"),
            Map.entry("yi", "masteryi"));

    /** Normalised display name and slug → champion id. */
    private final Map<String, Integer> exact = new HashMap<>();
    /** Normalised display names only, for fuzzy passes — slugs would double every candidate. */
    private final Map<String, Integer> canonical = new HashMap<>();

    private ChampionNameResolver(List<Champion> champions) {
        for (Champion champion : champions) {
            String name = normalise(champion.getName());
            if (!name.isEmpty()) {
                exact.putIfAbsent(name, champion.getId());
                canonical.putIfAbsent(name, champion.getId());
            }
            String slug = normalise(champion.getSlug());
            if (!slug.isEmpty()) {
                exact.putIfAbsent(slug, champion.getId());
            }
        }
    }

    public static ChampionNameResolver of(List<Champion> champions) {
        return new ChampionNameResolver(champions == null ? List.of() : champions);
    }

    /** Lower-cases and drops everything that is not a letter or digit. */
    public static String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * @return the champion id, or {@code null} when nothing matches confidently enough to pre-fill.
     */
    public Integer resolve(String raw) {
        String query = normalise(raw);
        if (query.isEmpty()) {
            return null;
        }
        Integer hit = exact.get(query);
        if (hit != null) {
            return hit;
        }
        String alias = ALIASES.get(query);
        if (alias != null) {
            Integer aliased = exact.get(alias);
            if (aliased != null) {
                return aliased;
            }
        }
        Integer affix = uniqueAffixMatch(query);
        if (affix != null) {
            return affix;
        }
        return uniqueClosestMatch(query);
    }

    /**
     * One candidate that starts with the query, or contains it. Requires a single winner: "kai" hits
     * both Kai'Sa and Kaisa-like names in some locales, and a coin flip there is worse than nothing.
     * Queries shorter than four characters are skipped — "ka" would match half the roster.
     */
    private Integer uniqueAffixMatch(String query) {
        if (query.length() < 4) {
            return null;
        }
        Integer found = null;
        for (Map.Entry<String, Integer> entry : canonical.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(query) && !name.contains(query) && !query.startsWith(name)) {
                continue;
            }
            if (found != null && !found.equals(entry.getValue())) {
                return null; // ambiguous
            }
            found = entry.getValue();
        }
        return found;
    }

    /**
     * Closest name by edit distance, within a budget that grows with length (1 for short names, up to
     * 3 for long ones) and only when strictly closer than every other candidate.
     */
    private Integer uniqueClosestMatch(String query) {
        int budget = query.length() <= 5 ? 1 : query.length() <= 9 ? 2 : 3;
        int best = Integer.MAX_VALUE;
        int runnerUp = Integer.MAX_VALUE;
        Integer bestId = null;
        for (Map.Entry<String, Integer> entry : canonical.entrySet()) {
            int distance = editDistance(query, entry.getKey(), budget);
            if (distance < best) {
                runnerUp = best;
                best = distance;
                bestId = entry.getValue();
            } else if (distance < runnerUp) {
                runnerUp = distance;
            }
        }
        return best <= budget && best < runnerUp ? bestId : null;
    }

    /** Levenshtein distance, abandoned early once every cell in a row exceeds {@code budget}. */
    static int editDistance(String a, String b, int budget) {
        if (Math.abs(a.length() - b.length()) > budget) {
            return budget + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > budget) {
                return budget + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
