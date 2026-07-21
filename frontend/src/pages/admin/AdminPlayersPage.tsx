import { useRef, useState } from 'react';
import { usePlayers, useCreatePlayer, useUpdatePlayer, useUploadAvatar } from '../../api/hooks/players';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadingState, EmptyState } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';
import type { Player, Role } from '../../api/types';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

export function AdminPlayersPage() {
  const players = usePlayers();
  const create = useCreatePlayer();
  const update = useUpdatePlayer();
  const upload = useUploadAvatar();

  const [nickname, setNickname] = useState('');
  const [mainRole, setMainRole] = useState<Role>('MID');
  const [riotId, setRiotId] = useState('');
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!nickname.trim()) return;
    create.mutate(
      { nickname: nickname.trim(), mainRole, riotId: riotId.trim() || null },
      {
        onSuccess: () => {
          setNickname('');
          setRiotId('');
        },
      },
    );
  };

  const list = players.data?.content ?? [];

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl">Gracze</h1>

      <form onSubmit={submit} className="panel flex flex-wrap items-end gap-3 p-4">
        <label className="flex-1">
          <span className="kicker">Nick</span>
          <input
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            className="mt-1 h-10 w-full rounded-md border border-line bg-bg-1 px-3 text-text-hi"
          />
        </label>
        <label>
          <span className="kicker">Rola</span>
          <select
            value={mainRole}
            onChange={(e) => setMainRole(e.target.value as Role)}
            className="mt-1 h-10 rounded-md border border-line bg-bg-1 px-3 text-text-hi"
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {roleLabel(r)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex-1">
          <span className="kicker">Riot ID (opc.)</span>
          <input
            value={riotId}
            onChange={(e) => setRiotId(e.target.value)}
            placeholder="Nick#EUW"
            className="mt-1 h-10 w-full rounded-md border border-line bg-bg-1 px-3 text-text-hi placeholder:text-text-lo"
          />
        </label>
        <Button type="submit" variant="gold" disabled={create.isPending}>
          Dodaj gracza
        </Button>
      </form>

      {players.isLoading ? (
        <LoadingState />
      ) : list.length === 0 ? (
        <EmptyState title="Brak graczy" />
      ) : (
        <div className="space-y-2">
          {list.map((p: Player) => (
            <div key={p.id} className="glass flex items-center gap-3 p-3">
              <Avatar src={p.avatarUrl} name={p.nickname} size={44} />
              <div className="min-w-0 flex-1">
                <div className="font-medium text-text-hi">{p.nickname}</div>
                <div className="flex items-center gap-2 text-xs text-text-lo">
                  <Badge tone="gold">{roleLabel(p.mainRole)}</Badge>
                  {p.riotId && <span className="num">{p.riotId}</span>}
                </div>
              </div>
              <input
                ref={(el) => {
                  fileInputs.current[p.id] = el;
                }}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) upload.mutate({ id: p.id, file });
                }}
              />
              <Button size="sm" variant="ghost" onClick={() => fileInputs.current[p.id]?.click()}>
                Zdjęcie
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => update.mutate({ id: p.id, body: { active: !p.active } })}
              >
                {p.active ? 'Dezaktywuj' : 'Aktywuj'}
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
