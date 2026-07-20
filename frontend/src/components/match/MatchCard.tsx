import { Link } from 'react-router-dom';
import type { MatchSummary } from '../../api/types';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { ChampionIcon } from '../champion/ChampionIcon';
import { PrBadge } from '../ui/PrBadge';
import { formatDateTime, formatDuration } from '../../lib/format';
import { cn } from '../../lib/cn';

export interface MatchCardProps {
  match: MatchSummary;
}

/** Recent-match card: BLUE vs RED, score, time and MVP (docs/06 §6.5). */
export function MatchCard({ match }: MatchCardProps) {
  const blueWon = match.winningSide === 'BLUE';
  const redWon = match.winningSide === 'RED';

  return (
    <Link to={`/matches/${match.id}`} className="block">
      <Card interactive className="overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4">
          <TeamScore side="BLUE" score={match.blueScore} won={blueWon} />
          <div className="px-3 text-center">
            <div className="text-xs uppercase tracking-wide text-text-lo">vs</div>
            <div className="num text-lg font-bold text-text-hi">
              {match.blueScore} : {match.redScore}
            </div>
            <div className="num text-xs text-text-lo">{formatDuration(match.durationSeconds)}</div>
          </div>
          <TeamScore side="RED" score={match.redScore} won={redWon} align="right" />
        </div>

        <div className="flex items-center justify-between border-t border-line px-5 py-2.5">
          <span className="text-xs text-text-lo">
            {formatDateTime(match.completedAt ?? match.createdAt)}
          </span>
          {match.mvp ? (
            <span className="flex items-center gap-2">
              <Badge tone="gold">MVP</Badge>
              <ChampionIcon
                src={match.mvp.championIconUrl}
                name={match.mvp.championSlug}
                size={22}
              />
              <span className="max-w-[8rem] truncate text-xs text-text">{match.mvp.nickname}</span>
              <PrBadge value={match.mvp.performanceRating} size="sm" />
            </span>
          ) : (
            <Badge tone="pending">{match.status}</Badge>
          )}
        </div>
      </Card>
    </Link>
  );
}

function TeamScore({
  side,
  won,
  align = 'left',
}: {
  side: 'BLUE' | 'RED';
  score: number;
  won: boolean;
  align?: 'left' | 'right';
}) {
  return (
    <div className={cn('flex-1', align === 'right' && 'text-right')}>
      <div
        className="text-sm font-semibold"
        style={{ color: side === 'BLUE' ? 'var(--blue)' : 'var(--red)' }}
      >
        {side === 'BLUE' ? 'Niebiescy' : 'Czerwoni'}
      </div>
      <div className="mt-1 text-xs">
        {won ? (
          <span className="text-win">● Wygrana</span>
        ) : (
          <span className="text-text-lo">Przegrana</span>
        )}
      </div>
    </div>
  );
}
