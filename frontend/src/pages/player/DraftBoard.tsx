import { useEffect, useMemo, useRef, useState } from 'react';
import { useChampions } from '../../api/hooks/champions';
import {
  useDraftBan, useDraftHover, useDraftPick, useRequestSwap, useRespondSwap, useServerCountdown,
  type StreamState,
} from '../../api/hooks/drawLobby';
import type { Champion, DrawLobby, LobbyPlayer, Side } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ChampionIcon } from '../../components/champion/ChampionIcon';
import { VolumeControl } from '../../components/ui/VolumeControl';
import { Spinner } from '../../components/ui/States';
import { cn } from '../../lib/cn';
import { roleLabel } from '../../lib/format';
import { sound } from '../../lib/sound';

/** Order a team top→bottom by the draft order (captain first); picks flow down this list. */
const sortByOrder = (players: LobbyPlayer[], order: string[]) =>
  [...players].sort((a, b) => {
    const ia = order.indexOf(a.playerId);
    const ib = order.indexOf(b.playerId);
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib);
  });

/**
 * Re-exported for the panel's other cards (the vote countdown). Kept as a thin alias so callers do
 * not need to know the deadline is server-anchored.
 */
export const useCountdown = (deadline: string | null, serverNow?: string) =>
  useServerCountdown(deadline, serverNow);

/**
 * Tournament draft board.
 *
 * Laid out as a full-viewport stage: both teams, both ban strips, the timer and the champion pool are
 * on screen at once with no page scrolling — only the champion grid scrolls, inside its own box. The
 * on-clock player's pre-selection ("hover") is pushed to the server and rendered for everyone on both
 * teams, and a slot filled in by the expiring timer is labelled as such rather than left blank.
 */
export function DraftBoard({ lobby, myPlayerId, streamState, onCollapse, fullscreen = true }: {
  lobby: DrawLobby;
  myPlayerId: string;
  streamState?: StreamState;
  onCollapse?: () => void;
  fullscreen?: boolean;
}) {
  const draft = lobby.draft;
  const champions = useChampions();
  const ban = useDraftBan(lobby.matchId);
  const pick = useDraftPick(lobby.matchId);
  const hover = useDraftHover(lobby.matchId);
  const requestSwap = useRequestSwap(lobby.matchId);
  const respondSwap = useRespondSwap(lobby.matchId);
  const [search, setSearch] = useState('');
  const [swapMenu, setSwapMenu] = useState<string | null>(null);
  const remaining = useServerCountdown(draft?.deadline ?? null, lobby.updatedAt);

  const champById = useMemo(() => {
    const map = new Map<number, Champion>();
    (champions.data ?? []).forEach((c) => map.set(c.id, c));
    return map;
  }, [champions.data]);

  const all = [...lobby.blue, ...lobby.red];
  const me = all.find((p) => p.playerId === myPlayerId);
  const mySide = me?.side ?? null;
  const isDone = lobby.status === 'DRAFTED' || draft?.status === 'DONE';
  const myTurn = !isDone && draft?.currentPlayerId != null && draft.currentPlayerId === myPlayerId;
  const myTurnBan = Boolean(myTurn && draft?.currentType === 'BAN');
  const myTurnPick = Boolean(myTurn && draft?.currentType === 'PICK');
  const stepSeconds = draft?.stepSeconds && draft.stepSeconds > 0 ? draft.stepSeconds : 30;

  // The server-side hover is the single source of truth, so every client (including the picker's own
  // other tab) shows the same pre-selection.
  const selected = myTurn ? draft?.hoverChampionId ?? null : null;

  useDraftAudio({ draft, isDone, myTurn, remaining });

  useEffect(() => { setSearch(''); }, [draft?.currentPlayerId, draft?.currentType]);

  if (!draft) return null;

  const unavailable = new Set<number>([
    ...draft.blueBans, ...draft.redBans,
    ...all.map((p) => p.championId).filter((id): id is number => id != null),
  ]);

  const filtered = (champions.data ?? [])
    .filter((c) => !unavailable.has(c.id))
    .filter((c) => c.name.toLowerCase().includes(search.trim().toLowerCase()));

  const selectedChamp = selected != null ? champById.get(selected) : undefined;
  const preselect = (championId: number) => {
    if (!myTurn || draft.paused) return;
    hover.mutate(championId === selected ? null : championId);
  };
  const lockIn = () => {
    if (selected == null || draft.paused) return;
    if (myTurnBan) ban.mutate(selected);
    else if (myTurnPick) pick.mutate(selected);
    setSearch('');
  };

  const phaseLabel = isDone ? 'Draft zakończony'
    : draft.currentType === 'BAN' ? 'Faza banów' : 'Faza wyborów';
  const sideName = draft.currentSide === 'BLUE' ? 'Niebiescy' : draft.currentSide === 'RED' ? 'Czerwoni' : '';
  const mySwaps = draft.swaps.filter((s) => s.toPlayerId === myPlayerId || s.fromPlayerId === myPlayerId);

  const teamProps = {
    champById, draft, myPlayerId, isDone, mySide, swapMenu, setSwapMenu,
    onSwap: (target: string, type: 'POSITION' | 'CHAMPION') => {
      requestSwap.mutate({ targetPlayerId: target, type });
      setSwapMenu(null);
    },
  };

  return (
    <section
      className={cn(
        'grid-tex flex flex-col gap-3 overflow-hidden bg-[color:var(--bg)]',
        fullscreen
          ? 'fixed inset-0 z-40 h-[100dvh] p-3 sm:p-4'
          : 'glass draw-stage rounded-xl p-4 sm:p-6',
      )}
    >
      {/* --- Top bar: phase, clock, sound, connection ------------------------------------ */}
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="min-w-0">
          <div className="kicker text-gold">Draft turniejowy</div>
          <h2 className="truncate font-display text-xl leading-tight sm:text-2xl">{phaseLabel}</h2>
          {!isDone && (
            <p className="text-xs text-text-lo sm:text-sm">
              Teraz:{' '}
              <strong style={{ color: draft.currentSide === 'BLUE' ? 'var(--blue)' : 'var(--red)' }}>
                {sideName}
              </strong>{' '}
              — {draft.currentType === 'BAN' ? 'ban (kapitan)' : 'wybór postaci'}
            </p>
          )}
        </div>

        <div className="flex items-center gap-3 sm:gap-4">
          {!isDone && (
            <div className="text-right">
              {draft.paused ? (
                <div className="num font-display text-2xl font-bold text-pending sm:text-3xl">⏸ PAUZA</div>
              ) : (
                <>
                  <div className={cn('num font-display text-3xl font-bold leading-none sm:text-4xl',
                    remaining <= 5 ? 'text-loss' : 'text-text-hi')}>
                    0:{String(remaining).padStart(2, '0')}
                  </div>
                  <div className="mt-1.5 h-1.5 w-24 overflow-hidden rounded-full bg-bg-2 sm:w-28">
                    <div className="h-full rounded-full bg-gradient-to-r from-cyan to-gold transition-all duration-500"
                      style={{ width: `${Math.min(100, (remaining / stepSeconds) * 100)}%` }} />
                  </div>
                </>
              )}
            </div>
          )}
          <VolumeControl />
          <ConnectionPill state={streamState} />
          {onCollapse && (
            <button type="button" onClick={onCollapse}
              title={fullscreen ? 'Zwiń do panelu' : 'Pełny ekran'}
              className="grid h-8 w-8 place-items-center rounded-md border border-line text-text-lo transition hover:text-text-hi">
              {fullscreen ? '⤢' : '⛶'}
            </button>
          )}
        </div>
      </header>

      {draft.paused && !isDone && (
        <div className="shrink-0 rounded-lg border border-[color:var(--pending)]/50 bg-[color:var(--pending)]/10 p-2 text-center text-sm font-semibold text-pending">
          ⏸ Draft wstrzymany przez admina — poczekaj na wznowienie.
        </div>
      )}

      {/* --- Stage: blue | pool | red ---------------------------------------------------- */}
      <div className="grid min-h-0 flex-1 gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)_minmax(0,1fr)]">
        <DraftTeam title="Niebiescy" side="BLUE" color="var(--blue)"
          players={sortByOrder(lobby.blue, draft.blueOrder)} bans={draft.blueBans} {...teamProps} />

        <div className="order-last flex min-h-0 flex-col gap-2 lg:order-none">
          {isDone ? (
            <DoneCard swaps={mySwaps.length > 0} />
          ) : myTurn ? (
            <ChampionPool
              champions={filtered}
              selected={selected}
              disabled={draft.paused}
              search={search}
              onSearch={setSearch}
              onPick={preselect}
              banning={myTurnBan}
            />
          ) : (
            <WaitingCard
              paused={draft.paused}
              onClockName={all.find((p) => p.playerId === draft.currentPlayerId)?.nickname}
              hoverChamp={draft.hoverChampionId != null ? champById.get(draft.hoverChampionId) : undefined}
              banning={draft.currentType === 'BAN'}
            />
          )}

          {myTurn && (
            <div className="shrink-0 rounded-xl border-2 border-gold bg-[color:var(--gold)]/10 p-3">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex min-w-0 items-center gap-2">
                  {selectedChamp ? (
                    <>
                      <ChampionIcon iconUrl={selectedChamp.iconUrl} name={selectedChamp.name} size={36} />
                      <span className="truncate font-medium text-text-hi">{selectedChamp.name}</span>
                    </>
                  ) : (
                    <span className="text-sm text-text-lo">Kliknij postać, aby ją zaznaczyć…</span>
                  )}
                </div>
                <Button variant={myTurnBan ? 'danger' : 'gold'}
                  disabled={selected == null || ban.isPending || pick.isPending || draft.paused}
                  onClick={lockIn}>
                  {myTurnBan ? '🚫 Zbanuj' : '✅ Lock in'}
                </Button>
              </div>
              <p className="mt-1.5 text-[11px] text-text-lo">
                Zaznaczoną postać widzą wszyscy w lobby. Jeśli czas minie, zostanie zablokowana automatycznie.
              </p>
            </div>
          )}

          {mySwaps.length > 0 && (
            <div className="shrink-0 space-y-2 overflow-y-auto">
              {mySwaps.map((s) => {
                const other = all.find((p) =>
                  p.playerId === (s.fromPlayerId === myPlayerId ? s.toPlayerId : s.fromPlayerId));
                const kind = s.type === 'POSITION' ? 'pozycją' : 'postacią';
                const incoming = s.toPlayerId === myPlayerId;
                return (
                  <div key={s.id}
                    className="flex items-center justify-between gap-3 rounded-lg border border-gold/40 bg-[color:var(--bg)]/60 p-2.5">
                    <span className="text-sm text-text">
                      {incoming
                        ? <><strong>{other?.nickname}</strong> chce zamienić się {kind} z Tobą</>
                        : <>Czekasz aż <strong>{other?.nickname}</strong> zaakceptuje zamianę {kind}</>}
                    </span>
                    <div className="flex gap-2">
                      {incoming && (
                        <Button variant="gold" size="sm"
                          onClick={() => respondSwap.mutate({ swapId: s.id, accept: true })}>Akceptuj</Button>
                      )}
                      <Button variant="danger" size="sm"
                        onClick={() => respondSwap.mutate({ swapId: s.id, accept: false })}>
                        {incoming ? 'Odrzuć' : 'Anuluj'}
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <DraftTeam title="Czerwoni" side="RED" color="var(--red)"
          players={sortByOrder(lobby.red, draft.redOrder)} bans={draft.redBans} {...teamProps} />
      </div>
    </section>
  );
}

/** Tells the player whether the board is being pushed live or has fallen back to polling. */
function ConnectionPill({ state }: { state?: StreamState }) {
  if (!state || state === 'live') return null;
  return (
    <span className="flex items-center gap-1.5 rounded-md border border-line px-2 py-1 text-[11px] text-text-lo"
      title="Strumień na żywo jest niedostępny — plansza odświeża się co ~2,5 s.">
      <Spinner className="h-3 w-3 border" />
      {state === 'connecting' ? 'łączenie…' : 'tryb odświeżania'}
    </span>
  );
}

function ChampionPool({ champions, selected, disabled, search, onSearch, onPick, banning }: {
  champions: Champion[]; selected: number | null; disabled: boolean;
  search: string; onSearch: (v: string) => void; onPick: (id: number) => void; banning: boolean;
}) {
  return (
    <div className="flex min-h-0 flex-1 flex-col rounded-xl border-2 border-gold bg-[color:var(--gold)]/5 p-3">
      <div className="mb-2 flex shrink-0 flex-wrap items-center justify-between gap-2">
        <div className={cn('font-display text-lg font-bold sm:text-xl',
          banning ? 'text-loss' : 'text-gold', !disabled && 'animate-pulse')}>
          {banning ? '🚫 TWOJA KOLEJ — BANUJ' : '⚡ TWOJA KOLEJ — WYBIERZ POSTAĆ'}
        </div>
        <input value={search} onChange={(e) => onSearch(e.target.value)} placeholder="Szukaj postaci…"
          className="w-full rounded-md border border-line bg-[color:var(--bg-1)] px-3 py-1.5 text-sm sm:w-44" />
      </div>
      <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-6 gap-1.5 overflow-y-auto pr-1 sm:grid-cols-8 lg:grid-cols-7 xl:grid-cols-9">
        {champions.map((c) => (
          <button key={c.id} type="button" title={c.name} disabled={disabled}
            onClick={() => onPick(c.id)}
            className={cn('rounded-md ring-2 transition disabled:opacity-40',
              selected === c.id ? 'ring-gold' : 'ring-transparent hover:ring-gold/50')}>
            <ChampionIcon iconUrl={c.iconUrl} name={c.name} size={44} />
          </button>
        ))}
        {champions.length === 0 && (
          <p className="col-span-full py-6 text-center text-sm text-text-lo">Brak postaci dla tego filtra.</p>
        )}
      </div>
    </div>
  );
}

/** What the other nine players see while somebody else is on the clock. */
function WaitingCard({ paused, onClockName, hoverChamp, banning }: {
  paused: boolean; onClockName?: string; hoverChamp?: Champion; banning: boolean;
}) {
  return (
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 rounded-xl border border-line bg-[color:var(--bg-1)]/60 p-5 text-center">
      {paused ? (
        <p className="font-display text-lg text-pending">⏸ Draft wstrzymany przez admina.</p>
      ) : (
        <>
          <p className="text-sm text-text-lo">
            {onClockName ? <>Tura: <strong className="text-text-hi">{onClockName}</strong></> : 'Czekaj na swoją kolej…'}
          </p>
          {hoverChamp ? (
            <div className="flex flex-col items-center gap-2">
              <ChampionIcon iconUrl={hoverChamp.iconUrl} name={hoverChamp.name} size={92} />
              <div>
                <div className="font-display text-xl text-text-hi">{hoverChamp.name}</div>
                <div className={cn('kicker', banning ? 'text-loss' : 'text-gold')}>
                  {banning ? 'zaraz zbanuje' : 'zaraz wybierze'}
                </div>
              </div>
            </div>
          ) : (
            <div className="grid h-24 w-24 place-items-center rounded-lg border border-dashed border-line text-3xl text-text-lo">
              ?
            </div>
          )}
        </>
      )}
    </div>
  );
}

function DoneCard({ swaps }: { swaps: boolean }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 rounded-xl border border-[color:var(--win)]/40 bg-[color:var(--win)]/10 p-5 text-center">
      <div className="text-3xl">🏁</div>
      <p className="font-display text-lg text-win">Draft zakończony — stwórzcie lobby w grze!</p>
      <p className="max-w-sm text-sm text-text-lo">
        Możesz jeszcze zamienić się pozycją lub postacią z kolegą z drużyny (klik ⇄ przy graczu).
        Admin uruchomi mecz, gdy będziecie gotowi.
      </p>
      {swaps && <p className="text-xs text-gold">Masz oczekującą propozycję zamiany poniżej.</p>}
    </div>
  );
}

function DraftTeam({ title, side, color, players, bans, champById, draft, myPlayerId, isDone, mySide, swapMenu, setSwapMenu, onSwap }: {
  title: string; side: Side; color: string; players: LobbyPlayer[]; bans: number[];
  champById: Map<number, Champion>; draft: NonNullable<DrawLobby['draft']>; myPlayerId: string;
  isDone: boolean; mySide: Side | null;
  swapMenu: string | null; setSwapMenu: (id: string | null) => void;
  onSwap: (targetPlayerId: string, type: 'POSITION' | 'CHAMPION') => void;
}) {
  const active = draft.currentSide === side && draft.status !== 'DONE';
  return (
    <div className={cn('flex min-h-0 flex-col overflow-hidden rounded-xl border bg-[color:var(--bg-1)]/80',
      active ? 'border-gold' : 'border-line')}>
      <div className="flex shrink-0 items-center justify-between px-3 py-2 font-display text-sm font-semibold"
        style={{ color, background: `color-mix(in srgb, ${color} 12%, transparent)` }}>
        <span>{title}</span>
        {active && <Badge tone="win">tura</Badge>}
      </div>

      {/* Ban strip */}
      <div className="flex shrink-0 items-center gap-1.5 border-b border-line px-3 py-1.5">
        <span className="kicker text-text-lo">Bany</span>
        {Array.from({ length: 5 }).map((_, i) => {
          const champ = bans[i] != null ? champById.get(bans[i]) : undefined;
          return bans[i] != null
            ? <ChampionIcon key={i} iconUrl={champ?.iconUrl} name={champ?.name} size={26}
                className="opacity-60 grayscale" />
            : <span key={i} className="h-6 w-6 rounded-md border border-dashed border-line" />;
        })}
      </div>

      <div className="flex min-h-0 flex-1 flex-col divide-y divide-line overflow-y-auto">
        {players.map((player) => {
          const onClock = !isDone && draft.currentPlayerId === player.playerId;
          // While a player is on the clock for a PICK, show their live pre-selection in their own slot
          // so both teams watch the pick take shape.
          const previewId = onClock && draft.currentType === 'PICK' ? draft.hoverChampionId : null;
          const champ = player.championId != null
            ? champById.get(player.championId)
            : previewId != null ? champById.get(previewId) : undefined;
          const isPreview = player.championId == null && previewId != null;
          const canSwap = isDone && mySide === side && player.playerId !== myPlayerId;
          return (
            <div key={player.playerId}
              className={cn('relative flex flex-1 items-center gap-2.5 px-3 py-2',
                onClock && 'bg-[color:var(--gold)]/10 ring-1 ring-inset ring-gold')}>
              <div className={cn('shrink-0', isPreview && 'opacity-60 ring-2 ring-gold rounded-md')}>
                <ChampionIcon iconUrl={champ?.iconUrl} name={champ?.name} size={38} />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5 text-sm font-medium text-text-hi">
                  <span className="truncate">{player.nickname}</span>
                  {player.captain && <span title="Kapitan (robi bany)" className="text-gold">★</span>}
                  {player.playerId === myPlayerId && <span className="kicker text-cyan">(Ty)</span>}
                </div>
                <div className="truncate text-xs text-text-lo">
                  {roleLabel(player.role)}
                  {champ ? ` · ${champ.name}` : ''}
                  {isPreview && <span className="text-gold"> · zaznaczono</span>}
                </div>
              </div>
              {canSwap && (
                <button type="button"
                  onClick={() => setSwapMenu(swapMenu === player.playerId ? null : player.playerId)}
                  title="Zamień się"
                  className="rounded-md border border-line px-1.5 py-0.5 text-text-lo hover:text-text-hi">⇄</button>
              )}
              {canSwap && swapMenu === player.playerId && (
                <div className="absolute right-3 top-11 z-10 w-44 rounded-lg border border-line bg-[color:var(--bg-1)] p-1 shadow-lg">
                  <button type="button" onClick={() => onSwap(player.playerId, 'POSITION')}
                    className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-[var(--glass-strong)]">Zamień pozycję</button>
                  <button type="button" onClick={() => onSwap(player.playerId, 'CHAMPION')}
                    className="block w-full rounded px-3 py-2 text-left text-sm hover:bg-[var(--glass-strong)]">Zamień postać</button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Audio cues driven purely off state transitions, so they fire once each regardless of how many
 * times the board re-renders (SSE push and the polling backstop both land here).
 */
function useDraftAudio({ draft, isDone, myTurn, remaining }: {
  draft: DrawLobby['draft']; isDone: boolean; myTurn: boolean; remaining: number;
}) {
  const prevIndex = useRef<number | null>(null);
  const prevType = useRef<string | null>(null);
  const wasMyTurn = useRef(false);
  const wasDone = useRef(false);
  const lastTick = useRef<number | null>(null);

  useEffect(() => {
    sound.armOnFirstGesture();
  }, []);

  // Music runs for the duration of the draft only.
  useEffect(() => {
    if (draft && !isDone) void sound.startMusic();
    return () => sound.stopMusic();
  }, [draft, isDone]);

  useEffect(() => {
    if (!draft) return;
    if (prevIndex.current === null) {
      prevIndex.current = draft.currentIndex;
      prevType.current = draft.currentType;
      if (!isDone) sound.play('draftStart');
      return;
    }
    if (draft.currentIndex !== prevIndex.current) {
      // The step that just finished tells us which cue to play.
      sound.play(prevType.current === 'BAN' ? 'ban' : 'lockIn');
      prevIndex.current = draft.currentIndex;
      prevType.current = draft.currentType;
    }
  }, [draft, isDone]);

  useEffect(() => {
    if (myTurn && !wasMyTurn.current) sound.play('yourTurn');
    wasMyTurn.current = myTurn;
  }, [myTurn]);

  useEffect(() => {
    if (isDone && !wasDone.current) sound.play('draftDone');
    wasDone.current = isDone;
  }, [isDone]);

  useEffect(() => {
    if (isDone || draft?.paused) return;
    if (remaining > 0 && remaining <= 5 && lastTick.current !== remaining) {
      lastTick.current = remaining;
      sound.play('tick');
    }
    if (remaining > 5) lastTick.current = null;
  }, [remaining, isDone, draft?.paused]);
}
