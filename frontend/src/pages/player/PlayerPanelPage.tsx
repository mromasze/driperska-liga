import { useEffect, useRef, useState } from 'react';
import { useChampions } from '../../api/hooks/champions';
import { useDrawLobby, useVoteOnDraw } from '../../api/hooks/drawLobby';
import { DraftBoard, useCountdown } from './DraftBoard';
import { GameLineup } from '../../components/match/GameLineup';
import { usePlannedMatches, useRsvpPlannedMatch } from '../../api/hooks/planned';
import { useRateableMatches, useSubmitFeedback } from '../../api/hooks/feedback';
import { useChangePassword } from '../../api/hooks/auth';
import { useMyPlayer, useUpdateMyPlayer, useUploadMyAvatar } from '../../api/hooks/players';
import type { DrawLobby, LobbyPlayer, PlannedMatch, Role } from '../../api/types';
import type { StreamState } from '../../api/hooks/drawLobby';
import { Avatar } from '../../components/ui/Avatar';
import { AdSlot } from '../../components/ads/AdSlot';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { CardSkeleton, ErrorState, SectionSkeleton } from '../../components/ui/States';
import { roleLabel, formatDateTime } from '../../lib/format';
import type { RsvpResponse, RateableMatch } from '../../api/types';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

type PanelTab = 'dashboard' | 'draft' | 'ratings' | 'profile';

/** Lobby phases that belong on the dedicated draft tab rather than the dashboard. */
const DRAFT_STATUSES = ['DRAFT_READY', 'DRAFTING', 'DRAFTED'];

export function PlayerPanelPage() {
  const player = useMyPlayer();
  const draw = useDrawLobby();
  const vote = useVoteOnDraw();
  const update = useUpdateMyPlayer();
  const upload = useUploadMyAvatar();
  const champions = useChampions();
  const rateable = useRateableMatches();
  const ratingsCount = (rateable.data ?? []).filter((m) => !m.myFeedback).length;
  const fileInput = useRef<HTMLInputElement>(null);

  const [tab, setTab] = useState<PanelTab>('dashboard');
  const [draftFullscreen, setDraftFullscreen] = useState(true);
  const [mainRole, setMainRole] = useState<Role>('MID');
  const [secondaryRole, setSecondaryRole] = useState<Role | ''>('');
  const [riotId, setRiotId] = useState('');
  const [opggLink, setOpggLink] = useState('');
  const [bio, setBio] = useState('');
  const [favorites, setFavorites] = useState<number[]>([]);
  const [champQuery, setChampQuery] = useState('');

  useEffect(() => {
    if (!player.data) return;
    setMainRole(player.data.mainRole);
    setSecondaryRole(player.data.secondaryRole ?? '');
    setRiotId(player.data.riotId ?? '');
    setOpggLink(player.data.opggLink ?? '');
    setBio(player.data.bio ?? '');
    setFavorites(player.data.favoriteChampionIds ?? []);
  }, [player.data]);

  const lobby = draw.data;
  const draftActive = DRAFT_STATUSES.includes(lobby?.status ?? '');

  // Follow a draft that *starts* while the player is in the panel, so nobody on another tab misses
  // their turn — but never on the first load. Opening the panel during a running match used to drop
  // the player straight into a full-screen board; now the pulsing "Draft" tab invites them in and
  // they choose when to enter.
  const sawDraftActive = useRef<boolean | null>(null);
  useEffect(() => {
    if (draw.isLoading) return;
    const firstReading = sawDraftActive.current === null;
    sawDraftActive.current = draftActive;
    if (firstReading) return;
    if (draftActive) setTab('draft');
    else setTab((current) => (current === 'draft' ? 'dashboard' : current));
  }, [draftActive, draw.isLoading]);

  if (player.isLoading) return <PanelSkeleton />;
  if (player.isError || !player.data) return <ErrorState error={player.error} />;

  const p = player.data;
  const myVote = lobby?.acceptedPlayerIds.includes(p.id)
    ? 'ACCEPT'
    : lobby?.rejectedPlayerIds.includes(p.id) ? 'REJECT' : null;
  const tabs: { id: PanelTab; label: string }[] = [
    { id: 'dashboard', label: 'Dashboard' },
    ...(draftActive ? [{ id: 'draft' as PanelTab, label: 'Draft' }] : []),
    { id: 'ratings', label: 'Ocena' },
    { id: 'profile', label: 'Profil i ustawienia' },
  ];

  // The draft board owns the whole viewport, so the panel chrome steps aside while it is open.
  if (tab === 'draft' && lobby && draftFullscreen) {
    return (
      <DraftOrWaiting
        lobby={lobby}
        myPlayerId={p.id}
        streamState={draw.streamState}
        fullscreen
        onCollapse={() => setDraftFullscreen(false)}
      />
    );
  }

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
    <div className="space-y-8">
      <header>
        <div className="kicker text-gold">Zalogowano jako {p.nickname}</div>
        <h1 className="mt-1 font-display text-4xl">Centrum gracza</h1>
      </header>

      <nav className="flex flex-wrap gap-2 border-b border-line">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            className={`-mb-px border-b-2 px-4 py-2.5 font-display text-sm font-semibold transition ${
              tab === t.id
                ? 'border-gold text-text-hi'
                : t.id === 'draft'
                  // The board no longer opens by itself, so the way in has to stand out.
                  ? 'border-gold/40 text-gold hover:text-gold-soft'
                  : 'border-transparent text-text-lo hover:text-text'
            }`}
          >
            <span className="inline-flex items-center gap-1.5">
              {t.label}
              {t.id === 'ratings' && ratingsCount > 0 && (
                <span className="num grid h-5 min-w-5 place-items-center rounded-full bg-gold px-1.5 text-xs font-bold text-[#1a1205]">
                  {ratingsCount}
                </span>
              )}
              {t.id === 'draft' && (
                <span className="on-clock-tag inline-flex items-center gap-1 rounded-full bg-gold px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[#1a1205]">
                  na żywo
                </span>
              )}
            </span>
          </button>
        ))}
      </nav>

      {tab === 'draft' && lobby && (
        <DraftOrWaiting
          lobby={lobby}
          myPlayerId={p.id}
          streamState={draw.streamState}
          fullscreen={false}
          onCollapse={() => setDraftFullscreen(true)}
        />
      )}

      {tab === 'dashboard' && (
        <div className="space-y-10">
          {draw.isLoading ? (
            <CardSkeleton lines={4} />
          ) : (
            <DrawVotingCard
              lobby={lobby ?? undefined}
              myPlayerId={p.id}
              myVote={myVote}
              pending={vote.isPending}
              onVote={(decision) => lobby && vote.mutate({ matchId: lobby.matchId, decision })}
              onOpenDraft={() => { setDraftFullscreen(true); setTab('draft'); }}
            />
          )}

          <UpcomingMatches />

          {/* Dashboard only. The draft tab is live and time-critical — nothing competes with it,
              and the ratings/profile tabs are short forms where an ad would dominate the page. */}
          <AdSlot name="panelDashboard" />
        </div>
      )}

      {tab === 'ratings' && (
        <div className="space-y-10">
          {rateable.isLoading && <CardSkeleton lines={5} />}
          <MatchRatingSurvey myPlayerId={p.id} />
          {!rateable.isLoading && (rateable.data?.length ?? 0) === 0 && (
            <section className="glass grid-tex p-8 text-center">
              <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-full bg-[color:var(--bg-2)] text-2xl">⭐</div>
              <h2 className="font-display text-2xl">Brak meczów do oceny</h2>
              <p className="mt-2 text-sm text-text-lo">Gdy admin zakończy Twój mecz, pojawi się tutaj ankieta oceny.</p>
            </section>
          )}
        </div>
      )}

      {tab === 'profile' && (
        <div className="space-y-10">
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
            <input
              value={champQuery}
              onChange={(e) => setChampQuery(e.target.value)}
              placeholder="Szukaj bohatera po nazwie…"
              className="form-control mb-3"
            />
            <div className="grid max-h-72 grid-cols-2 gap-2 overflow-y-auto pr-2 sm:grid-cols-3 lg:grid-cols-5">
              {(champions.data ?? [])
                .filter((champion) => champion.name.toLowerCase().includes(champQuery.trim().toLowerCase()))
                .map((champion) => {
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

          <ChangePasswordCard />
        </div>
      )}
    </div>
  );
}

/**
 * The draft tab. Once the squad is locked the board itself takes over; before the draft is actually
 * running (DRAFT_READY) there is nothing to draft yet, so the lineup and a short explanation stand in.
 */
function DraftOrWaiting({ lobby, myPlayerId, streamState, fullscreen, onCollapse }: {
  lobby: DrawLobby; myPlayerId: string; streamState?: StreamState;
  fullscreen: boolean; onCollapse: () => void;
}) {
  if (lobby.draft) {
    return (
      <DraftBoard lobby={lobby} myPlayerId={myPlayerId} streamState={streamState}
        fullscreen={fullscreen} onCollapse={onCollapse} />
    );
  }
  return (
    <section className="draw-stage glass grid-tex overflow-hidden p-5 sm:p-8">
      <div className="mb-5">
        <div className="kicker text-gold">Skład zatwierdzony</div>
        <h2 className="mt-1 font-display text-3xl">Czekamy aż admin rozpocznie draft</h2>
        <p className="mt-2 text-sm text-text-lo">
          Wskoczcie na Discorda / osobne lobby i ustalcie, kto gdzie gra. Draft ruszy, gdy admin da sygnał —
          ta zakładka przełączy się automatycznie.
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2">
        <LobbyTeam title="Niebieska strona" players={lobby.blue} color="var(--blue)" myPlayerId={myPlayerId} />
        <LobbyTeam title="Czerwona strona" players={lobby.red} color="var(--red)" myPlayerId={myPlayerId} />
      </div>
    </section>
  );
}

function DrawVotingCard({ lobby, myPlayerId, myVote, pending, onVote, onOpenDraft }: {
  lobby?: DrawLobby; myPlayerId: string; myVote: 'ACCEPT' | 'REJECT' | null;
  pending: boolean; onVote: (decision: 'ACCEPT' | 'REJECT') => void; onOpenDraft: () => void;
}) {
  const secondsLeft = useCountdown(lobby?.voteDeadline ?? null, lobby?.updatedAt);
  if (!lobby) {
    return (
      <section className="glass grid-tex p-8 text-center">
        <div className="mx-auto mb-3 h-3 w-3 animate-pulse rounded-full bg-cyan shadow-glow-cyan" />
        <h2 className="font-display text-2xl">Czekamy na następne losowanie</h2>
        <p className="mt-2 text-sm text-text-lo">Gdy admin rozpocznie grę, składy pojawią się tutaj automatycznie.</p>
      </section>
    );
  }

  // The draft has its own tab; the dashboard only announces it and links across.
  if (DRAFT_STATUSES.includes(lobby.status)) {
    const drafting = lobby.status === 'DRAFTING';
    return (
      <section className="glass grid-tex p-6 text-center sm:p-8">
        <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-full bg-[color:var(--gold)]/15 text-2xl">
          {drafting ? '⚔' : '🛡'}
        </div>
        <h2 className="font-display text-2xl">
          {drafting ? 'Trwa draft — bany i wybór postaci' : lobby.status === 'DRAFTED'
            ? 'Draft zakończony — tworzymy lobby' : 'Skład zatwierdzony — draft zaraz ruszy'}
        </h2>
        <p className="mx-auto mt-2 max-w-md text-sm text-text-lo">
          Cała plansza draftu jest w zakładce <strong className="text-text-hi">Draft</strong>.
        </p>
        <Button variant="gold" className="mt-4" onClick={onOpenDraft}>Otwórz draft →</Button>
      </section>
    );
  }

  if (lobby.status === 'LIVE') {
    const mine = [...lobby.blue, ...lobby.red].find((pl) => pl.playerId === myPlayerId);
    const sl = mine?.side === 'BLUE' ? 'NIEBIESKĄ' : 'CZERWONĄ';
    return (
      <section className="draw-stage glass grid-tex overflow-hidden p-5 sm:p-8">
        <div className="mb-5">
          <div className="kicker text-gold">Mecz trwa</div>
          <h2 className="mt-1 font-display text-3xl">Kto kim gra</h2>
          {mine && <p className="mt-2 text-sm font-semibold text-cyan">Grasz stroną {sl}. Powodzenia na Rifcie!</p>}
        </div>
        <GameLineup lobby={lobby} myPlayerId={myPlayerId} />
      </section>
    );
  }

  if (lobby.status === 'RESULTS_SUBMITTED') {
    return (
      <section className="glass grid-tex p-8 text-center">
        <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-full bg-[color:var(--pending)]/15 text-2xl">🏁</div>
        <h2 className="font-display text-2xl">Mecz zakończony</h2>
        <p className="mt-2 text-sm text-text-lo">
          Wyniki zostały wysłane. Oczekiwanie na zakończenie meczu przez admina — potem pojawi się ankieta oceny.
        </p>
      </section>
    );
  }

  const voting = lobby.status === 'TEAMS_DRAWN';
  const mySlot = [...lobby.blue, ...lobby.red].find((player) => player.playerId === myPlayerId);
  const sideLabel = mySlot?.side === 'BLUE' ? 'NIEBIESKĄ' : 'CZERWONĄ';
  const title = voting ? 'Czy gramy tym składem?'
    : lobby.status === 'LOBBY_READY' ? 'Lobby Riot jest gotowe'
    : 'Wynik wymaga korekty';
  return (
    <section className="draw-stage glass grid-tex overflow-hidden p-5 sm:p-8">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <div className="kicker text-gold">Losowanie na żywo · runda {lobby.round}</div>
          <h2 className="mt-1 font-display text-3xl">{title}</h2>
        </div>
        {voting && (
          <div className="flex items-center gap-2">
            {lobby.voteDeadline && (
              <Badge tone={secondsLeft <= 10 ? 'loss' : 'pending'}>⏱ 0:{String(secondsLeft).padStart(2, '0')}</Badge>
            )}
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
          <p className="mt-3 text-center text-xs text-text-lo">
            6 głosów „za” zatwierdza skład i startuje draft. 5 „przeciw” losuje nowe drużyny.
            {lobby.voteDeadline && ' Brak decyzji do końca odliczania = automatyczne zatwierdzenie.'}
          </p>
          <VoteTally lobby={lobby} />
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
          {lobby.tournamentCode?.startsWith('STUB') && (
            <p className="mt-3 text-xs text-loss">
              ⚠ To jest kod TESTOWY (Riot stub) — nie zadziała w kliencie gry. Zagrajcie zwykłe lobby custom,
              a wynik admin wpisze ręcznie. Prawdziwe kody wymagają produkcyjnego dostępu Tournament API.
            </p>
          )}
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

function VoteTally({ lobby }: { lobby: DrawLobby }) {
  const players = [...lobby.blue, ...lobby.red];
  const accepted = new Set(lobby.acceptedPlayerIds);
  const rejected = new Set(lobby.rejectedPlayerIds);
  return (
    <div className="mt-4 border-t border-line pt-4">
      <div className="mb-2 kicker">Kto jak zagłosował</div>
      <div className="grid gap-2 sm:grid-cols-2">
        {players.map((player) => {
          const vote = accepted.has(player.playerId) ? 'ACCEPT'
            : rejected.has(player.playerId) ? 'REJECT' : null;
          return (
            <div key={player.playerId} className="flex items-center gap-2 rounded-md bg-[color:var(--bg-1)]/60 px-3 py-1.5">
              <Avatar src={player.avatarUrl} name={player.nickname} size={24} />
              <span className="min-w-0 flex-1 truncate text-sm text-text-hi">{player.nickname}</span>
              {vote === 'ACCEPT' ? (
                <span className="text-xs font-semibold text-win">✓ Gramy</span>
              ) : vote === 'REJECT' ? (
                <span className="text-xs font-semibold text-loss">↻ Losuj ponownie</span>
              ) : (
                <span className="text-xs text-text-lo">czeka…</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="kicker">{label}</span><div className="mt-1">{children}</div></label>;
}
const RSVP_OPTIONS: { value: RsvpResponse; label: string; variant: 'gold' | 'ghost' | 'danger' }[] = [
  { value: 'YES', label: '✓ Będę', variant: 'gold' },
  { value: 'MAYBE', label: '? Może', variant: 'ghost' },
  { value: 'NO', label: '✗ Nie', variant: 'danger' },
];

function UpcomingMatches() {
  const planned = usePlannedMatches();
  const rsvp = useRsvpPlannedMatch();
  const list = planned.data ?? [];
  if (planned.isLoading) return <SectionSkeleton title="Nadchodzące mecze" rows={2} />;
  if (list.length === 0) return null;

  return (
    <section className="glass grid-tex p-5 sm:p-7">
      <div className="mb-4">
        <div className="kicker text-gold">Nadchodzące mecze</div>
        <h2 className="mt-1 font-display text-2xl">Potwierdź obecność</h2>
      </div>
      <div className="space-y-3">
        {list.map((m) => (
          <div key={m.id} className="rounded-lg border border-line bg-[color:var(--bg-1)]/70 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div className="font-semibold text-text-hi">{formatDateTime(m.scheduledAt)}</div>
                {m.note && <div className="text-sm text-text-lo">{m.note}</div>}
                <div className="mt-1 text-xs text-text-lo">Będzie: {m.yes} · Może: {m.maybe} · Nie: {m.no}</div>
              </div>
              <div className="flex gap-2">
                {RSVP_OPTIONS.map((o) => (
                  <Button key={o.value}
                    variant={m.myResponse === o.value ? o.variant : 'ghost'}
                    size="sm"
                    disabled={rsvp.isPending}
                    onClick={() => rsvp.mutate({ id: m.id, response: o.value })}>
                    {o.label}
                  </Button>
                ))}
              </div>
            </div>
            <AttendanceList entries={m.responses} />
          </div>
        ))}
      </div>
    </section>
  );
}

/**
 * Who has actually declared what, next to the vote itself — a tally alone does not tell you whether
 * the two people you need are in.
 */
function AttendanceList({ entries }: { entries: PlannedMatch['responses'] }) {
  const groups: { value: RsvpResponse; label: string; tone: string }[] = [
    { value: 'YES', label: 'Będą', tone: 'text-win' },
    { value: 'MAYBE', label: 'Może', tone: 'text-pending' },
    { value: 'NO', label: 'Nie będą', tone: 'text-loss' },
  ];
  if (entries.length === 0) {
    return (
      <p className="mt-3 border-t border-line pt-3 text-xs text-text-lo">
        Nikt jeszcze nie zagłosował.
      </p>
    );
  }
  return (
    <div className="mt-3 grid gap-3 border-t border-line pt-3 sm:grid-cols-3">
      {groups.map((g) => {
        const names = entries.filter((e) => e.response === g.value);
        return (
          <div key={g.value}>
            <div className={`kicker ${g.tone}`}>{g.label} · {names.length}</div>
            {names.length === 0 ? (
              <p className="mt-1 text-xs text-text-lo">—</p>
            ) : (
              <ul className="mt-1 space-y-0.5">
                {names.map((e) => (
                  <li key={e.playerId} className="truncate text-sm text-text-hi">{e.nickname}</li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
    </div>
  );
}

function PanelSkeleton() {
  return (
    <div className="space-y-8">
      <div className="space-y-2">
        <div className="skeleton h-3 w-40" />
        <div className="skeleton h-9 w-64" />
      </div>
      <div className="skeleton h-10 w-full max-w-md" />
      <CardSkeleton lines={5} />
      <SectionSkeleton title="Nadchodzące mecze" rows={2} />
    </div>
  );
}

function MatchRatingSurvey({ myPlayerId }: { myPlayerId: string }) {
  const rateable = useRateableMatches();
  const list = rateable.data ?? [];
  if (list.length === 0) return null;
  return (
    <section className="glass grid-tex p-5 sm:p-7">
      <div className="mb-4">
        <div className="kicker text-gold">Po meczu</div>
        <h2 className="mt-1 font-display text-2xl">Oceń mecz (opcjonalnie)</h2>
        <p className="mt-1 text-sm text-text-lo">
          Możesz wyróżnić jedną osobę na plus i jedną na minus oraz zostawić krótką notatkę. To dobrowolne.
        </p>
      </div>
      <div className="space-y-5">
        {list.map((m) => <RatingRow key={m.matchId} match={m} myPlayerId={myPlayerId} />)}
      </div>
    </section>
  );
}

function RatingRow({ match, myPlayerId }: { match: RateableMatch; myPlayerId: string }) {
  const submit = useSubmitFeedback();
  const others = match.participants.filter((p) => p.playerId !== myPlayerId);
  const [up, setUp] = useState<string>(match.myFeedback?.upvotePlayerId ?? '');
  const [down, setDown] = useState<string>(match.myFeedback?.downvotePlayerId ?? '');
  const [note, setNote] = useState<string>(match.myFeedback?.note ?? '');
  const [saved, setSaved] = useState(false);

  const save = () => {
    setSaved(false);
    submit.mutate(
      { matchId: match.matchId, upvotePlayerId: up || null, downvotePlayerId: down || null, note: note.trim() || null },
      { onSuccess: () => setSaved(true) },
    );
  };

  return (
    <div className="rounded-lg border border-line bg-[color:var(--bg-1)]/70 p-4">
      <div className="mb-3">
        <div className="text-sm font-medium text-text-hi">
          Mecz {(match.startedAt ?? match.completedAt)
            ? `z ${formatDateTime(match.startedAt ?? match.completedAt)}` : ''}
        </div>
        <div className="mt-0.5 text-xs text-text-lo">
          <span style={{ color: 'var(--blue)' }}>Niebiescy:</span>{' '}
          {match.participants.filter((p) => p.side === 'BLUE').map((p) => p.nickname).join(', ')}
        </div>
        <div className="text-xs text-text-lo">
          <span style={{ color: 'var(--red)' }}>Czerwoni:</span>{' '}
          {match.participants.filter((p) => p.side === 'RED').map((p) => p.nickname).join(', ')}
        </div>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label><span className="kicker text-win">👍 Wyróżnij (plus)</span>
          <select value={up} onChange={(e) => setUp(e.target.value)} className="form-control mt-1">
            <option value="">— nikt —</option>
            {others.filter((p) => p.playerId !== down).map((p) => (
              <option key={p.playerId} value={p.playerId}>{p.nickname} ({roleLabel(p.role)})</option>
            ))}
          </select>
        </label>
        <label><span className="kicker text-loss">👎 Oceń słabo (minus)</span>
          <select value={down} onChange={(e) => setDown(e.target.value)} className="form-control mt-1">
            <option value="">— nikt —</option>
            {others.filter((p) => p.playerId !== up).map((p) => (
              <option key={p.playerId} value={p.playerId}>{p.nickname} ({roleLabel(p.role)})</option>
            ))}
          </select>
        </label>
      </div>
      <label className="mt-3 block"><span className="kicker">Notatka (kto zagrał źle i dlaczego — opcjonalnie)</span>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} maxLength={500}
          className="form-control mt-1 h-auto py-2" />
      </label>
      <div className="mt-3 flex items-center gap-3">
        <Button variant="gold" size="sm" disabled={submit.isPending} onClick={save}>
          {submit.isPending ? 'Zapisywanie…' : match.myFeedback ? 'Zaktualizuj ocenę' : 'Wyślij ocenę'}
        </Button>
        {saved && <span className="text-sm text-win">Zapisano.</span>}
        {submit.isError && <span className="text-sm text-loss">{(submit.error as Error).message}</span>}
      </div>
    </div>
  );
}

function ChangePasswordCard() {
  const change = useChangePassword();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [msg, setMsg] = useState<string | null>(null);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setMsg(null);
    if (next.length < 8) { setMsg('⚠ Nowe hasło musi mieć min. 8 znaków'); return; }
    if (next !== confirm) { setMsg('⚠ Hasła nie są takie same'); return; }
    change.mutate({ currentPassword: current, newPassword: next }, {
      onSuccess: () => { setMsg('✓ Hasło zmienione'); setCurrent(''); setNext(''); setConfirm(''); },
      onError: (err) => setMsg('⚠ ' + (err as Error).message),
    });
  };

  return (
    <section className="panel p-5 sm:p-7">
      <h2 className="font-display text-2xl">Ustawienia konta</h2>
      <p className="text-sm text-text-lo">Zmień hasło do logowania.</p>
      <form onSubmit={submit} className="mt-4 grid max-w-md gap-3">
        <label><span className="kicker">Aktualne hasło</span>
          <input type="password" autoComplete="current-password" value={current}
            onChange={(e) => setCurrent(e.target.value)} className="form-control mt-1" required />
        </label>
        <label><span className="kicker">Nowe hasło (min. 8 znaków)</span>
          <input type="password" autoComplete="new-password" value={next}
            onChange={(e) => setNext(e.target.value)} className="form-control mt-1" required />
        </label>
        <label><span className="kicker">Powtórz nowe hasło</span>
          <input type="password" autoComplete="new-password" value={confirm}
            onChange={(e) => setConfirm(e.target.value)} className="form-control mt-1" required />
        </label>
        <div className="flex items-center gap-3">
          <Button type="submit" variant="gold" disabled={change.isPending}>
            {change.isPending ? 'Zmiana…' : 'Zmień hasło'}
          </Button>
          {msg && <span className={`text-sm ${msg.startsWith('✓') ? 'text-win' : 'text-loss'}`}>{msg}</span>}
        </div>
      </form>
    </section>
  );
}
