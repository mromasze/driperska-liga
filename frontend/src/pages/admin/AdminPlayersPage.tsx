import { useRef, useState } from 'react';
import {
  usePlayers, useCreatePlayer, useProvisionPlayerAccount,
  useUpdatePlayer, useUploadAvatar, useResendPlayerCredentials, useSetPlayerModerator,
} from '../../api/hooks/players';
import type { CreatedPlayerResponse, LoginCredentials, Player, Role } from '../../api/types';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState, ErrorState, SectionSkeleton } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

export function AdminPlayersPage() {
  const players = usePlayers();
  const create = useCreatePlayer();
  const provision = useProvisionPlayerAccount();
  const resend = useResendPlayerCredentials();
  const update = useUpdatePlayer();
  const setModerator = useSetPlayerModerator();
  const upload = useUploadAvatar();
  const [nickname, setNickname] = useState('');
  const [mainRole, setMainRole] = useState<Role>('MID');
  const [riotId, setRiotId] = useState('');
  const [discordName, setDiscordName] = useState('');
  const [credentials, setCredentials] = useState<LoginCredentials | null>(null);
  const [copied, setCopied] = useState(false);
  const [delivery, setDelivery] = useState<{ sent: boolean; message: string } | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<{
    riotId: string; discordName: string; realName: string; mainRole: Role; secondaryRole: Role | '';
  }>({ riotId: '', discordName: '', realName: '', mainRole: 'MID', secondaryRole: '' });
  const fileInputs = useRef<Record<string, HTMLInputElement | null>>({});

  const startEdit = (p: Player) => {
    setEditingId(p.id);
    setEditForm({
      riotId: p.riotId ?? '', discordName: p.discordName ?? '', realName: p.realName ?? '',
      mainRole: p.mainRole, secondaryRole: p.secondaryRole ?? '',
    });
  };
  const saveEdit = (id: string) => {
    update.mutate({
      id,
      body: {
        riotId: editForm.riotId.trim() || null,
        discordName: editForm.discordName.trim() || undefined,
        realName: editForm.realName.trim() || null,
        mainRole: editForm.mainRole,
        secondaryRole: editForm.secondaryRole || null,
      },
    }, { onSuccess: () => setEditingId(null) });
  };

  const showCredentials = (created: CreatedPlayerResponse) => {
    setCredentials(created.credentials);
    setDelivery(created.discordDelivery);
    setCopied(false);
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!nickname.trim() || !discordName.trim()) return;
    create.mutate({
      nickname: nickname.trim(), mainRole, riotId: riotId.trim() || null, discordName: discordName.trim(),
    }, {
      onSuccess: (created) => {
        setNickname('');
        setRiotId('');
        setDiscordName('');
        showCredentials(created);
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
        <p className="mt-1 text-sm text-text-lo">
          Dodanie gracza tworzy też konto i losowe hasło. Moderator dodatkowo wprowadza rozegrane
          mecze — jego wnioski czekają w kolejce akceptacji.
        </p>
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
        <label className="min-w-48 flex-1"><span className="kicker">Discord name / User ID *</span>
          <input required value={discordName} onChange={(e) => setDiscordName(e.target.value)}
            placeholder="@nick lub numeryczne ID" className="form-control mt-1" />
        </label>
        <Button type="submit" variant="gold" disabled={create.isPending}>
          {create.isPending ? 'Tworzenie…' : 'Utwórz gracza i konto'}
        </Button>
      </form>

      {(create.isError || provision.isError || resend.isError || setModerator.isError) && (
        <div className="rounded-lg border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10 p-4 text-sm text-loss">
          {(create.error ?? provision.error ?? resend.error ?? setModerator.error)?.message}
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
          {delivery && (
            <p className={`mt-2 text-sm ${delivery.sent ? 'text-win' : 'text-loss'}`}>
              {delivery.sent ? '✓ ' : '⚠ '}{delivery.message}
            </p>
          )}
        </section>
      )}

      {players.isError ? <ErrorState error={players.error} /> : players.isLoading ? <SectionSkeleton rows={6} /> : list.length === 0 ? <EmptyState title="Brak graczy" /> : (
        <div className="space-y-2">
          {list.map((p: Player) => (
            <div key={p.id} className="glass p-3">
              <div className="flex flex-wrap items-center gap-3">
                <Avatar src={p.avatarUrl} name={p.nickname} size={44} />
                <div className="min-w-40 flex-1">
                  <div className="font-medium text-text-hi">{p.nickname}</div>
                  <div className="flex flex-wrap items-center gap-2 text-xs text-text-lo">
                    <Badge tone="gold">{roleLabel(p.mainRole)}</Badge>
                    {p.riotId ? <span className="num">{p.riotId}</span> : <span className="text-loss">brak Riot ID</span>}
                    <span>Discord: {p.discordName || '—'}</span>
                    <Badge tone={p.accountProvisioned ? 'win' : 'pending'}>
                      {p.accountProvisioned ? 'konto aktywne' : 'brak konta'}
                    </Badge>
                    {p.moderator && <Badge tone="info">moderator</Badge>}
                  </div>
                </div>
                <Button size="sm" variant="ghost" onClick={() => (editingId === p.id ? setEditingId(null) : startEdit(p))}>
                  {editingId === p.id ? 'Zwiń' : 'Edytuj'}
                </Button>
                {!p.accountProvisioned && (
                  <Button size="sm" variant="gold" disabled={provision.isPending}
                    onClick={() => provision.mutate(p.id, { onSuccess: showCredentials })}>
                    Utwórz konto
                  </Button>
                )}
                {p.accountProvisioned && (
                  <Button size="sm" variant="ghost" disabled={resend.isPending}
                    onClick={() => resend.mutate(p.id, { onSuccess: showCredentials })}>
                    Wyślij dane ponownie
                  </Button>
                )}
                {/* The permission sits on the login account, so it needs one to exist first. */}
                {p.accountProvisioned && (
                  <Button size="sm" variant="ghost" disabled={setModerator.isPending}
                    title={p.moderator
                      ? 'Odbierz prawo wprowadzania meczów do kolejki akceptacji'
                      : 'Pozwól wprowadzać rozegrane mecze do kolejki akceptacji'}
                    onClick={() => setModerator.mutate({ id: p.id, moderator: !p.moderator })}>
                    {p.moderator ? 'Odbierz moderatora' : 'Nadaj moderatora'}
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

              {editingId === p.id && (
                <div className="mt-3 grid gap-3 border-t border-line pt-3 sm:grid-cols-2 lg:grid-cols-3">
                  <label><span className="kicker">Riot ID (Nazwa#TAG)</span>
                    <input value={editForm.riotId} placeholder="Nick#EUNE" className="form-control mt-1"
                      onChange={(e) => setEditForm((f) => ({ ...f, riotId: e.target.value }))} />
                  </label>
                  <label><span className="kicker">Discord name / User ID</span>
                    <input value={editForm.discordName} className="form-control mt-1"
                      onChange={(e) => setEditForm((f) => ({ ...f, discordName: e.target.value }))} />
                  </label>
                  <label><span className="kicker">Imię (opc.)</span>
                    <input value={editForm.realName} className="form-control mt-1"
                      onChange={(e) => setEditForm((f) => ({ ...f, realName: e.target.value }))} />
                  </label>
                  <label><span className="kicker">Rola główna</span>
                    <select value={editForm.mainRole} className="form-control mt-1"
                      onChange={(e) => setEditForm((f) => ({ ...f, mainRole: e.target.value as Role }))}>
                      {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
                    </select>
                  </label>
                  <label><span className="kicker">Rola dodatkowa</span>
                    <select value={editForm.secondaryRole} className="form-control mt-1"
                      onChange={(e) => setEditForm((f) => ({ ...f, secondaryRole: e.target.value as Role | '' }))}>
                      <option value="">—</option>
                      {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
                    </select>
                  </label>
                  <div className="flex items-end gap-2">
                    <Button size="sm" variant="gold" disabled={update.isPending} onClick={() => saveEdit(p.id)}>
                      {update.isPending ? 'Zapisywanie…' : 'Zapisz'}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}>Anuluj</Button>
                  </div>
                  <p className="text-xs text-text-lo sm:col-span-2 lg:col-span-3">
                    Login gracza (nick) i hasło zmienisz przez „Wyślij dane ponownie”. Zmiana Discorda wyśle dane od nowa przy następnym generowaniu hasła.
                  </p>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}