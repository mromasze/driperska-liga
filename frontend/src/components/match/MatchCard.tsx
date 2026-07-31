import { Link } from 'react-router-dom';
import type { MatchDetail, MatchParticipant, Role } from '../../api/types';
import { formatDate, formatDuration, roleLabel } from '../../lib/format';
import { lineupOf, matchMvp, teamKills } from '../../lib/match';
import { cn } from '../../lib/cn';
import { ChampionIcon } from '../champion/ChampionIcon';
import { PrBadge } from '../ui/PrBadge';

/** Lane labels for the card's centre column — short enough to sit between two nicknames. */
const LANE_LABEL: Record<Role, string> = {
  TOP: 'TOP',
  JUNGLE: 'JUNGLE',
  MID: 'MID',
  ADC: 'ADC',
  SUPPORT: 'SUPP',
};

/** Rich result card built from a full match detail (used on Home / results grid). */
export function MatchCard({ match }: { match: MatchDetail }) {
  const blueKills = teamKills(match, 'BLUE');
  const redKills = teamKills(match, 'RED');
  const mvp = matchMvp(match);
  // Best player of the losing side — the league awards it separately from the MVP.
  const ace = match.participants.find((p) => p.ace) ?? null;
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
            Both lineups, lane against lane, with the lane written between them: the two nicknames on
            a row are the players who actually faced each other, and saying which lane that was turns
            the block into a readable matchup instead of two lists side by side. Each side's K/D/A
            sits on the inside edge (the red row is mirrored), so both columns of numbers end up next
            to the lane label and read as one comparison.
          */}
          {lanes > 0 && (
            <div className="mt-3 space-y-1 border-t border-line pt-3">
              {Array.from({ length: lanes }).map((_, lane) => {
                const blue = blueLineup[lane];
                const red = redLineup[lane];
                const role = blue?.role ?? red?.role;
                return (
                  <div key={lane} className="flex items-center gap-1">
                    <LineupSlot player={blue} mvpId={mvp?.playerId} />
                    <span
                      className="w-[3.4rem] shrink-0 text-center text-[9px] font-semibold uppercase tracking-[0.06em] text-text-lo"
                      title={role ? roleLabel(role) : undefined}
                    >
                      {role ? LANE_LABEL[role] : ''}
                    </span>
                    <LineupSlot player={red} mvpId={mvp?.playerId} align="right" />
                  </div>
                );
              })}
            </div>
          )}

          {/* The honours line carries the ratings, which the tight lineup rows have no room for. */}
          {(mvp || ace) && (
            <div className="mt-3 flex items-center gap-3 border-t border-line pt-3">
              {mvp && <Honour player={mvp} label="MVP" icon="👑" />}
              {ace && <Honour player={ace} label="ACE" icon="🛡️" />}
            </div>
          )}
        </div>
        <SideBar won={match.winningSide === 'RED'} color="var(--red)" />
      </div>
    </Link>
  );
}

/**
 * One player in a lineup row: portrait, nickname with its honours, and K/D/A — mirrored for the red
 * side so the numbers of both players meet in the middle of the card.
 */
function LineupSlot({ player, mvpId, align = 'left' }: {
  player?: MatchParticipant;
  mvpId?: string;
  align?: 'left' | 'right';
}) {
  if (!player) return <span className="min-w-0 flex-1" />;
  // `mvp` is set when the match is scored; the highest-PR fallback covers a card rendered before that.
  const isMvp = player.mvp || player.playerId === mvpId;
  const titleParts = [player.nickname];
  if (player.championName) titleParts.push(player.championName);
  titleParts.push(`${player.kills}/${player.deaths}/${player.assists}`);
  if (isMvp) titleParts.push('MVP meczu');
  if (player.ace) titleParts.push('ACE przegranej drużyny');

  return (
    <span
      className={cn('flex min-w-0 flex-1 items-center gap-1', align === 'right' && 'flex-row-reverse')}
      title={titleParts.join(' — ')}
    >
      <ChampionIcon
        iconUrl={player.championIconUrl}
        name={player.championName}
        size={20}
        className={cn(isMvp && 'ring-[var(--gold)]')}
      />
      <span
        className={cn(
          'flex min-w-0 flex-1 items-center gap-0.5 text-[11px]',
          align === 'right' && 'flex-row-reverse',
          isMvp ? 'font-semibold text-gold' : 'text-text-lo',
        )}
      >
        <span className="truncate">{player.nickname}</span>
        {isMvp && <span aria-label="MVP meczu">👑</span>}
        {player.ace && <span aria-label="ACE przegranej drużyny">🛡️</span>}
      </span>
      <span className="num shrink-0 text-[10px] text-text-lo">
        {player.kills}<span className="opacity-50">/</span>
        <span className="text-loss">{player.deaths}</span>
        <span className="opacity-50">/</span>{player.assists}
      </span>
    </span>
  );
}

/** One honour on the card footer: who it was, on what champion, and their rating. */
function Honour({ player, label, icon }: { player: MatchParticipant; label: string; icon: string }) {
  return (
    <span className="flex min-w-0 flex-1 items-center gap-1.5">
      <ChampionIcon iconUrl={player.championIconUrl} name={player.championName} size={26} />
      <span className="min-w-0">
        <span className="flex items-center gap-1 text-[10px] text-text-lo">
          <span aria-hidden>{icon}</span>{label}
        </span>
        <span className="block truncate text-xs font-medium text-text-hi">{player.nickname}</span>
      </span>
      <span className="ml-auto shrink-0">
        <PrBadge value={player.performanceRating} size="sm" />
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
