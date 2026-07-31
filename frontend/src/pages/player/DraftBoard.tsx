import { useEffect, useMemo, useRef, useState } from 'react';
import { useChampions } from '../../api/hooks/champions';
import {
  useDraftBan, useDraftHover, useDraftPick, useRequestSwap, useRespondSwap, useServerCountdown,
  type StreamState,
} from '../../api/hooks/drawLobby';
import type { Champion, DrawLobby, LobbyPlayer, Side, SwapType } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ChampionIcon } from '../../components/champion/ChampionIcon';
import { DraftChat } from '../../components/match/DraftChat';
import { VolumeControl } from '../../components/ui/VolumeControl';
import { Spinner } from '../../components/ui/States';
import { cn } from '../../lib/cn';
import { roleLabel } from '../../lib/format';
import { sound } from '../../lib/sound';
import { useSoundSettings } from '../../lib/useSound';

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
 * Everything the board can do to a draft. Defaults to the real endpoints; the admin "Test draftu"
 * page swaps in a local simulation so the exact same UI (and the exact same audio cues) can be
 * driven without a match, a lobby, or nine other people.
 */
export interface DraftActions {
  ban: (championId: number) => void;
  pick: (championId: number) => void;
  hover: (championId: number | null) => void;
  requestSwap: (request: { targetPlayerId: string; type: SwapType }) => void;
  respondSwap: (request: { swapId: string; accept: boolean }) => void;
  /** Blocks lock-in while a previous action is still in flight. */
  busy: boolean;
}

/** The real thing: every action goes to the backend. */
function useApiDraftActions(matchId: string): DraftActions {
  const ban = useDraftBan(matchId);
  const pick = useDraftPick(matchId);
  const hover = useDraftHover(matchId);
  const requestSwap = useRequestSwap(matchId);
  const respondSwap = useRespondSwap(matchId);
  return {
    ban: (championId) => ban.mutate(championId),
    pick: (championId) => pick.mutate(championId),
    hover: (championId) => hover.mutate(championId),
    requestSwap: (request) => requestSwap.mutate(request),
    respondSwap: (request) => respondSwap.mutate(request),
    busy: ban.isPending || pick.isPending,
  };
}

/**
 * Tournament draft board.
 *
 * Laid out as a full-viewport stage: both teams, both ban strips, the timer and the champion pool are
 * on screen at once with no page scrolling — only the champion grid scrolls, inside its own box. The
 * on-clock player's pre-selection ("hover") is pushed to the server and rendered for everyone on both
 * teams, and a slot filled in by the expiring timer is labelled as such rather than left blank.
 */
export function DraftBoard({ lobby, myPlayerId, streamState, onCollapse, fullscreen = true, actions }: {
  lobby: DrawLobby;
  myPlayerId: string;
  streamState?: StreamState;
  onCollapse?: () => void;
  fullscreen?: boolean;
  /** Overrides the API calls — see {@link DraftActions}. */
  actions?: DraftActions;
}) {
  const draft = lobby.draft;
  const champions = useChampions();
  const apiActions = useApiDraftActions(lobby.matchId);
  const act = actions ?? apiActions;
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
    act.hover(championId === selected ? null : championId);
  };
  const lockIn = () => {
    if (selected == null || draft.paused) return;
    if (myTurnBan) act.ban(selected);
    else if (myTurnPick) act.pick(selected);
    setSearch('');
  };

  const phaseLabel = isDone ? 'Draft zakończony'
    : draft.currentType === 'BAN' ? 'Faza banów' : 'Faza wyborów';
  const sideName = draft.currentSide === 'BLUE' ? 'Niebiescy' : draft.currentSide === 'RED' ? 'Czerwoni' : '';
  const onClockName = all.find((p) => p.playerId === draft.currentPlayerId)?.nickname;
  const mySwaps = draft.swaps.filter((s) => s.toPlayerId === myPlayerId || s.fromPlayerId === myPlayerId);

  const teamProps = {
    champById, draft, myPlayerId, isDone, mySide, swapMenu, setSwapMenu,
    onSwap: (target: string, type: SwapType) => {
      act.requestSwap({ targetPlayerId: target, type });
      setSwapMenu(null);
    },
  };

  return (
    <section
      className={cn(
        // `.draft-stage` carries its own grid texture, so no `grid-tex` here.
        'draft-stage flex flex-col gap-3 overflow-hidden',
        fullscreen
          // The players' view: the board owns the whole viewport.
          ? 'fixed inset-0 z-40 h-[100dvh] p-3 sm:p-4'
          // Collapsed into a panel it still goes edge to edge — `.draft-bleed` uncaps the layout
          // container's max-width (see index.css), so nothing is squeezed into a narrow column.
          : 'draft-bleed draw-stage min-h-[86dvh] rounded-xl border-2 border-line-strong p-3 sm:p-5',
      )}
    >
      {/* --- Top bar: phase, clock, sound, connection ------------------------------------ */}
      <header className="flex shrink-0 flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="min-w-0">
          <div className="kicker text-gold">Draft turniejowy</div>
          <h2 className="truncate font-display text-2xl leading-tight text-text-hi sm:text-3xl">{phaseLabel}</h2>
          {!isDone && (
            <p className="text-sm text-text sm:text-base">
              Teraz:{' '}
              <strong style={{ color: draft.currentSide === 'BLUE' ? 'var(--blue)' : 'var(--red)' }}>
                {sideName}
              </strong>{' '}
              — {draft.currentType === 'BAN' ? 'ban (kapitan)' : 'wybór postaci'}
              {onClockName && <> · <strong className="text-gold">{onClockName}</strong></>}
            </p>
          )}
        </div>

        <div className="flex items-center gap-3 sm:gap-4">
          {!isDone && (
            <div className="text-right">
              {draft.paused ? (
                <div className="num font-display text-3xl font-bold text-pending sm:text-4xl">⏸ PAUZA</div>
              ) : (
                <>
                  <div className={cn('num font-display text-4xl font-bold leading-none sm:text-5xl',
                    remaining <= 5 ? 'on-clock-tag text-loss' : 'text-text-hi')}>
                    0:{String(remaining).padStart(2, '0')}
                  </div>
                  <div className="mt-2 h-2 w-28 overflow-hidden rounded-full bg-bg-2 sm:w-36">
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
      <div className="grid min-h-0 flex-1 gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.35fr)_minmax(0,1fr)] sm:gap-4">
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
              onClockName={onClockName}
              hoverChamp={draft.hoverChampionId != null ? champById.get(draft.hoverChampionId) : undefined}
              banning={draft.currentType === 'BAN'}
              step={draft.currentIndex + 1}
              totalSteps={draft.sequence.length}
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
                  disabled={selected == null || act.busy || draft.paused}
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
                          onClick={() => act.respondSwap({ swapId: s.id, accept: true })}>Akceptuj</Button>
                      )}
                      <Button variant="danger" size="sm"
                        onClick={() => act.respondSwap({ swapId: s.id, accept: false })}>
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

      {/* Talking to your team during bans used to mean leaving the board for Discord. */}
      <DraftChat matchId={lobby.matchId} mySide={mySide} className="h-44 shrink-0 sm:h-52" />
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
    <div className="on-clock-frame flex min-h-0 flex-1 flex-col rounded-xl bg-[color:var(--bg-1)] p-3">
      <div className="mb-2.5 flex shrink-0 flex-wrap items-center justify-between gap-2">
        <div className={cn('font-display text-xl font-bold sm:text-2xl',
          banning ? 'text-loss' : 'text-gold', !disabled && 'on-clock-tag')}>
          {banning ? '🚫 TWOJA KOLEJ — BANUJ' : '⚡ TWOJA KOLEJ — WYBIERZ POSTAĆ'}
        </div>
        <input value={search} onChange={(e) => onSearch(e.target.value)} placeholder="Szukaj postaci…"
          className="w-full rounded-md border border-line-strong bg-[color:var(--bg-2)] px-3 py-2 text-sm sm:w-52" />
      </div>
      <div className="grid min-h-0 flex-1 auto-rows-min grid-cols-6 gap-2 overflow-y-auto pr-1 sm:grid-cols-8 lg:grid-cols-8 xl:grid-cols-10 2xl:grid-cols-12">
        {champions.map((c) => (
          <button key={c.id} type="button" title={c.name} disabled={disabled}
            onClick={() => onPick(c.id)}
            className={cn('rounded-md ring-2 transition hover:scale-105 disabled:opacity-40',
              selected === c.id ? 'scale-105 ring-gold shadow-glow-gold' : 'ring-transparent hover:ring-gold/60')}>
            <ChampionIcon iconUrl={c.iconUrl} name={c.name} size={52} />
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
function WaitingCard({ paused, onClockName, hoverChamp, banning, step, totalSteps }: {
  paused: boolean; onClockName?: string; hoverChamp?: Champion; banning: boolean;
  step: number; totalSteps: number;
}) {
  return (
    <div className="relative flex min-h-0 flex-1 flex-col items-center justify-center gap-4 rounded-xl border-2 border-line-strong bg-[color:var(--bg-1)] p-5 text-center">
      {/* Progress along the pick/ban sequence — otherwise this panel is a large empty box for the
          nine players who are not on the clock. */}
      <div className="absolute inset-x-0 top-0 flex flex-col gap-2 p-4">
        <div className="flex items-baseline justify-between">
          <span className="kicker">Krok {step} / {totalSteps}</span>
          <span className={cn('kicker', banning ? 'text-loss' : 'text-gold')}>
            {banning ? 'ban' : 'wybór'}
          </span>
        </div>
        <div className="flex gap-1">
          {Array.from({ length: totalSteps }).map((_, i) => (
            <span key={i} className={cn('h-1 flex-1 rounded-full',
              i < step - 1 ? 'bg-[color:var(--gold)]/60'
                : i === step - 1 ? 'on-clock-tag bg-gold'
                : 'bg-bg-3')} />
          ))}
        </div>
      </div>

      {paused ? (
        <p className="font-display text-xl text-pending">⏸ Draft wstrzymany przez admina.</p>
      ) : (
        <>
          <p className="text-base text-text">
            {onClockName
              ? <>Tura: <strong className="font-display text-xl text-gold">{onClockName}</strong></>
              : 'Czekaj na swoją kolej…'}
          </p>
          {hoverChamp ? (
            <div className="flex flex-col items-center gap-3">
              <ChampionIcon iconUrl={hoverChamp.iconUrl} name={hoverChamp.name} size={128}
                className={cn('ring-2', banning ? 'ring-loss' : 'ring-gold')} />
              <div>
                <div className="font-display text-2xl text-text-hi">{hoverChamp.name}</div>
                <div className={cn('kicker on-clock-tag', banning ? 'text-loss' : 'text-gold')}>
                  {banning ? 'zaraz zbanuje' : 'zaraz wybierze'}
                </div>
              </div>
            </div>
          ) : (
            <div className="grid h-32 w-32 place-items-center rounded-lg border-2 border-dashed border-line-strong text-5xl text-text-lo">
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
  onSwap: (targetPlayerId: string, type: SwapType) => void;
}) {
  const active = draft.currentSide === side && draft.status !== 'DONE';
  return (
    <div className={cn('flex min-h-0 flex-col overflow-hidden rounded-xl border-2 bg-[color:var(--bg-1)]',
      active ? 'border-gold shadow-glow-gold' : 'border-line-strong')}>
      <div className="flex shrink-0 items-center justify-between px-3 py-2.5 font-display text-base font-bold"
        style={{ color, background: `color-mix(in srgb, ${color} 22%, transparent)` }}>
        <span>{title}</span>
        {active && <Badge tone="win">tura</Badge>}
      </div>

      {/* Ban strip */}
      <div className="flex shrink-0 items-center gap-2 border-b border-line-strong bg-[color:var(--bg)]/50 px-3 py-2">
        <span className="kicker text-text-lo">Bany</span>
        {Array.from({ length: 5 }).map((_, i) => {
          const champ = bans[i] != null ? champById.get(bans[i]) : undefined;
          return bans[i] != null
            ? <ChampionIcon key={i} iconUrl={champ?.iconUrl} name={champ?.name} size={30}
                className="opacity-70 grayscale ring-loss/50" />
            : <span key={i} className="h-[30px] w-[30px] rounded-md border border-dashed border-line-strong" />;
        })}
      </div>

      {/* Centred rather than stretched: five rows spread over a tall column left each one a mostly
          empty 170px slab. Capped heights keep them tight and the team grouped. */}
      <div className="flex min-h-0 flex-1 flex-col justify-center gap-1.5 overflow-y-auto p-1.5">
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
              className={cn(
                'relative flex items-center gap-3 rounded-[var(--r-sm)] px-3 transition-all duration-300',
                onClock
                  // Whoever is on the clock gets a frame that is both larger and pulsing.
                  ? 'on-clock-frame z-10 max-h-[128px] flex-[1.5] bg-[color:var(--gold)]/15 py-2.5'
                  // Same 2px border as the frame, so switching turns shifts nothing sideways.
                  : 'max-h-[88px] flex-1 border-2 border-line bg-[color:var(--bg-2)]/60 py-2',
              )}>
              <div className={cn('shrink-0', isPreview && 'rounded-md opacity-70 ring-2 ring-gold')}>
                <ChampionIcon iconUrl={champ?.iconUrl} name={champ?.name} size={onClock ? 54 : 40} />
              </div>
              <div className="min-w-0 flex-1">
                <div className={cn('flex items-center gap-1.5 font-medium text-text-hi',
                  onClock ? 'font-display text-base font-bold sm:text-lg' : 'text-sm')}>
                  <span className="truncate">{player.nickname}</span>
                  {player.captain && <span title="Kapitan (robi bany)" className="text-gold">★</span>}
                  {player.playerId === myPlayerId && <span className="kicker text-cyan">(Ty)</span>}
                </div>
                <div className={cn('truncate', onClock ? 'text-sm text-text' : 'text-xs text-text-lo')}>
                  {roleLabel(player.role)}
                  {champ ? ` · ${champ.name}` : ''}
                  {isPreview && <span className="text-gold"> · zaznaczono</span>}
                </div>
                {onClock && (
                  <div className="on-clock-tag kicker mt-0.5 text-gold">
                    {draft.currentType === 'BAN' ? '▶ teraz banuje' : '▶ teraz wybiera'}
                  </div>
                )}
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
  const notifiedTurn = useRef<number | null>(null);
  const remindedTurn = useRef<number | null>(null);
  const previousCountdown = useRef<{ index: number; remaining: number } | null>(null);
  const wasDone = useRef(false);
  const lastTick = useRef<number | null>(null);
  const { unlocked } = useSoundSettings();

  useEffect(() => {
    sound.armOnFirstGesture();
  }, []);

  // Music runs for the duration of the draft only.
  //
  // The dependency is deliberately a boolean, not `draft`: every pick, ban and clock push hands us a
  // brand-new draft object, and depending on it re-ran this effect — stopping and restarting the
  // track from the top on every action. The bed now just loops until the draft is over, at which
  // point `stopMusic()` fades it out.
  const musicWanted = Boolean(draft) && !isDone;
  useEffect(() => {
    if (!musicWanted) return;
    void sound.startMusic();
    // Draft finished, or the board went away: fade out.
    return () => sound.stopMusic();
  }, [musicWanted]);

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
    if (!draft || !myTurn || !unlocked || notifiedTurn.current === draft.currentIndex) return;
    sound.play('yourTurn');
    notifiedTurn.current = draft.currentIndex;
  }, [draft, myTurn, unlocked]);

  // Remind the active player once when their clock crosses 10 seconds. Tracking the crossing avoids
  // a double cue when the board opens or audio is unlocked in the middle of an already-running turn.
  useEffect(() => {
    if (!draft) return;
    const previous = previousCountdown.current;
    const crossedReminder = previous?.index === draft.currentIndex
      && previous.remaining > 10
      && remaining <= 10;
    previousCountdown.current = { index: draft.currentIndex, remaining };

    if (myTurn && unlocked && !draft.paused && crossedReminder
      && remindedTurn.current !== draft.currentIndex) {
      sound.play('yourTurn');
      remindedTurn.current = draft.currentIndex;
    }
  }, [draft, myTurn, remaining, unlocked]);

  useEffect(() => {
    if (isDone && !wasDone.current) sound.play('draftDone');
    wasDone.current = isDone;
  }, [isDone]);

  // Last five seconds: one beep per second, with the music pulled down underneath it so nobody can
  // miss the clock running out — whether it is their pick or not.
  useEffect(() => {
    const countingDown = !isDone && !draft?.paused && remaining > 0 && remaining <= 5;
    sound.duckMusic(countingDown);
    if (!countingDown) {
      lastTick.current = null;
      return;
    }
    if (lastTick.current === remaining) return;
    lastTick.current = remaining;
    sound.play('tick');
  }, [remaining, isDone, draft?.paused]);
}
