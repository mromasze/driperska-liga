import { Link } from 'react-router-dom';
import type { RankingRow } from '../../api/types';
import { Avatar } from '../ui/Avatar';
import { PrBadge } from '../ui/PrBadge';
import { RankMedal } from '../ui/RankMedal';
import { podiumFrame, podiumOf } from '../../lib/podium';

/**
 * The season leader, across the full width of the page.
 *
 * Used to be a stat tile with a nickname in it, which said who was winning but nothing about why. A
 * banner has room for the face and the whole line of numbers behind the position, and the whole thing
 * is one link into the profile — the natural next click after reading it.
 */
export function LeaderCard({ leader, seasonName }: { leader: RankingRow; seasonName?: string }) {
  const look = podiumOf(1);
  return (
    <Link
      to={`/players/${leader.playerId}`}
      className="glass lift group relative block overflow-hidden p-5 sm:p-6"
      style={podiumFrame(1)}
    >
      {/* Warm bloom behind the avatar — the leader's box should feel lit, not just outlined. */}
      <span
        aria-hidden
        className="pointer-events-none absolute -left-10 -top-16 h-56 w-56 rounded-full opacity-60 blur-3xl"
        style={{ background: `radial-gradient(closest-side, ${look?.glow}, transparent)` }}
      />
      <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center">
        <div className="flex items-center gap-4">
          <span className="relative shrink-0">
            <Avatar src={leader.avatarUrl} name={leader.nickname} size={72} ring />
            <RankMedal rank={1} className="absolute -bottom-1 -right-1 ring-2 ring-[color:var(--bg-1)]" />
          </span>
          <div className="min-w-0">
            <div className="kicker text-gold">Lider {seasonName ?? 'sezonu'}</div>
            <div className="truncate font-display text-3xl text-text-hi group-hover:text-gold sm:text-4xl">
              {leader.nickname}
            </div>
            <div className="num mt-1 text-xs text-text-lo">
              wynik {leader.rankingScore.toFixed(2)}
              <span className="opacity-60"> ({leader.baseScore.toFixed(2)} + akt. {leader.activityBonus.toFixed(2)})</span>
              {!leader.qualified && (
                <span className="ml-2" title="Do klasyfikacji potrzeba 5 meczów">· prowizoryczny</span>
              )}
            </div>
          </div>
        </div>

        {/* The numbers that put them there. Wraps to two rows on a phone rather than shrinking. */}
        <div className="grid flex-1 grid-cols-3 gap-x-4 gap-y-3 sm:grid-cols-6">
          <LeaderStat label="Σ LP" value={leader.totalLp} accent />
          <LeaderStat label="Mecze" value={leader.games} />
          <LeaderStat
            label="Bilans"
            value={
              <>
                <span className="text-win">{leader.wins}</span>
                <span className="text-text-lo">-</span>
                <span className="text-loss">{leader.losses}</span>
              </>
            }
          />
          <LeaderStat label="Win%" value={`${Math.round(leader.winRate * 100)}%`} />
          <LeaderStat label="Śr. PR" value={<PrBadge value={leader.avgPerformanceRating} size="sm" />} />
          <LeaderStat label="MVP / ACE" value={`${leader.mvpCount} / ${leader.aceCount}`} />
        </div>
      </div>
    </Link>
  );
}

function LeaderStat({ label, value, accent }: { label: string; value: React.ReactNode; accent?: boolean }) {
  return (
    <div>
      <div className="kicker text-[0.6rem]">{label}</div>
      <div className={`num mt-0.5 text-lg font-bold ${accent ? 'text-gradient-gold' : 'text-text-hi'}`}>
        {value}
      </div>
    </div>
  );
}
