import type { MatchDetail } from '../../api/types';

/** Explains how each player's LP for the match adds up (base + performance + bonuses = total). */
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
        {sorted.map((p) => (
          <div key={p.playerId}
            className="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-line bg-[color:var(--bg-1)]/70 p-3">
            <span className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ background: p.side === 'BLUE' ? 'var(--blue)' : 'var(--red)' }} />
            <span className="w-32 shrink-0 truncate font-medium text-text-hi">
              {p.nickname}{p.mvp && ' 👑'}
            </span>
            <div className="flex flex-1 flex-wrap items-center gap-1.5">
              {p.lpBreakdown!.components.map((c, i) => (
                <span key={i} className="inline-flex items-center gap-1 rounded-full border border-line bg-[color:var(--bg-2)] px-2 py-0.5 text-xs">
                  <span className="text-text-lo">{c.label}</span>
                  <span className="num font-semibold text-text-hi">{c.points >= 0 ? '+' : ''}{c.points}</span>
                </span>
              ))}
            </div>
            <span className="num ml-auto shrink-0 font-display text-lg font-bold text-gold">
              {p.lpBreakdown!.total} LP
            </span>
          </div>
        ))}
      </div>
    </section>
  );
}
