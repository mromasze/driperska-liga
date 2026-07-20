import { Link } from 'react-router-dom';
import type { Player } from '../../api/types';
import { Card } from '../ui/Card';
import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';
import { roleLabel } from '../../lib/format';

export interface PlayerCardProps {
  player: Player;
}

/** Grid card: avatar, nick, main role, LP (docs/06 §6.5). */
export function PlayerCard({ player }: PlayerCardProps) {
  return (
    <Link to={`/players/${player.id}`} className="block">
      <Card interactive className="flex items-center gap-4 px-4 py-4">
        <Avatar src={player.avatarUrl} name={player.nickname} size={56} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-base font-semibold text-text-hi">
              {player.nickname}
            </span>
            {!player.active && <Badge tone="neutral">nieaktywny</Badge>}
          </div>
          {player.riotId && <div className="truncate text-xs text-text-lo">{player.riotId}</div>}
          <div className="mt-2 flex items-center gap-2">
            <Badge tone="info">{roleLabel(player.mainRole)}</Badge>
            {player.lp != null && (
              <span className="num text-sm text-gold">{player.lp} LP</span>
            )}
          </div>
        </div>
      </Card>
    </Link>
  );
}
