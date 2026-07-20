import { useNavigate } from 'react-router-dom';
import type { RankingRow } from '../../api/types';
import { Table, type Column } from '../ui/Table';
import { Avatar } from '../ui/Avatar';
import { RankMedal } from '../ui/RankMedal';
import { PrBadge } from '../ui/PrBadge';
import { Sparkline } from '../ui/Sparkline';
import { fixed } from '../../lib/format';

export interface RankingTableProps {
  rows: RankingRow[];
}

/** Full league table with medals for the top 3 and sortable columns. */
export function RankingTable({ rows }: RankingTableProps) {
  const navigate = useNavigate();

  const columns: Column<RankingRow>[] = [
    {
      key: 'rank',
      header: '#',
      align: 'center',
      render: (row) => <RankMedal rank={row.rank} />,
    },
    {
      key: 'player',
      header: 'Gracz',
      render: (row) => (
        <div className="flex items-center gap-3">
          <Avatar src={row.player.avatarUrl} name={row.player.nickname} size={32} ring={row.rank === 1} />
          <span className="font-medium text-text-hi">{row.player.nickname}</span>
        </div>
      ),
    },
    {
      key: 'lp',
      header: 'LP',
      align: 'right',
      sortable: true,
      sortValue: (row) => row.totalLp,
      render: (row) => <span className="num font-semibold text-gold">{row.totalLp}</span>,
    },
    {
      key: 'wl',
      header: 'W-L',
      align: 'right',
      sortable: true,
      sortValue: (row) => row.wins,
      render: (row) => (
        <span className="num text-text">
          <span className="text-win">{row.wins}</span>
          <span className="text-text-lo"> - </span>
          <span className="text-loss">{row.losses}</span>
        </span>
      ),
    },
    {
      key: 'winrate',
      header: 'Win%',
      align: 'right',
      sortable: true,
      sortValue: (row) => row.winRate,
      render: (row) => <span className="num text-text">{fixed(row.winRate, 0)}%</span>,
    },
    {
      key: 'pr',
      header: 'Avg PR',
      align: 'right',
      sortable: true,
      sortValue: (row) => row.avgPerformanceRating,
      render: (row) => <PrBadge value={row.avgPerformanceRating} size="sm" />,
    },
    {
      key: 'mvp',
      header: 'MVP',
      align: 'right',
      sortable: true,
      sortValue: (row) => row.mvpCount,
      render: (row) => <span className="num text-gold">{row.mvpCount || '—'}</span>,
    },
    {
      key: 'form',
      header: 'Forma',
      align: 'center',
      render: (row) => <Sparkline data={row.form} />,
    },
  ];

  return (
    <Table
      columns={columns}
      data={rows}
      rowKey={(row) => row.player.id}
      initialSort={{ key: 'lp', dir: 'desc' }}
      onRowClick={(row) => navigate(`/players/${row.player.id}`)}
      emptyMessage="Brak danych rankingowych dla tego sezonu."
    />
  );
}
