import type { MatchDetail } from '../../api/types';
import { TeamColumn } from './TeamColumn';
import { Badge } from '../ui/Badge';
import { formatDuration } from '../../lib/format';

export interface ScoreboardProps {
  match: MatchDetail;
}

/** End-of-game style scoreboard: two TeamColumns (BLUE / RED) (docs/06 §6.6). */
export function Scoreboard({ match }: ScoreboardProps) {
  const blue = match.participants.filter((p) => p.side === 'BLUE');
  const red = match.participants.filter((p) => p.side === 'RED');

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-center gap-3 text-center">
        <span className="num text-3xl font-bold text-text-hi">
          {match.blueScore} : {match.redScore}
        </span>
        <span className="text-sm text-text-lo">{formatDuration(match.durationSeconds)}</span>
        {match.patch && <Badge tone="neutral">patch {match.patch}</Badge>}
        {match.balance && (
          <Badge tone="info">
            szansa BLUE {Math.round(match.balance.predictedBlueWinPct)}% / RED{' '}
            {Math.round(100 - match.balance.predictedBlueWinPct)}%
          </Badge>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <TeamColumn side="BLUE" participants={blue} won={match.winningSide === 'BLUE'} />
        <TeamColumn side="RED" participants={red} won={match.winningSide === 'RED'} />
      </div>
    </div>
  );
}
