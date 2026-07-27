import { Link } from 'react-router-dom';
import type { MatchDetail } from '../../api/types';
import { formatDate, formatDuration } from '../../lib/format';
import { matchMvp, teamKills } from '../../lib/match';
import { cn } from '../../lib/cn';
import { ChampionIcon } from '../champion/ChampionIcon';
import { PrBadge } from '../ui/PrBadge';

/** Rich result card built from a full match detail (used on Home / results grid). */
export function MatchCard({ match }: { match: MatchDetail }) {
  const blueKills = teamKills(match, 'BLUE');
  const redKills = teamKills(match, 'RED');
  const mvp = matchMvp(match);
  const blueWon = match.winningSide === 'BLUE';

  return (
    <Link to={`/matches/${match.id}`} className="glass lift block overflow-hidden p-0">
      <div className="flex items-stretch">
        <SideBar won={blueWon} color="var(--blue)" />
        <div className="flex-1 p-4">
          <div className="flex items-center justify-between text-xs text-text-lo">
            <span className="num">{formatDate(match.startedAt ?? match.completedAt ?? match.createdAt)}</span>
            <span className="num">{formatDuration(match.durationSeconds)}</span>
          </div>

          <div className="mt-2 flex items-center justify-center gap-3">
            <TeamScore label="BLUE" score={blueKills} color="var(--blue)" won={blueWon} />
            <span className="font-display text-sm text-text-lo">:</span>
            <TeamScore label="RED" score={redKills} color="var(--red)" won={match.winningSide === 'RED'} align="right" />
          </div>

          {mvp && (
            <div className="mt-3 flex items-center gap-2 border-t border-line pt-3">
              <ChampionIcon iconUrl={mvp.championIconUrl} name={mvp.championName} size={28} />
              <span className="text-xs text-text-lo">MVP</span>
              <span className="truncate text-sm font-medium text-text-hi">{mvp.nickname}</span>
              <span className="ml-auto">
                <PrBadge value={mvp.performanceRating} size="sm" />
              </span>
            </div>
          )}
        </div>
        <SideBar won={match.winningSide === 'RED'} color="var(--red)" />
      </div>
    </Link>
  );
}

function SideBar({ won, color }: { won: boolean; color: string }) {
  return (
    <span
      className={cn('w-1.5 shrink-0', !won && 'opacity-20')}
      style={{ background: color }}
      aria-hidden
    />
  );
}

function TeamScore({
  label,
  score,
  color,
  won,
  align = 'left',
}: {
  label: string;
  score: number;
  color: string;
  won: boolean;
  align?: 'left' | 'right';
}) {
  return (
    <div className={align === 'right' ? 'text-right' : 'text-left'}>
      <div className="kicker" style={{ color, letterSpacing: '0.18em' }}>
        {label}
      </div>
      <div className="num text-2xl font-bold" style={{ color: won ? color : 'var(--text)' }}>
        {score}
      </div>
    </div>
  );
}
