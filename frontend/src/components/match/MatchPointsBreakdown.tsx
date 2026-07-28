import type { MatchDetail, MatchParticipant, PrMetric } from '../../api/types';

const METRIC_LABEL: Record<string, string> = {
  KDA: 'KDA',
  KP: 'Udział w zabójstwach',
  CS: 'CS / min',
  DMG: 'Obrażenia / min',
  EFF: 'Obrażenia / złoto',
  VISION: 'Wizja / min',
};

/** KP is a ratio (0–1) — show it as a percentage; the rest are plain numbers. */
function metricValue(key: string, v: number): string {
  return key === 'KP' ? `${Math.round(v * 100)}%` : v.toFixed(2);
}

/**
 * Explains how each player's LP for the match adds up. Only the total is visible at first —
 * the full math (LP components + per-metric PR breakdown) unfolds on clicking „Szczegóły”.
 */
export function MatchPointsBreakdown({ match }: { match: MatchDetail }) {
  const scored = match.participants.filter((p) => p.lpBreakdown);
  if (scored.length === 0) return null;
  const sorted = [...scored].sort((a, b) => (b.lpBreakdown!.total) - (a.lpBreakdown!.total));
  const formula = sorted[0].lpBreakdown!.formula;

  return (
    <section className="glass grid-tex p-5 sm:p-7">
      <div className="mb-4">
        <div className="kicker text-gold">Punktacja</div>
        <h2 className="mt-1 font-display text-2xl">Skąd LP w tym meczu</h2>
        <p className="mt-1 text-sm text-text-lo">{formula}</p>
      </div>
      <div className="space-y-2">
        {sorted.map((p) => <PlayerPoints key={p.playerId} participant={p} />)}
      </div>
    </section>
  );
}

function PlayerPoints({ participant: p }: { participant: MatchParticipant }) {
  const breakdown = p.lpBreakdown!;
  return (
    <details className="match-dropdown overflow-hidden rounded-lg border border-line bg-[color:var(--bg-1)]/70">
      <summary className="flex cursor-pointer list-none flex-wrap items-center gap-x-3 gap-y-1 p-3">
        <span className="h-2.5 w-2.5 shrink-0 rounded-full"
          style={{ background: p.side === 'BLUE' ? 'var(--blue)' : 'var(--red)' }} />
        <span className="min-w-0 flex-1 truncate font-medium text-text-hi">
          {p.nickname}{p.mvp && ' 👑'}{p.ace && ' 🛡️'}
          {p.bestKda && ' 🎯'}{p.perfectKda && ' ✨'}
        </span>
        <span className="num shrink-0 font-display text-lg font-bold text-gold">
          {breakdown.total} LP
        </span>
        <span className="shrink-0 text-xs text-text-lo">
          Szczegóły <span className="match-dropdown-chevron inline-block transition-transform">▾</span>
        </span>
      </summary>

      <div className="space-y-3 border-t border-line p-3">
        {/* LP components: base + performance + bonuses = total */}
        <div className="flex flex-wrap items-center gap-1.5">
          {breakdown.components.map((c, i) => (
            <span key={i} className="inline-flex items-center gap-1 rounded-full border border-line bg-[color:var(--bg-2)] px-2 py-0.5 text-xs">
              <span className="text-text-lo">{c.label}</span>
              <span className="num font-semibold text-text-hi">{c.points >= 0 ? '+' : ''}{c.points}</span>
            </span>
          ))}
          <span className="num ml-auto text-sm font-semibold text-gold">= {breakdown.total} LP</span>
        </div>

        {/* Per-metric PR math behind the „Występ” component */}
        {breakdown.prMetrics.length > 0 && (
          <div>
            <div className="kicker mb-1.5">
              Występ (PR {p.performanceRating != null ? Math.round(p.performanceRating) : '—'}) — jak powstało
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[30rem] text-xs">
                <thead>
                  <tr className="text-left text-text-lo">
                    <th className="py-1 pr-2 font-medium">Metryka</th>
                    <th className="py-1 pr-2 text-right font-medium">Ty</th>
                    <th className="py-1 pr-2 text-right font-medium">Baza porównania</th>
                    <th className="py-1 pr-2 text-right font-medium">Wynik 0–1</th>
                    <th className="py-1 pr-2 text-right font-medium">Waga ligi</th>
                    <th className="py-1 text-right font-medium">Pkt PR</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {breakdown.prMetrics.map((m) => <PrMetricRow key={m.key} metric={m} />)}
                </tbody>
                <tfoot>
                  <tr className="border-t border-line text-text-hi">
                    <td className="py-1 pr-2 font-medium" colSpan={5}>Razem PR</td>
                    <td className="num py-1 text-right font-semibold">
                      {p.performanceRating != null ? p.performanceRating.toFixed(0) : '—'}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p className="mt-1.5 text-xs text-text-lo">
              PR v2 łączy historyczny percentyl pozycji z bezpośrednim porównaniem rywali tej roli.
              Historia dochodzi maksymalnie do 50% wagi, więc bieżący mecz zawsze stanowi co najmniej
              połowę oceny. Baza porównania orientacyjnie łączy medianę historii i średnią rywali;
              kolumna „Wynik 0–1” pokazuje dokładny rezultat połączenia obu ocen.
              Pkt PR = wynik × wspólna waga ligi × 100. Te same wagi obowiązują każdą rolę,
              dzięki czemu PR i MVP są porównywalne między pozycjami.
            </p>
          </div>
        )}
      </div>
    </details>
  );
}

function PrMetricRow({ metric: m }: { metric: PrMetric }) {
  return (
    <tr>
      <td className="py-1 pr-2 text-text">{METRIC_LABEL[m.key] ?? m.key}</td>
      <td className="num py-1 pr-2 text-right text-text-hi">{metricValue(m.key, m.value)}</td>
      <td className="num py-1 pr-2 text-right text-text-lo">{metricValue(m.key, m.average)}</td>
      <td className="num py-1 pr-2 text-right text-text-lo">{m.normalized.toFixed(2)}</td>
      <td className="num py-1 pr-2 text-right text-text-lo">{Math.round(m.weight * 100)}%</td>
      <td className="num py-1 text-right font-medium text-text-hi">{m.points.toFixed(1)}</td>
    </tr>
  );
}
