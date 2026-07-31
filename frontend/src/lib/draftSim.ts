import { useCallback, useEffect, useRef, useState } from 'react';
import type {
  Champion, DraftStepView, DraftSwapView, DrawLobby, LobbyPlayer, Role, Side, SwapType,
} from '../api/types';
import type { DraftActions } from '../pages/player/DraftBoard';

/**
 * A whole tournament draft, played out in the browser against scripted opponents.
 *
 * This exists so the draft can be *seen* — the real board, the real audio, the real clock — without
 * a match, a lobby and nine other people. It is a faithful re-implementation of the server rules
 * (`DraftState` / `DraftService`), not a mock of the API: the same pick/ban order, the same
 * captain-bans rule, the same "timer locks whatever you hovered" behaviour. Anything that diverges
 * here is a bug in this file, not a licence for the real draft to behave differently.
 */

export const SIM_MATCH_ID = 'sim-draft';

const ROLES: Role[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const BLUE_NAMES = ['Kapitan Niebieskich', 'Nocny Łowca', 'Mid Diff', 'Ostatni Strzał', 'Tarcza'];
const RED_NAMES = ['Kapitan Czerwonych', 'Cichy Gank', 'Zimna Krew', 'Celny Strzał', 'Anioł Stróż'];

/** The canonical LoL tournament order — mirrors `DraftState.tournamentSequence()`. */
export const TOURNAMENT_SEQUENCE: DraftStepView[] = [
  // Ban phase 1: B R B R B R
  { side: 'BLUE', type: 'BAN' }, { side: 'RED', type: 'BAN' },
  { side: 'BLUE', type: 'BAN' }, { side: 'RED', type: 'BAN' },
  { side: 'BLUE', type: 'BAN' }, { side: 'RED', type: 'BAN' },
  // Pick phase 1: B R R B B R
  { side: 'BLUE', type: 'PICK' }, { side: 'RED', type: 'PICK' }, { side: 'RED', type: 'PICK' },
  { side: 'BLUE', type: 'PICK' }, { side: 'BLUE', type: 'PICK' }, { side: 'RED', type: 'PICK' },
  // Ban phase 2: R B R B
  { side: 'RED', type: 'BAN' }, { side: 'BLUE', type: 'BAN' },
  { side: 'RED', type: 'BAN' }, { side: 'BLUE', type: 'BAN' },
  // Pick phase 2: R B B R
  { side: 'RED', type: 'PICK' }, { side: 'BLUE', type: 'PICK' },
  { side: 'BLUE', type: 'PICK' }, { side: 'RED', type: 'PICK' },
];

/** How long a bot dithers before locking, as a fraction of the step. */
export type BotSpeed = 'fast' | 'normal' | 'slow';

const BOT_TIMING: Record<BotSpeed, { hover: number; lockMin: number; lockMax: number }> = {
  fast: { hover: 0.08, lockMin: 0.18, lockMax: 0.35 },
  normal: { hover: 0.22, lockMin: 0.40, lockMax: 0.70 },
  // Deliberately close to the buzzer, so the last-5-seconds tick cue gets exercised.
  slow: { hover: 0.45, lockMin: 0.78, lockMax: 0.94 },
};

export interface SimOptions {
  champions: Champion[];
  stepSeconds: number;
  botSpeed: BotSpeed;
  /** Seat the admin occupies, or null to watch all ten play themselves. */
  mySeat: string | null;
  /** Hand the admin's own turns to a bot as well — useful for watching the full sequence. */
  autoPlayMe: boolean;
}

const playerId = (side: Side, index: number) => `sim-${side === 'BLUE' ? 'b' : 'r'}${index}`;

export function simRoster(): LobbyPlayer[] {
  const seat = (side: Side, index: number): LobbyPlayer => ({
    playerId: playerId(side, index),
    nickname: (side === 'BLUE' ? BLUE_NAMES : RED_NAMES)[index],
    avatarUrl: null,
    role: ROLES[index],
    side,
    championId: null,
    captain: index === 0,
  });
  return [
    ...ROLES.map((_, i) => seat('BLUE', i)),
    ...ROLES.map((_, i) => seat('RED', i)),
  ];
}

// --- pure helpers ----------------------------------------------------------------------------

const picksConsumed = (draft: NonNullable<DrawLobby['draft']>, side: Side) =>
  draft.sequence
    .slice(0, draft.currentIndex)
    .filter((step) => step.type === 'PICK' && step.side === side).length;

/** Who is on the clock: bans belong to the captain, picks flow down the team's draft order. */
function onClock(draft: NonNullable<DrawLobby['draft']>): string | null {
  const step = draft.sequence[draft.currentIndex];
  if (!step) return null;
  if (step.type === 'BAN') {
    return step.side === 'BLUE' ? draft.blueCaptain : draft.redCaptain;
  }
  const order = step.side === 'BLUE' ? draft.blueOrder : draft.redOrder;
  return order[picksConsumed(draft, step.side)] ?? null;
}

/** Recomputes everything derived from the step pointer. */
function withDerived(lobby: DrawLobby, draft: NonNullable<DrawLobby['draft']>): DrawLobby {
  const step = draft.sequence[draft.currentIndex];
  const done = !step;
  const next: NonNullable<DrawLobby['draft']> = {
    ...draft,
    status: done ? 'DONE' : 'DRAFTING',
    currentSide: step?.side ?? null,
    currentType: step?.type ?? null,
    currentPlayerId: done ? null : onClock(draft),
    deadline: done ? null : draft.deadline,
  };
  return {
    ...lobby,
    status: done ? 'DRAFTED' : 'DRAFTING',
    draft: next,
    updatedAt: new Date().toISOString(),
  };
}

function unavailableIds(lobby: DrawLobby): Set<number> {
  const draft = lobby.draft;
  const taken = new Set<number>();
  draft?.blueBans.forEach((id) => taken.add(id));
  draft?.redBans.forEach((id) => taken.add(id));
  [...lobby.blue, ...lobby.red].forEach((p) => { if (p.championId != null) taken.add(p.championId); });
  return taken;
}

function randomAvailable(lobby: DrawLobby, champions: Champion[]): number | null {
  const taken = unavailableIds(lobby);
  const pool = champions.filter((c) => !taken.has(c.id));
  if (pool.length === 0) return null;
  return pool[Math.floor(Math.random() * pool.length)].id;
}

/**
 * Locks {@code championId} into the current step and moves the pointer on. Mirrors the server:
 * an empty selection at the buzzer becomes a random available champion, and the step it filled is
 * recorded so the board can label it as auto-picked.
 */
export function applyChampion(lobby: DrawLobby, championId: number, stepSeconds: number,
                              auto: boolean): DrawLobby {
  const draft = lobby.draft;
  const step = draft?.sequence[draft.currentIndex];
  if (!draft || !step) return lobby;

  let blue = lobby.blue;
  let red = lobby.red;
  const blueBans = [...draft.blueBans];
  const redBans = [...draft.redBans];

  if (step.type === 'BAN') {
    (step.side === 'BLUE' ? blueBans : redBans).push(championId);
  } else {
    const target = draft.currentPlayerId;
    const assign = (players: LobbyPlayer[]) =>
      players.map((p) => (p.playerId === target ? { ...p, championId } : p));
    if (step.side === 'BLUE') blue = assign(blue);
    else red = assign(red);
  }

  const nextIndex = draft.currentIndex + 1;
  const finished = nextIndex >= draft.sequence.length;
  return withDerived({ ...lobby, blue, red }, {
    ...draft,
    blueBans,
    redBans,
    currentIndex: nextIndex,
    hoverChampionId: null,
    hoverPlayerId: null,
    autoResolvedSteps: auto ? [...draft.autoResolvedSteps, draft.currentIndex] : draft.autoResolvedSteps,
    deadline: finished ? null : new Date(Date.now() + stepSeconds * 1000).toISOString(),
  });
}

export function buildSimLobby(stepSeconds: number): DrawLobby {
  const roster = simRoster();
  const blue = roster.filter((p) => p.side === 'BLUE');
  const red = roster.filter((p) => p.side === 'RED');
  const base: DrawLobby = {
    matchId: SIM_MATCH_ID,
    status: 'DRAFTING',
    round: 1,
    requiredAccepts: 10,
    accepts: 10,
    rejects: 0,
    acceptedPlayerIds: roster.map((p) => p.playerId),
    rejectedPlayerIds: [],
    blue,
    red,
    updatedAt: new Date().toISOString(),
    tournamentCode: null,
    riotImportError: null,
    voteDeadline: null,
    // The simulator drops straight into a running draft, so the pre-draft setup is already behind it.
    setup: null,
    draft: {
      status: 'DRAFTING',
      currentIndex: 0,
      deadline: new Date(Date.now() + stepSeconds * 1000).toISOString(),
      currentSide: null,
      currentType: null,
      blueCaptain: blue[0].playerId,
      redCaptain: red[0].playerId,
      currentPlayerId: null,
      paused: false,
      blueOrder: blue.map((p) => p.playerId),
      redOrder: red.map((p) => p.playerId),
      blueBans: [],
      redBans: [],
      sequence: TOURNAMENT_SEQUENCE,
      swaps: [],
      hoverChampionId: null,
      hoverPlayerId: null,
      autoResolvedSteps: [],
      stepSeconds,
    },
  };
  return withDerived(base, base.draft!);
}

// --- the hook --------------------------------------------------------------------------------

interface BotPlan { index: number; championId: number; hoverAt: number; lockAt: number; }

export interface DraftSimulation {
  lobby: DrawLobby | null;
  actions: DraftActions;
  running: boolean;
  finished: boolean;
  start: () => void;
  stop: () => void;
  togglePause: () => void;
  skipStep: () => void;
  finish: () => void;
}

/**
 * Drives the simulation on a 200 ms tick.
 *
 * Every decision (which champion a bot wants, when it locks) is made in the tick callback and
 * committed as a finished value — never inside a `setState` updater. React 18's StrictMode invokes
 * updaters twice in development, which would otherwise advance the draft two steps per tick.
 */
export function useDraftSimulation(options: SimOptions): DraftSimulation {
  const [lobby, setLobby] = useState<DrawLobby | null>(null);
  const [running, setRunning] = useState(false);
  const lobbyRef = useRef<DrawLobby | null>(null);
  const planRef = useRef<BotPlan | null>(null);
  /** Milliseconds left on the step when the admin hit pause. */
  const pausedRemainingRef = useRef<number | null>(null);
  const swapSeenRef = useRef(new Map<string, number>());
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const commit = useCallback((next: DrawLobby | null) => {
    lobbyRef.current = next;
    setLobby(next);
  }, []);

  const start = useCallback(() => {
    planRef.current = null;
    swapSeenRef.current = new Map();
    commit(buildSimLobby(optionsRef.current.stepSeconds));
    setRunning(true);
  }, [commit]);

  const stop = useCallback(() => {
    setRunning(false);
    planRef.current = null;
    commit(null);
  }, [commit]);

  const isBot = useCallback((id: string | null) => {
    const { mySeat, autoPlayMe } = optionsRef.current;
    if (id == null) return false;
    return id !== mySeat || autoPlayMe;
  }, []);

  const lock = useCallback((championId: number, auto: boolean) => {
    const current = lobbyRef.current;
    if (!current) return;
    planRef.current = null;
    commit(applyChampion(current, championId, optionsRef.current.stepSeconds, auto));
  }, [commit]);

  const togglePause = useCallback(() => {
    const current = lobbyRef.current;
    if (!current?.draft) return;
    const paused = !current.draft.paused;
    if (paused) {
      // Freeze the clock by clearing the deadline, keeping what was left for the resume.
      pausedRemainingRef.current = current.draft.deadline
        ? Math.max(0, new Date(current.draft.deadline).getTime() - Date.now())
        : optionsRef.current.stepSeconds * 1000;
      planRef.current = null;
    }
    const remaining = pausedRemainingRef.current ?? optionsRef.current.stepSeconds * 1000;
    commit({
      ...current,
      updatedAt: new Date().toISOString(),
      draft: {
        ...current.draft,
        paused,
        deadline: paused ? null : new Date(Date.now() + remaining).toISOString(),
      },
    });
  }, [commit]);

  const skipStep = useCallback(() => {
    const current = lobbyRef.current;
    if (!current?.draft || current.draft.status === 'DONE') return;
    const champion = current.draft.hoverChampionId
      ?? randomAvailable(current, optionsRef.current.champions);
    if (champion != null) lock(champion, true);
  }, [lock]);

  const finish = useCallback(() => {
    let current = lobbyRef.current;
    if (!current?.draft) return;
    // Bounded by the sequence length, so a champion pool too small to fill it cannot spin forever.
    for (let guard = 0; guard < TOURNAMENT_SEQUENCE.length + 1; guard++) {
      if (!current.draft || current.draft.status === 'DONE') break;
      const champion = randomAvailable(current, optionsRef.current.champions);
      if (champion == null) break;
      current = applyChampion(current, champion, optionsRef.current.stepSeconds, true);
    }
    planRef.current = null;
    commit(current);
  }, [commit]);

  // --- the clock ------------------------------------------------------------------------------
  useEffect(() => {
    if (!running) return;
    const id = window.setInterval(() => {
      const current = lobbyRef.current;
      const draft = current?.draft;
      if (!current || !draft || draft.status === 'DONE' || draft.paused) return;
      const { champions, stepSeconds, botSpeed } = optionsRef.current;
      const now = Date.now();

      // 1. The buzzer always wins: hovered champion if there is one, otherwise a random available.
      const deadline = draft.deadline ? new Date(draft.deadline).getTime() : null;
      if (deadline != null && now >= deadline) {
        const champion = draft.hoverChampionId ?? randomAvailable(current, champions);
        if (champion != null) lock(champion, true);
        return;
      }

      // 2. Bot on the clock — plan once per step, then hover and lock on schedule.
      if (isBot(draft.currentPlayerId)) {
        let plan = planRef.current;
        if (!plan || plan.index !== draft.currentIndex) {
          const championId = randomAvailable(current, champions);
          if (championId == null) return;
          const timing = BOT_TIMING[botSpeed];
          const stepStart = (deadline ?? now + stepSeconds * 1000) - stepSeconds * 1000;
          const lockFraction = timing.lockMin + Math.random() * (timing.lockMax - timing.lockMin);
          plan = {
            index: draft.currentIndex,
            championId,
            hoverAt: stepStart + timing.hover * stepSeconds * 1000,
            lockAt: stepStart + lockFraction * stepSeconds * 1000,
          };
          planRef.current = plan;
        }
        if (now >= plan.lockAt) {
          lock(plan.championId, false);
          return;
        }
        if (now >= plan.hoverAt && draft.hoverChampionId !== plan.championId) {
          commit({
            ...current,
            updatedAt: new Date().toISOString(),
            draft: { ...draft, hoverChampionId: plan.championId, hoverPlayerId: draft.currentPlayerId },
          });
          return;
        }
      }

      // 3. Bots accept swap requests after a beat, so the post-draft flow is testable too.
      const pending = draft.swaps.find((swap) => isBot(swap.toPlayerId));
      if (pending) {
        const seenAt = swapSeenRef.current.get(pending.id);
        if (seenAt == null) {
          swapSeenRef.current.set(pending.id, now);
        } else if (now - seenAt > 1_500) {
          swapSeenRef.current.delete(pending.id);
          commit(resolveSwap(current, pending.id, true));
        }
      }
    }, 200);
    return () => window.clearInterval(id);
  }, [running, commit, isBot, lock]);

  // Restarting the clock length mid-draft should be visible immediately, not at the next step.
  useEffect(() => {
    const current = lobbyRef.current;
    if (!current?.draft || current.draft.status === 'DONE') return;
    if (current.draft.stepSeconds === options.stepSeconds) return;
    commit({
      ...current,
      updatedAt: new Date().toISOString(),
      draft: {
        ...current.draft,
        stepSeconds: options.stepSeconds,
        deadline: current.draft.paused
          ? null
          : new Date(Date.now() + options.stepSeconds * 1000).toISOString(),
      },
    });
    planRef.current = null;
  }, [options.stepSeconds, commit]);

  const actions: DraftActions = {
    ban: (championId) => lock(championId, false),
    pick: (championId) => lock(championId, false),
    hover: (championId) => {
      const current = lobbyRef.current;
      if (!current?.draft) return;
      commit({
        ...current,
        updatedAt: new Date().toISOString(),
        draft: {
          ...current.draft,
          hoverChampionId: championId,
          hoverPlayerId: championId == null ? null : current.draft.currentPlayerId,
        },
      });
    },
    requestSwap: ({ targetPlayerId, type }) => {
      const current = lobbyRef.current;
      const me = optionsRef.current.mySeat;
      if (!current?.draft || !me) return;
      const swap: DraftSwapView = {
        id: `sim-swap-${current.draft.swaps.length + 1}`,
        fromPlayerId: me,
        toPlayerId: targetPlayerId,
        type,
      };
      commit({
        ...current,
        updatedAt: new Date().toISOString(),
        draft: { ...current.draft, swaps: [...current.draft.swaps, swap] },
      });
    },
    respondSwap: ({ swapId, accept }) => {
      const current = lobbyRef.current;
      if (!current) return;
      commit(resolveSwap(current, swapId, accept));
    },
    busy: false,
  };

  return {
    lobby,
    actions,
    running,
    finished: lobby?.draft?.status === 'DONE',
    start,
    stop,
    togglePause,
    skipStep,
    finish,
  };
}

/** Applies (or drops) a swap request: positions and champions trade between two team-mates. */
function resolveSwap(lobby: DrawLobby, swapId: string, accept: boolean): DrawLobby {
  const draft = lobby.draft;
  if (!draft) return lobby;
  const swap = draft.swaps.find((s) => s.id === swapId);
  const swaps = draft.swaps.filter((s) => s.id !== swapId);
  if (!swap || !accept) {
    return { ...lobby, updatedAt: new Date().toISOString(), draft: { ...draft, swaps } };
  }
  const trade = (players: LobbyPlayer[]): LobbyPlayer[] => {
    const from = players.find((p) => p.playerId === swap.fromPlayerId);
    const to = players.find((p) => p.playerId === swap.toPlayerId);
    if (!from || !to) return players;
    return players.map((p) => {
      const other = p.playerId === from.playerId ? to : p.playerId === to.playerId ? from : null;
      if (!other) return p;
      return swap.type === 'POSITION' ? { ...p, role: other.role } : { ...p, championId: other.championId };
    });
  };
  return {
    ...lobby,
    blue: trade(lobby.blue),
    red: trade(lobby.red),
    updatedAt: new Date().toISOString(),
    draft: { ...draft, swaps },
  };
}

export type { SwapType };
