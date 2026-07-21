import { useRef, useState } from 'react';
import {
  usePlayers, useCreatePlayer, useProvisionPlayerAccount,
  useUpdatePlayer, useUploadAvatar,
} from '../../api/hooks/players';
import type { LoginCredentials, Player, Role } from '../../api/types';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadingState, EmptyState } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

export function AdminPlayersPage() {
  const players = usePlayers();
  const create = useCreatePlayer();
  const provision = useProvisionPlayerAccount();
  const update = useUpdatePlayer();
  const upload = useUploadAvatar();
  const [nickname, setNickname] = useState('');
  const [mainRole, setMainRole] = useState<Role>('MID');
  const [riotId, setRiotId] = useState('');
  const [credentials, setCredentials] = useState<LoginCredentials | null>(null);
  const [copied, setCopied] = useState(false);
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  const showCredentials = (value: LoginCredentials) => {
    setCredentials(value);
    setCopied(false);
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!nickname.trim()) return;
    create.mutate({ nickname: nickname.trim(), mainRole, riotId: riotId.trim() || null }, {
      onSuccess: (created) => {
        setNickname('');
        setRiotId('');
        showCredentials(created.credentials);
      },
    });
  };

  const copyMessage = async () => {
    if (!credentials) return;
    await navigator.clipboard.writeText(credentials.messageTemplate);
    setCopied(true);
  };

  const list = players.data?.content ?? [];
  return (
    <div className="space-y-8">
      <div>
        <div className="kicker text-gold">Konta i dostęp</div>
        <h1 className="font-display text-3xl">Gracze</h1>
        <p className="mt-1 text-sm text-text-lo">Dodanie gracza tworzy też konto i losowe hasło.</p>
      </div>

      <form onSubmit={submit} className="panel flex flex-wrap items-end gap-3 p-4">
        <label className="min-w-48 flex-1"><span className="kicker">Nick = login</span>
          <input value={nickname} onChange={(e) => setNickname(e.target.value)} className="form-control mt-1" />
        </label>
        <label><span className="kicker">Rola</span>
          <select value={mainRole} onChange={(e) => setMainRole(e.target.value as Role)} className="form-control mt-1">
            {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
          </select>
        </label>
        <label className="min-w-48 flex-1"><span className="kicker">Riot ID (opc.)</span>
          <input value={riotId} onChange={(e) => setRiotId(e.target.value)} placeholder="Nick#EUW" className="form-control mt-1" />
        </label>
        <Button type="submit" variant="gold" disabled={create.isPending}>
          {create.isPending ? 'Tworzenie…' : 'Utwórz gracza i konto'}
        </Button>
      </form>

      {(create.isError || provision.isError) && (
        <div className="rounded-lg border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10 p-4 text-sm text-loss">
          {(create.error ?? provision.error)?.message}
        </div>
      )}

      {credentials && (
        <section className="rounded-xl border border-[color:var(--win)]/40 bg-[color:var(--win)]/8 p-5">
          <div className="mb-3 flex items-center justify-between gap-3">
            <div><div className="kicker text-win">Konto gotowe</div><h2 className="font-display text-xl">Wiadomość na DM</h2></div>
            <Button variant="gold" onClick={copyMessage}>{copied ? '✓ Skopiowano' : 'Kopiuj wiadomość'}</Button>
          </div>
          <textarea readOnly value={credentials.messageTemplate} rows={8}
            onFocus={(event) => event.currentTarget.select()}
            className="w-full resize-none rounded-lg border border-line bg-bg-1 p-4 font-mono text-sm leading-6 text-text-hi" />
          <p className="mt-2 text-xs text-text-lo">Hasło nie jest nigdzie przechowywane w jawnej postaci — skopiuj je teraz.</p>
        </section>
      )}

      {players.isLoading ? <LoadingState /> : list.length === 0 ? <EmptyState title="Brak graczy" /> : (
        <div className="space-y-2">
          {list.map((p: Player) => (
            <div key={p.id} className="glass flex flex-wrap items-center gap-3 p-3">
              <Avatar src={p.avatarUrl} name={p.nickname} size={44} />
              <div className="min-w-40 flex-1">
                <div className="font-medium text-text-hi">{p.nickname}</div>
                <div className="flex items-center gap-2 text-xs text-text-lo">
                  <Badge tone="gold">{roleLabel(p.mainRole)}</Badge>
                  {p.riotId && <span className="num">{p.riotId}</span>}
                  <Badge tone={p.accountProvisioned ? 'win' : 'pending'}>
                    {p.accountProvisioned ? 'konto aktywne' : 'brak konta'}
                  </Badge>
                </div>
              </div>
              {!p.accountProvisioned && (
                <Button size="sm" variant="gold" disabled={provision.isPending}
                  onClick={() => provision.mutate(p.id, { onSuccess: (created) => showCredentials(created.credentials) })}>
                  Utwórz konto
                </Button>
              )}
              <input ref={(element) => { fileInputs.current[p.id] = element; }} type="file"
                accept="image/png,image/jpeg,image/webp" className="hidden"
                onChange={(event) => { const file = event.target.files?.[0]; if (file) upload.mutate({ id: p.id, file }); }} />
              <Button size="sm" variant="ghost" onClick={() => fileInputs.current[p.id]?.click()}>Zdjęcie</Button>
              <Button size="sm" variant="ghost" onClick={() => update.mutate({ id: p.id, body: { active: !p.active } })}>
                {p.active ? 'Dezaktywuj' : 'Aktywuj'}
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}