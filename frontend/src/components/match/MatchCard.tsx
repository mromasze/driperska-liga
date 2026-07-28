import { Link } from 'react-router-dom';
import type { MatchDetail, MatchParticipant } from '../../api/types';
import { formatDate, formatDuration } from '../../lib/format';
import { lineupOf, matchMvp, teamKills } from '../../lib/match';
import { cn } from '../../lib/cn';
import { ChampionIcon } from '../champion/ChampionIcon';
import { PrBadge } from '../ui/PrBadge';

/** Rich result card built from a full match detail (used on Home / results grid). */
export function MatchCard({ match }: { match: MatchDetail }) {
  const blueKills = teamKills(match, 'BLUE');
  const redKills = teamKills(match, 'RED');
  const mvp = matchMvp(match);
  const blueWon = match.winningSide === 'BLUE';
  const blueLineup = lineupOf(match, 'BLUE');
  const redLineup = lineupOf(match, 'RED');
  const lanes = Math.max(blueLineup.length, redLineup.length);

  return (
    <Link to={`/matches/${match.id}`} className="glass lift block overflow-hidden p-0">
      <div className="flex items-stretch">
        <SideBar won={blueWon} color="var(--blue)" />
        <div className="min-w-0 flex-1 p-4">
          <div className="flex items-center justify-between text-xs text-text-lo">
            <span className="num">{formatDate(match.startedAt ?? match.completedAt ?? match.createdAt)}</span>
            <span className="num">{formatDuration(match.durationSeconds)}</span>
          </div>

          <div className="mt-2 flex items-center justify-center gap-3">
            <TeamScore label="BLUE" score={blueKills} color="var(--blue)" won={blueWon} />
            <span className="font-display text-sm text-text-lo">:</span>
            <TeamScore label="RED" score={redKills} color="var(--red)" won={match.winningSide === 'RED'} align="right" />
          </div>

          {/*
            Both lineups, lane against lane. A scoreline alone never told you *which* match you were
            looking at — the portraits and nicknames do, at a glance, before the card is even clicked.
          */}
          {lanes > 0 && (
            <div className="mt-3 space-y-1 border-t border-line pt-3">
              {Array.from({ length: lanes }).map((_, lane) => (
                <div key={lane} className="flex items-center gap-2">
                  <LineupSlot player={blueLineup[lane]} mvpId={mvp?.playerId} />
                  <LineupSlot player={redLineup[lane]} mvpId={mvp?.playerId} align="right" />
                </div>
              ))}
            </div>
          )}

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

/** One player in a lineup row: portrait plus nickname, mirrored for the red side. */
function LineupSlot({ player, mvpId, align = 'left' }: {
  player?: MatchParticipant;
  mvpId?: string;
  align?: 'left' | 'right';
}) {
  if (!player) return <span className="min-w-0 flex-1" />;
  const isMvp = player.playerId === mvpId;
  return (
    <span
      className={cn('flex min-w-0 flex-1 items-center gap-1.5', align === 'right' && 'flex-row-reverse')}
      title={`${player.nickname}${player.championName ? ` — ${player.championName}` : ''}`}
    >
      <ChampionIcon
        iconUrl={player.championIconUrl}
        name={player.championName}
        size={20}
        className={cn(isMvp && 'ring-[var(--gold)]')}
      />
      <span className={cn('truncate text-[11px]', isMvp ? 'font-semibold text-gold' : 'text-text-lo')}>
        {player.nickname}
      </span>
    </span>
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
