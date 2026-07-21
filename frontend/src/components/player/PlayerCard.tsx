import { Link } from 'react-router-dom';
import type { Player } from '../../api/types';
import { roleLabel } from '../../lib/format';
import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';

export function PlayerCard({ player }: { player: Player }) {
  return (
    <Link to={`/players/${player.id}`} className="glass lift flex items-center gap-4 p-4">
      <Avatar src={player.avatarUrl} name={player.nickname} size={56} ring />
      <div className="min-w-0 flex-1">
        <div className="truncate font-display text-lg font-semibold text-text-hi">
          {player.nickname}
        </div>
        <div className="mt-0.5 flex items-center gap-2 text-xs text-text-lo">
          <Badge tone="gold">{roleLabel(player.mainRole)}</Badge>
          {player.riotId && <span className="num truncate">{player.riotId}</span>}
        </div>
      </div>
      {!player.active && <Badge tone="default">nieaktywny</Badge>}
    </Link>
  );
}
