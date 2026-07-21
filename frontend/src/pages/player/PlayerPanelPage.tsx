import { useEffect, useRef, useState } from 'react';
import { useChampions } from '../../api/hooks/champions';
import { useDrawLobby, useVoteOnDraw } from '../../api/hooks/drawLobby';
import { useMyPlayer, useUpdateMyPlayer, useUploadMyAvatar } from '../../api/hooks/players';
import type { DrawLobby, LobbyPlayer, Role } from '../../api/types';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ErrorState, LoadingState } from '../../components/ui/States';
import { roleLabel } from '../../lib/format';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

export function PlayerPanelPage() {
  const player = useMyPlayer();
  const draw = useDrawLobby();
  const vote = useVoteOnDraw();
  const update = useUpdateMyPlayer();
  const upload = useUploadMyAvatar();
  const champions = useChampions();
  const fileInput = useRef<HTMLInputElement>(null);

  const [mainRole, setMainRole] = useState<Role>('MID');
  const [secondaryRole, setSecondaryRole] = useState<Role | ''>('');
  const [riotId, setRiotId] = useState('');
  const [opggLink, setOpggLink] = useState('');
  const [bio, setBio] = useState('');
  const [favorites, setFavorites] = useState<number[]>([]);

  useEffect(() => {
    if (!player.data) return;
    setMainRole(player.data.mainRole);
    setSecondaryRole(player.data.secondaryRole ?? '');
    setRiotId(player.data.riotId ?? '');
    setOpggLink(player.data.opggLink ?? '');
    setBio(player.data.bio ?? '');
    setFavorites(player.data.favoriteChampionIds ?? []);
  }, [player.data]);

  if (player.isLoading) return <LoadingState />;
  if (player.isError || !player.data) return <ErrorState error={player.error} />;

  const p = player.data;
  const lobby = draw.data;
  const myVote = lobby?.acceptedPlayerIds.includes(p.id)
    ? 'ACCEPT'
    : lobby?.rejectedPlayerIds.includes(p.id) ? 'REJECT' : null;

  const toggleChampion = (id: number) => {
    setFavorites((current) => current.includes(id)
      ? current.filter((value) => value !== id)
      : current.length < 5 ? [...current, id] : current);
  };

  const save = (event: React.FormEvent) => {
    event.preventDefault();
    update.mutate({
      mainRole,
      secondaryRole: secondaryRole || null,
      riotId: riotId.trim() || null,
      opggLink: opggLink.trim() || null,
      bio: bio.trim() || null,
      favoriteChampionIds: favorites,
    });
  };

  return (
    <div className="space-y-10">
      <header>
        <div className="kicker text-gold">Zalogowano jako {p.nickname}</div>
        <h1 className="mt-1 font-display text-4xl">Centrum gracza</h1>
      </header>

      <DrawVotingCard
        lobby={lobby}
        myPlayerId={p.id}
        myVote={myVote}
        pending={vote.isPending}
        onVote={(decision) => lobby && vote.mutate({ matchId: lobby.matchId, decision })}
      />

      <section className="panel p-5 sm:p-7">
        <div className="mb-6 flex flex-wrap items-center gap-4">
          <Avatar src={p.avatarUrl} name={p.nickname} size={72} ring />
          <div className="flex-1">
            <h2 className="font-display text-2xl">Twój profil</h2>
            <p className="text-sm text-text-lo">Ustaw role, ulubionych bohaterów, zdjęcie i OP.GG.</p>
          </div>
          <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) upload.mutate(file);
            }} />
          <Button variant="ghost" onClick={() => fileInput.current?.click()} disabled={upload.isPending}>
            {upload.isPending ? 'Wysyłanie…' : 'Zmień zdjęcie'}
          </Button>
        </div>

        <form onSubmit={save} className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Główna rola">
              <select value={mainRole} onChange={(e) => setMainRole(e.target.value as Role)} className="form-control">
                {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
              </select>
            </Field>
            <Field label="Druga rola">
              <select value={secondaryRole} onChange={(e) => setSecondaryRole(e.target.value as Role | '')} className="form-control">
                <option value="">Brak</option>
                {ROLES.map((role) => <option key={role} value={role}>{roleLabel(role)}</option>)}
              </select>
            </Field>
            <Field label="Riot ID">
              <input value={riotId} onChange={(e) => setRiotId(e.target.value)} placeholder="Nick#EUW" className="form-control" />
            </Field>
            <Field label="Link do OP.GG">
              <input type="url" value={opggLink} onChange={(e) => setOpggLink(e.target.value)} placeholder="https://op.gg/..." className="form-control" />
            </Field>
          </div>
          <Field label="O mnie">
            <textarea value={bio} onChange={(e) => setBio(e.target.value)} rows={3} maxLength={500} className="form-control h-auto py-3" />
          </Field>

          <div>
            <div className="mb-3 flex items-center justify-between">
              <span className="kicker">Ulubieni bohaterowie</span>
              <span className="num text-xs text-text-lo">{favorites.length}/5</span>
            </div>
            <div className="grid max-h-72 grid-cols-2 gap-2 overflow-y-auto pr-2 sm:grid-cols-3 lg:grid-cols-5">
              {(champions.data ?? []).map((champion) => {
                const active = favorites.includes(champion.id);
                return (
                  <button type="button" key={champion.id} onClick={() => toggleChampion(champion.id)}
                    className={`flex items-center gap-2 rounded-md border p-2 text-left text-sm transition ${active ? 'border-gold bg-[color:var(--gold)]/10 text-text-hi' : 'border-line text-text-lo hover:border-line-strong'}`}>
                    <img src={champion.iconUrl} alt="" className="h-8 w-8 rounded" />
                    <span className="truncate">{champion.name}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button type="submit" variant="gold" disabled={update.isPending}>
              {update.isPending ? 'Zapisywanie…' : 'Zapisz profil'}
            </Button>
            {update.isSuccess && <span className="text-sm text-win">Profil zapisany.</span>}
          </div>
        </form>
      </section>
    </div>
  );
}

function DrawVotingCard({ lobby, myPlayerId, myVote, pending, onVote }: {
  lobby?: DrawLobby; myPlayerId: string; myVote: 'ACCEPT' | 'REJECT' | null;
  pending: boolean; onVote: (decision: 'ACCEPT' | 'REJECT') => void;
}) {
  if (!lobby) {
    return (
      <section className="glass grid-tex p-8 text-center">
        <div className="mx-auto mb-3 h-3 w-3 animate-pulse rounded-full bg-cyan shadow-glow-cyan" />
        <h2 className="font-display text-2xl">Czekamy na następne losowanie</h2>
        <p className="mt-2 text-sm text-text-lo">Gdy admin rozpocznie grę, składy pojawią się tutaj automatycznie.</p>
      </section>
    );
  }

  const voting = lobby.status === 'TEAMS_DRAWN';
  const mySlot = [...lobby.blue, ...lobby.red].find((player) => player.playerId === myPlayerId);
  const sideLabel = mySlot?.side === 'BLUE' ? 'NIEBIESKĄ' : 'CZERWONĄ';
  const title = voting ? 'Czy gramy tym składem?'
    : lobby.status === 'LOBBY_READY' ? 'Lobby Riot jest gotowe'
    : lobby.status === 'LIVE' ? 'Mecz trwa'
    : lobby.status === 'RESULTS_SUBMITTED' ? 'Wynik czeka na admina'
    : 'Wynik wymaga korekty';
  return (
    <section className="draw-stage glass grid-tex overflow-hidden p-5 sm:p-8">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="kicker text-gold">Losowanie na żywo · runda {lobby.round}</div>
          <h2 className="mt-1 font-display text-3xl">{title}</h2>
        </div>
        {voting && (
          <div className="flex gap-2">
            <Badge tone="win">{lobby.accepts} za</Badge>
            <Badge tone="loss">{lobby.rejects} przeciw</Badge>
            <Badge>{lobby.requiredAccepts} wymaganych</Badge>
          </div>
        )}
      </div>

      <div key={lobby.round} className="grid gap-4 md:grid-cols-2">
        <LobbyTeam title="Niebieska strona" players={lobby.blue} color="var(--blue)" myPlayerId={myPlayerId} />
        <LobbyTeam title="Czerwona strona" players={lobby.red} color="var(--red)" myPlayerId={myPlayerId} />
      </div>

      {voting && (
        <div className="mt-6 rounded-lg border border-line bg-[color:var(--bg)]/60 p-4">
          <div className="mb-3 h-2 overflow-hidden rounded-full bg-bg-2">
            <div className="h-full rounded-full bg-gradient-to-r from-cyan to-gold transition-all duration-500"
              style={{ width: `${Math.min(100, lobby.accepts / lobby.requiredAccepts * 100)}%` }} />
          </div>
          {myVote ? (
            <p className="text-center text-sm text-text">Twój głos: <strong className={myVote === 'ACCEPT' ? 'text-win' : 'text-loss'}>{myVote === 'ACCEPT' ? 'GRAMY' : 'LOSUJ PONOWNIE'}</strong></p>
          ) : (
            <div className="flex justify-center gap-3">
              <Button variant="gold" onClick={() => onVote('ACCEPT')} disabled={pending}>✓ Gramy</Button>
              <Button variant="danger" onClick={() => onVote('REJECT')} disabled={pending}>↻ Losuj ponownie</Button>
            </div>
          )}
          <p className="mt-3 text-center text-xs text-text-lo">6 głosów „za” tworzy lobby Riot. 5 głosów „przeciw” losuje nowe drużyny i strony.</p>
        </div>
      )}
      {lobby.status === 'LOBBY_READY' && (
        <div className="mt-6 rounded-lg border border-[color:var(--win)]/40 bg-[color:var(--win)]/10 p-5 text-center">
          <p className="text-sm text-text">W grze wybierz stronę <strong className="text-win">{sideLabel}</strong>.</p>
          <div className="my-3 select-all break-all font-mono text-xl font-bold text-text-hi">{lobby.tournamentCode}</div>
          <Button variant="gold" onClick={() => navigator.clipboard.writeText(lobby.tournamentCode ?? '')}>
            Kopiuj kod lobby
          </Button>
          <p className="mt-3 text-xs text-text-lo">Wejdź przez Graj → Turniej → wklej kod. Admin uruchomi mecz, gdy wszyscy dołączą.</p>
        </div>
      )}
      {lobby.status === 'LIVE' && (
        <div className="mt-6 rounded-lg border border-[color:var(--cyan)]/40 bg-[color:var(--cyan)]/10 p-4 text-center font-semibold text-cyan">
          Mecz trwa — grasz stroną {sideLabel}. Powodzenia na Rifcie!
        </div>
      )}
      {lobby.status === 'RESULTS_SUBMITTED' && (
        <div className="mt-6 rounded-lg border border-[color:var(--pending)]/40 bg-[color:var(--pending)]/10 p-4 text-center text-text-hi">
          Statystyki zostały pobrane. Oczekiwanie na dodanie meczu przez admina.
        </div>
      )}
      {lobby.status === 'REJECTED' && (
        <div className="mt-6 rounded-lg border border-[color:var(--loss)]/40 bg-[color:var(--loss)]/10 p-4 text-center text-loss">
          Wynik został odesłany do korekty przez admina.
        </div>
      )}
      {lobby.riotImportError && (
        <div className="mt-3 rounded-lg border border-[color:var(--loss)]/30 p-3 text-center text-xs text-loss">
          Automatyczny import nie powiódł się. Admin może pobrać dane ręcznie.
        </div>
      )}
    </section>
  );
}

function LobbyTeam({ title, players, color, myPlayerId }: { title: string; players: LobbyPlayer[]; color: string; myPlayerId: string }) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-[color:var(--bg-1)]/80">
      <div className="px-4 py-3 font-display font-semibold" style={{ color, background: `color-mix(in srgb, ${color} 12%, transparent)` }}>{title}</div>
      <div className="divide-y divide-line">
        {players.map((player, index) => (
          <div key={player.playerId} className="draw-player flex items-center gap-3 px-4 py-3" style={{ animationDelay: `${index * 90}ms` }}>
            <Avatar src={player.avatarUrl} name={player.nickname} size={38} ring={player.playerId === myPlayerId} />
            <span className="flex-1 font-medium text-text-hi">{player.nickname}</span>
            <span className="kicker">{roleLabel(player.role)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="kicker">{label}</span><div className="mt-1">{children}</div></label>;
}