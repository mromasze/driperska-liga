package pl.romcio.driperska.ranking.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;
import pl.romcio.driperska.ranking.domain.RatingCalculator.PrDetail;
import pl.romcio.driperska.ranking.domain.RatingCalculator.PrMetricDetail;
import pl.romcio.driperska.ranking.domain.ScoringConfig.RoleWeights;

/**
 * PR v2. During the first role samples it blends the in-match lane comparison with the historical
 * percentile for that role. After 20 samples the historical role distribution becomes the full
 * reference. The configured KDA weights are intentionally preserved.
 */
@Component
public class PerformanceRatingV2Calculator {

    private static final int FULL_HISTORY_SAMPLES = 20;
    private static final int MAX_HISTORY_SAMPLES_PER_ROLE = 60;

    public Map<UUID, Double> computePerformance(MatchStatsContext ctx, ScoringConfig cfg,
                                                PerformanceHistory history) {
        Map<UUID, Double> result = new HashMap<>();
        computeDetailed(ctx, cfg, history).forEach((id, detail) -> result.put(id, detail.pr()));
        return result;
    }

    public Map<UUID, PrDetail> computeDetailed(MatchStatsContext ctx, ScoringConfig cfg,
                                               PerformanceHistory history) {
        Map<UUID, Metrics> raw = rawMetrics(ctx);
        Map<Role, Metrics> roleAverage = averagesByRole(ctx, raw);
        Metrics overallAverage = average(raw.values());
        Map<UUID, PrDetail> result = new HashMap<>();

        for (ParticipantInput participant : ctx.participants()) {
            Metrics value = raw.get(participant.participantId());
            long sameRole = ctx.participants().stream()
                    .filter(other -> other.role() == participant.role())
                    .count();
            Metrics matchReference = sameRole >= 2
                    ? roleAverage.getOrDefault(participant.role(), overallAverage)
                    : overallAverage;
            HistoricalReference historical = history.reference(participant.role(), value);
            double historyWeight = Math.min(1.0,
                    historical.samples() / (double) FULL_HISTORY_SAMPLES);

            Metrics matchScore = normalize(value, matchReference);
            Metrics score = blend(matchScore, historical.percentiles(), historyWeight);
            Metrics displayedReference = historyWeight > 0
                    ? historical.medians()
                    : matchReference;
            RoleWeights weights = cfg.weightsFor(participant.role());

            List<PrMetricDetail> metrics = List.of(
                    metric("KDA", value.kda(), displayedReference.kda(), score.kda(), weights.kda()),
                    metric("KP", value.kp(), displayedReference.kp(), score.kp(), weights.kp()),
                    metric("CS", value.csPerMin(), displayedReference.csPerMin(),
                            score.csPerMin(), weights.cs()),
                    metric("DMG", value.dmgPerMin(), displayedReference.dmgPerMin(),
                            score.dmgPerMin(), weights.damage()),
                    metric("EFF", value.damagePerGold(), displayedReference.damagePerGold(),
                            score.damagePerGold(), weights.gold()),
                    metric("VISION", value.visionPerMin(), displayedReference.visionPerMin(),
                            score.visionPerMin(), weights.vision()));
            double pr = metrics.stream().mapToDouble(PrMetricDetail::points).sum();
            result.put(participant.participantId(),
                    new PrDetail(round2(clamp(pr, 0.0, 100.0)), metrics));
        }
        return result;
    }

    private static Map<UUID, Metrics> rawMetrics(MatchStatsContext ctx) {
        double minutes = ctx.minutes();
        Map<UUID, Metrics> raw = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            int teamKills = Math.max(1, ctx.teamKills(p.side()));
            raw.put(p.participantId(), new Metrics(
                    p.kda(),
                    (p.kills() + p.assists()) / (double) teamKills,
                    p.cs() / minutes,
                    p.damageToChampions() / minutes,
                    p.gold() <= 0 ? 0.0 : p.damageToChampions() / (double) p.gold(),
                    p.visionScore() / minutes));
        }
        return raw;
    }

    private static Metrics normalize(Metrics value, Metrics average) {
        return new Metrics(
                norm(value.kda(), average.kda()),
                norm(value.kp(), average.kp()),
                norm(value.csPerMin(), average.csPerMin()),
                norm(value.dmgPerMin(), average.dmgPerMin()),
                norm(value.damagePerGold(), average.damagePerGold()),
                norm(value.visionPerMin(), average.visionPerMin()));
    }

    private static Metrics blend(Metrics match, Metrics history, double historyWeight) {
        double matchWeight = 1.0 - historyWeight;
        return new Metrics(
                matchWeight * match.kda() + historyWeight * history.kda(),
                matchWeight * match.kp() + historyWeight * history.kp(),
                matchWeight * match.csPerMin() + historyWeight * history.csPerMin(),
                matchWeight * match.dmgPerMin() + historyWeight * history.dmgPerMin(),
                matchWeight * match.damagePerGold() + historyWeight * history.damagePerGold(),
                matchWeight * match.visionPerMin() + historyWeight * history.visionPerMin());
    }

    private static Map<Role, Metrics> averagesByRole(MatchStatsContext ctx, Map<UUID, Metrics> raw) {
        Map<Role, List<Metrics>> byRole = new EnumMap<>(Role.class);
        for (ParticipantInput p : ctx.participants()) {
            byRole.computeIfAbsent(p.role(), ignored -> new ArrayList<>())
                    .add(raw.get(p.participantId()));
        }
        Map<Role, Metrics> averages = new EnumMap<>(Role.class);
        byRole.forEach((role, values) -> averages.put(role, average(values)));
        return averages;
    }

    private static Metrics average(Collection<Metrics> values) {
        int size = Math.max(1, values.size());
        double kda = 0, kp = 0, cs = 0, damage = 0, efficiency = 0, vision = 0;
        for (Metrics value : values) {
            kda += value.kda();
            kp += value.kp();
            cs += value.csPerMin();
            damage += value.dmgPerMin();
            efficiency += value.damagePerGold();
            vision += value.visionPerMin();
        }
        return new Metrics(kda / size, kp / size, cs / size, damage / size,
                efficiency / size, vision / size);
    }

    private static double norm(double value, double average) {
        if (average <= 0) return 0.5;
        return clamp(value / (2.0 * average), 0.0, 1.0);
    }

    private static PrMetricDetail metric(String key, double value, double reference,
                                         double normalized, double weight) {
        return new PrMetricDetail(key, round2(value), round2(reference), round2(normalized),
                weight, round2(weight * normalized * 100.0));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Metrics(double kda, double kp, double csPerMin, double dmgPerMin,
                           double damagePerGold, double visionPerMin) {
    }

    private record HistoricalReference(int samples, Metrics percentiles, Metrics medians) {
    }

    /** Rolling role samples used by chronological recalculation and by match previews. */
    public static final class PerformanceHistory {
        private final Map<Role, Deque<Metrics>> samples = new EnumMap<>(Role.class);

        public void add(MatchStatsContext context) {
            Map<UUID, Metrics> raw = rawMetrics(context);
            for (ParticipantInput participant : context.participants()) {
                Deque<Metrics> roleSamples = samples.computeIfAbsent(
                        participant.role(), ignored -> new ArrayDeque<>());
                roleSamples.addLast(raw.get(participant.participantId()));
                while (roleSamples.size() > MAX_HISTORY_SAMPLES_PER_ROLE) {
                    roleSamples.removeFirst();
                }
            }
        }

        public int sampleCount(Role role) {
            return samples.getOrDefault(role, new ArrayDeque<>()).size();
        }

        private HistoricalReference reference(Role role, Metrics value) {
            List<Metrics> roleSamples = List.copyOf(
                    samples.getOrDefault(role, new ArrayDeque<>()));
            if (roleSamples.isEmpty()) {
                Metrics neutral = new Metrics(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
                return new HistoricalReference(0, neutral, value);
            }
            Metrics percentile = new Metrics(
                    percentile(roleSamples, value.kda(), Metrics::kda),
                    percentile(roleSamples, value.kp(), Metrics::kp),
                    percentile(roleSamples, value.csPerMin(), Metrics::csPerMin),
                    percentile(roleSamples, value.dmgPerMin(), Metrics::dmgPerMin),
                    percentile(roleSamples, value.damagePerGold(), Metrics::damagePerGold),
                    percentile(roleSamples, value.visionPerMin(), Metrics::visionPerMin));
            Metrics median = new Metrics(
                    median(roleSamples, Metrics::kda),
                    median(roleSamples, Metrics::kp),
                    median(roleSamples, Metrics::csPerMin),
                    median(roleSamples, Metrics::dmgPerMin),
                    median(roleSamples, Metrics::damagePerGold),
                    median(roleSamples, Metrics::visionPerMin));
            return new HistoricalReference(roleSamples.size(), percentile, median);
        }

        private static double percentile(List<Metrics> samples, double value,
                                         ToDoubleFunction<Metrics> extractor) {
            long lower = samples.stream().filter(sample -> extractor.applyAsDouble(sample) < value).count();
            long equal = samples.stream().filter(sample ->
                    Double.compare(extractor.applyAsDouble(sample), value) == 0).count();
            return (lower + 0.5 * equal) / samples.size();
        }

        private static double median(List<Metrics> samples, ToDoubleFunction<Metrics> extractor) {
            double[] sorted = samples.stream().mapToDouble(extractor).sorted().toArray();
            int middle = sorted.length / 2;
            return sorted.length % 2 == 0
                    ? (sorted[middle - 1] + sorted[middle]) / 2.0
                    : sorted[middle];
        }
    }
}
