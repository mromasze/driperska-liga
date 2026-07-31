import { Link } from 'react-router-dom';
import type { RankingRow } from '../../api/types';
import { Avatar } from '../ui/Avatar';
import { PrBadge } from '../ui/PrBadge';
import { RankMedal } from '../ui/RankMedal';
import { podiumOf } from '../../lib/podium';

export function RankingTable({ rows }: { rows: RankingRow[] }) {
  return (
    <div className="glass overflow-x-auto">
      <table className="w-full min-w-[680px] border-collapse text-sm">
        <thead>
          <tr className="kicker border-b border-line text-left">
            <th className="px-4 py-3 font-semibold">#</th>
            <th className="px-4 py-3 font-semibold">Gracz</th>
            <th className="px-3 py-3 text-right font-semibold">Wynik</th>
            <th className="px-3 py-3 text-right font-semibold">Σ LP</th>
            <th className="px-3 py-3 text-center font-semibold">Bilans</th>
            <th className="px-3 py-3 text-right font-semibold">Win%</th>
            <th className="px-3 py-3 text-center font-semibold">Avg PR</th>
            <th className="px-3 py-3 text-right font-semibold">MVP</th>
            <th className="px-3 py-3 text-right font-semibold">ACE</th>
            <th className="px-4 py-3 text-right font-semibold">MMR</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            // The top three are framed in their own metal: a left rail plus a wash of the same
            // colour, so a podium row is recognisable from the shape of the table alone.
            const podium = podiumOf(r.rank);
            return (
            <tr
              key={r.playerId}
              className="border-b border-line/60 transition-colors hover:bg-[var(--glass)]"
              style={podium ? {
                background: `linear-gradient(90deg, color-mix(in srgb, ${podium.color} 12%, transparent), transparent 42%)`,
                boxShadow: `inset 3px 0 0 0 ${podium.color}`,
              } : undefined}
            >
              <td className="px-4 py-3">
                <RankMedal rank={r.rank} />
              </td>
              <td className="px-4 py-3">
                <Link to={`/players/${r.playerId}`} className="flex items-center gap-3 hover:text-gold">
                  <Avatar
                    src={r.avatarUrl}
                    name={r.nickname}
                    size={34}
                    style={podium ? { boxShadow: `0 0 0 2px ${podium.color}, 0 0 16px -4px ${podium.glow}` } : undefined}
                  />
                  <span>
                    <span className="font-medium text-text-hi">{r.nickname}</span>
                    {!r.qualified && (
                      <span className="ml-2 text-xs text-text-lo" title="Do klasyfikacji potrzeba 5 meczów">
                        prowizoryczny
                      </span>
                    )}
                  </span>
                </Link>
              </td>
              <td className="px-3 py-3 text-right">
                <div className="num text-base font-bold text-gold">{r.rankingScore.toFixed(2)}</div>
                <div className="num text-[10px] text-text-lo" title="Wynik bazowy + bonus za rozegrane mecze">
                  {r.baseScore.toFixed(2)} + akt. {r.activityBonus.toFixed(2)}
                </div>
              </td>
              <td className="num px-3 py-3 text-right text-text-lo">{r.totalLp}</td>
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
              <td className="num px-3 py-3 text-right">{r.aceCount || '—'}</td>
              <td className="num px-4 py-3 text-right text-text-lo">{Math.round(r.mmr)}</td>
            </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
