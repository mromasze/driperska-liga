import { Link } from 'react-router-dom';
import type { RankingRow } from '../../api/types';
import { cn } from '../../lib/cn';
import { Avatar } from '../ui/Avatar';
import { PrBadge } from '../ui/PrBadge';
import { RankMedal } from '../ui/RankMedal';

export function RankingTable({ rows }: { rows: RankingRow[] }) {
  return (
    <div className="glass overflow-x-auto">
      <table className="w-full min-w-[680px] border-collapse text-sm">
        <thead>
          <tr className="kicker border-b border-line text-left">
            <th className="px-4 py-3 font-semibold">#</th>
            <th className="px-4 py-3 font-semibold">Gracz</th>
            <th className="px-3 py-3 text-right font-semibold">LP</th>
            <th className="px-3 py-3 text-center font-semibold">Bilans</th>
            <th className="px-3 py-3 text-right font-semibold">Win%</th>
            <th className="px-3 py-3 text-center font-semibold">Avg PR</th>
            <th className="px-3 py-3 text-right font-semibold">MVP</th>
            <th className="px-4 py-3 text-right font-semibold">MMR</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr
              key={r.playerId}
              className={cn(
                'border-b border-line/60 transition-colors hover:bg-[var(--glass)]',
                r.rank <= 3 && 'bg-[color:var(--gold)]/[0.03]',
              )}
            >
              <td className="px-4 py-3">
                <RankMedal rank={r.rank} />
              </td>
              <td className="px-4 py-3">
                <Link to={`/players/${r.playerId}`} className="flex items-center gap-3 hover:text-gold">
                  <Avatar src={r.avatarUrl} name={r.nickname} size={34} ring={r.rank <= 3} />
                  <span className="font-medium text-text-hi">{r.nickname}</span>
                </Link>
              </td>
              <td className="num px-3 py-3 text-right text-base font-bold text-gold">{r.totalLp}</td>
              <td className="num px-3 py-3 text-center text-text-lo">
                <span className="text-win">{r.wins}</span>
                <span className="text-text-lo"> - </span>
                <span className="text-loss">{r.losses}</span>
              </td>
              <td className="num px-3 py-3 text-right">{Math.round(r.winRate * 100)}%</td>
              <td className="px-3 py-3 text-center">
                <PrBadge value={r.avgPerformanceRating} size="sm" />
              </td>
              <td className="num px-3 py-3 text-right">{r.mvpCount || '—'}</td>
              <td className="num px-4 py-3 text-right text-text-lo">{Math.round(r.mmr)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
