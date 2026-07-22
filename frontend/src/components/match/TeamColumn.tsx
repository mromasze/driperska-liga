import { Link } from 'react-router-dom';
import type { MatchParticipant, Side } from '../../api/types';
import { formatDuration, roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';
import { ChampionIcon } from '../champion/ChampionIcon';
import { PrBadge } from '../ui/PrBadge';

interface TeamColumnProps {
  side: Side;
  players: MatchParticipant[];
  won: boolean | null;
  teamKills: number;
}

const ROLE_ORDER: Record<string, number> = { TOP: 0, JUNGLE: 1, MID: 2, ADC: 3, SUPPORT: 4 };

export function TeamColumn({ side, players, won, teamKills }: TeamColumnProps) {
  const isBlue = side === 'BLUE';
  const accent = isBlue ? 'var(--blue)' : 'var(--red)';
  const sorted = [...players].sort((a, b) => (ROLE_ORDER[a.role] ?? 9) - (ROLE_ORDER[b.role] ?? 9));

  return (
    <div
      className="glass overflow-hidden"
      style={{ borderColor: `color-mix(in srgb, ${accent} 35%, transparent)` }}
    >
      <div
        className="flex items-center justify-between px-4 py-3"
        style={{ background: `color-mix(in srgb, ${accent} 12%, transparent)` }}
      >
        <div className="flex items-center gap-2">
          <span className="h-2.5 w-2.5 rounded-full" style={{ background: accent }} />
          <span className="font-display font-semibold" style={{ color: accent }}>
            {isBlue ? 'Niebiescy' : 'Czerwoni'}
          </span>
          {won != null && (
            <span
              className={cn(
                'num rounded px-1.5 text-[11px] font-bold',
                won ? 'text-win' : 'text-text-lo',
              )}
            >
              {won ? 'WYGRANA' : 'PRZEGRANA'}
            </span>
          )}
        </div>
        <div className="num text-sm text-text-lo">
          <span className="text-text-hi">{teamKills}</span> zabójstw
        </div>
      </div>

      <div className="divide-y divide-[color:var(--line)]">
        {sorted.map((p) => (
          <div key={p.playerId} className="flex items-center gap-3 px-3 py-2.5">
            <ChampionIcon iconUrl={p.championIconUrl} name={p.championName} size={38} />
            <div className="min-w-0 flex-1">
              <Link
                to={`/players/${p.playerId}`}
                className="flex items-center gap-1.5 truncate font-medium text-text-hi hover:text-gold"
              >
                {p.nickname}
                {p.mvp && <span title="MVP meczu">👑</span>}
              </Link>
              <div className="kicker mt-0.5" style={{ letterSpacing: '0.14em' }}>
                {roleLabel(p.role)} · {p.championName ?? '—'}
              </div>
            </div>
            <div className="num hidden text-right text-sm sm:block">
              <div className="text-text-hi">
                {p.kills}<span className="text-text-lo"> / </span>
                <span className="text-loss">{p.deaths}</span>
                <span className="text-text-lo"> / </span>{p.assists}
              </div>
              <div className="text-[11px] text-text-lo">{p.cs} CS</div>
            </div>
            <PrBadge value={p.performanceRating} />
          </div>
        ))}
      </div>
    </div>
  );
}

export function formatMatchClock(seconds: number | null | undefined) {
  return formatDuration(seconds);
}
