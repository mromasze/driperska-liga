import { useMemo } from 'react';
import { useChampions } from '../../api/hooks/champions';
import type { Champion, DrawLobby, LobbyPlayer } from '../../api/types';
import { Avatar } from '../ui/Avatar';
import { ChampionIcon } from '../champion/ChampionIcon';
import { roleLabel } from '../../lib/format';

const ROLE_ORDER: Record<string, number> = { TOP: 0, JUNGLE: 1, MID: 2, ADC: 3, SUPPORT: 4 };

/** Final "who plays what" — each player with their picked champion. Shown once the game starts. */
export function GameLineup({ lobby, myPlayerId }: { lobby: DrawLobby; myPlayerId?: string }) {
  const champions = useChampions();
  const champById = useMemo(() => {
    const map = new Map<number, Champion>();
    (champions.data ?? []).forEach((c) => map.set(c.id, c));
    return map;
  }, [champions.data]);

  const order = (players: LobbyPlayer[], ids: string[] | undefined) =>
    [...players].sort((a, b) => {
      if (ids && ids.length) return ids.indexOf(a.playerId) - ids.indexOf(b.playerId);
      return (ROLE_ORDER[a.role] ?? 9) - (ROLE_ORDER[b.role] ?? 9);
    });

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Team title="Niebiescy" color="var(--blue)" players={order(lobby.blue, lobby.draft?.blueOrder)}
        champById={champById} myPlayerId={myPlayerId} />
      <Team title="Czerwoni" color="var(--red)" players={order(lobby.red, lobby.draft?.redOrder)}
        champById={champById} myPlayerId={myPlayerId} />
    </div>
  );
}

function Team({ title, color, players, champById, myPlayerId }: {
  title: string; color: string; players: LobbyPlayer[];
  champById: Map<number, Champion>; myPlayerId?: string;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-[color:var(--bg-1)]/80">
      <div className="px-4 py-3 font-display font-semibold"
        style={{ color, background: `color-mix(in srgb, ${color} 12%, transparent)` }}>{title}</div>
      <div className="divide-y divide-line">
        {players.map((p) => {
          const champ = p.championId != null ? champById.get(p.championId) : undefined;
          return (
            <div key={p.playerId} className="flex items-center gap-3 px-4 py-2.5">
              <ChampionIcon iconUrl={champ?.iconUrl} name={champ?.name} size={40} />
              <Avatar src={p.avatarUrl} name={p.nickname} size={26} ring={p.playerId === myPlayerId} />
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium text-text-hi">
                  {p.nickname}{p.playerId === myPlayerId && <span className="kicker text-cyan"> (Ty)</span>}
                </div>
                <div className="text-xs text-text-lo">{roleLabel(p.role)}{champ ? ` · ${champ.name}` : ''}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
