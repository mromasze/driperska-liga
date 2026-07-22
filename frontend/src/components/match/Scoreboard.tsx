import type { MatchDetail } from '../../api/types';
import { formatDuration } from '../../lib/format';
import { teamKills, teamOf } from '../../lib/match';
import { TeamColumn } from './TeamColumn';

export function Scoreboard({ match }: { match: MatchDetail }) {
  const blue = teamOf(match, 'BLUE');
  const red = teamOf(match, 'RED');
  const blueKills = teamKills(match, 'BLUE');
  const redKills = teamKills(match, 'RED');
  const decided = match.winningSide != null;

  return (
    <div>
      <div className="mb-4 flex items-center justify-center gap-4 sm:gap-8">
        <ScoreSide label="Niebiescy" score={blueKills} color="var(--blue)" win={match.winningSide === 'BLUE'} />
        <div className="text-center">
          <div className="num text-xs text-text-lo">{formatDuration(match.durationSeconds)}</div>
          <div className="font-display text-2xl text-text-lo">VS</div>
          {match.patch && <div className="num text-[11px] text-text-lo">patch {match.patch}</div>}
        </div>
        <ScoreSide label="Czerwoni" score={redKills} color="var(--red)" win={match.winningSide === 'RED'} align="right" />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <TeamColumn side="BLUE" players={blue} won={decided ? match.winningSide === 'BLUE' : null} teamKills={blueKills} />
        <TeamColumn side="RED" players={red} won={decided ? match.winningSide === 'RED' : null} teamKills={redKills} />
      </div>
    </div>
  );
}

function ScoreSide({
  label,
  score,
  color,
  win,
  align = 'left',
}: {
  label: string;
  score: number;
  color: string;
  win: boolean;
  align?: 'left' | 'right';
}) {
  return (
    <div className={align === 'right' ? 'text-right' : 'text-left'}>
      <div className="kicker" style={{ color }}>
        {label} {win && '· 🏆'}
      </div>
      <div className="num text-4xl font-bold sm:text-5xl" style={{ color: win ? color : 'var(--text-hi)' }}>
        {score}
      </div>
    </div>
  );
}
