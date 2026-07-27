package pl.romcio.driperska.ranking.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;
import pl.romcio.driperska.ranking.domain.ScoringConfig.RoleWeights;

/**
 * Computes each participant's Performance Rating (0–100) relative to the rest of the match, with
 * role-weighted metrics. Pure and stateless — see docs/04-points-and-ranking.md.
 */
@Component
public class RatingCalculator {

    /**
     * One metric of a participant's PR math: raw value vs the comparison average, normalised
     * 0–1, weighted, and the resulting PR points (weight × norm × 100).
     */
    public record PrMetricDetail(String key, double value, double average, double normalized,
                                 double weight, double points) {
    }

    /** A participant's PR together with the per-metric breakdown that produced it. */
    public record PrDetail(double pr, List<PrMetricDetail> metrics) {
    }

    public Map<UUID, Double> computePerformance(MatchStatsContext ctx, ScoringConfig cfg) {
        Map<UUID, Double> out = new HashMap<>();
        computeDetailed(ctx, cfg).forEach((id, detail) -> out.put(id, detail.pr()));
        return out;
    }

    /** Same computation as {@link #computePerformance}, additionally exposing each metric's math. */
    public Map<UUID, PrDetail> computeDetailed(MatchStatsContext ctx, ScoringConfig cfg) {
        double minutes = ctx.minutes();

        // Per-participant raw metric values.
        Map<UUID, Metrics> raw = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            int teamKills = Math.max(1, ctx.teamKills(p.side()));
            long teamDamage = Math.max(1, ctx.teamDamage(p.side()));
            long teamGold = Math.max(1, ctx.teamGold(p.side()));
            raw.put(p.participantId(), new Metrics(
                    p.kda(),
                    (p.kills() + p.assists()) / (double) teamKills,
                    p.cs() / minutes,
                    p.damageToChampions() / minutes,
                    p.damageToChampions() / (double) teamDamage,
                    p.gold() / (double) teamGold,
                    p.visionScore() / minutes));
        }

        // Role averages across the whole match (fallback for singleton roles).
        Map<Role, Metrics> roleAvg = averagesByRole(ctx, raw);
        Metrics overallAvg = average(raw.values());

        Map<UUID, PrDetail> result = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            Metrics m = raw.get(p.participantId());
            long sameRole = ctx.participants().stream().filter(o -> o.role() == p.role()).count();
            Metrics avg = (sameRole >= 2) ? roleAvg.getOrDefault(p.role(), overallAvg) : overallAvg;
            RoleWeights w = cfg.weightsFor(p.role());

            double kdaNorm = norm(m.kda(), avg.kda());
            double kpNorm = norm(m.kp(), avg.kp());
            double csNorm = norm(m.csPerMin(), avg.csPerMin());
            double damageNorm = (norm(m.dmgPerMin(), avg.dmgPerMin()) + norm(m.dmgShare(), avg.dmgShare())) / 2.0;
            double goldNorm = norm(m.goldShare(), avg.goldShare());
            double visionNorm = norm(m.visionPerMin(), avg.visionPerMin());
            double score =
                    w.kda() * kdaNorm
                    + w.kp() * kpNorm
                    + w.cs() * csNorm
                    + w.damage() * damageNorm
                    + w.gold() * goldNorm
                    + w.vision() * visionNorm;
            List<PrMetricDetail> metrics = List.of(
                    metric("KDA", m.kda(), avg.kda(), kdaNorm, w.kda()),
                    metric("KP", m.kp(), avg.kp(), kpNorm, w.kp()),
                    metric("CS", m.csPerMin(), avg.csPerMin(), csNorm, w.cs()),
                    metric("DMG", m.dmgPerMin(), avg.dmgPerMin(), damageNorm, w.damage()),
                    metric("GOLD", m.goldShare(), avg.goldShare(), goldNorm, w.gold()),
                    metric("VISION", m.visionPerMin(), avg.visionPerMin(), visionNorm, w.vision()));
            result.put(p.participantId(), new PrDetail(round2(clamp(score, 0, 1) * 100.0), metrics));
        }
        return result;
    }

    private static PrMetricDetail metric(String key, double value, double average,
                                         double normalized, double weight) {
        return new PrMetricDetail(key, round2(value), round2(average), round2(normalized),
                weight, round2(weight * normalized * 100.0));
    }

    /** Normalise a value: exactly average → 0.5, twice the average or more → 1.0. */
    private static double norm(double value, double avg) {
        if (avg <= 0) {
            return 0.5;
        }
        return clamp(value / (2 * avg), 0, 1);
    }

    private static Map<Role, Metrics> averagesByRole(MatchStatsContext ctx, Map<UUID, Metrics> raw) {
        Map<Role, List<Metrics>> byRole = new EnumMap<>(Role.class);
        for (ParticipantInput p : ctx.participants()) {
            byRole.computeIfAbsent(p.role(), r -> new java.util.ArrayList<>()).add(raw.get(p.participantId()));
        }
        Map<Role, Metrics> avg = new EnumMap<>(Role.class);
        byRole.forEach((role, list) -> avg.put(role, average(list)));
        return avg;
    }

    private static Metrics average(java.util.Collection<Metrics> list) {
        int n = Math.max(1, list.size());
        double kda = 0, kp = 0, cs = 0, dpm = 0, ds = 0, gs = 0, vpm = 0;
        for (Metrics m : list) {
            kda += m.kda();
            kp += m.kp();
            cs += m.csPerMin();
            dpm += m.dmgPerMin();
            ds += m.dmgShare();
            gs += m.goldShare();
            vpm += m.visionPerMin();
        }
        return new Metrics(kda / n, kp / n, cs / n, dpm / n, ds / n, gs / n, vpm / n);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Metrics(double kda, double kp, double csPerMin, double dmgPerMin,
                           double dmgShare, double goldShare, double visionPerMin) {
    }
}
