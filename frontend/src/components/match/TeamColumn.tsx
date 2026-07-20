import type { MatchParticipant, Side } from '../../api/types';
import { ChampionIcon } from '../champion/ChampionIcon';
import { PrBadge } from '../ui/PrBadge';
import { Badge } from '../ui/Badge';
import { roleLabel } from '../../lib/format';
import { cn } from '../../lib/cn';

export interface TeamColumnProps {
  side: Side;
  participants: MatchParticipant[];
  won: boolean;
}

/** One side of the scoreboard (docs/06 §6.6). Collapses to per-player rows on mobile. */
export function TeamColumn({ side, participants, won }: TeamColumnProps) {
  const color = side === 'BLUE' ? 'var(--blue)' : 'var(--red)';
  const bg = side === 'BLUE' ? 'var(--blue-bg)' : 'var(--red-bg)';
  const teamKills = participants.reduce((sum, p) => sum + p.kills, 0);

  return (
    <div className="rounded-md border border-line" style={{ backgroundColor: bg }}>
      <div
        className="flex items-center justify-between rounded-t-md px-4 py-2"
        style={{ borderBottom: `2px solid ${color}` }}
      >
        <span className="font-semibold" style={{ color }}>
          {side === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
        </span>
        <span className="flex items-center gap-2">
          {won && <Badge tone="win">Zwycięstwo</Badge>}
          <span className="num text-sm text-text-lo">{teamKills} kills</span>
        </span>
      </div>

      <ul className="divide-y divide-line/50">
        {participants.map((p) => (
          <li key={p.id} className="flex items-center gap-3 px-3 py-2">
            <ChampionIcon src={p.championIconUrl} name={p.championName} size={36} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                <span className="truncate text-sm font-medium text-text-hi">{p.nickname}</span>
                {p.isMvp && <Badge tone="gold">MVP</Badge>}
                {p.largestMultiKill >= 5 && <Badge tone="gold">PENTA</Badge>}
                {p.largestMultiKill === 4 && <Badge tone="info">QUADRA</Badge>}
              </div>
              <div className="text-xs text-text-lo">
                {roleLabel(p.role)} · {p.championName}
              </div>
            </div>
            <div className="text-right">
              <div className="num text-sm tabular-nums text-text-hi">
                {p.kills}/{p.deaths}/{p.assists}
              </div>
              <div className="num text-xs text-text-lo">
                {p.cs} CS · {Math.round(p.damageToChampions / 1000)}k dmg
              </div>
            </div>
            <PrBadge value={p.performanceRating} className={cn('ml-1')} />
          </li>
        ))}
      </ul>
    </div>
  );
}
