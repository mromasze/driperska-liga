import { create } from 'zustand';

/**
 * Tracks reachability of the backend so the app can show a "connection lost" and then a "technical
 * break" overlay when it is down. `bootId` is the backend's per-process id (from GET /config); the
 * monitor compares it to detect a restart. Kept separate from the auth store so a network blip never
 * touches tokens directly — the gate decides what to do about the session.
 *
 * `unreachableSince` drives the two-stage overlay: the first few seconds report that the connection
 * dropped and the session is being closed, after which it becomes the maintenance notice.
 */
interface ServerStatusState {
  reachable: boolean;
  bootId: string | null;
  /** Epoch millis of the first failed poll in the current outage; null while healthy. */
  unreachableSince: number | null;
  markReachable: (bootId: string) => void;
  markUnreachable: () => void;
}

export const useServerStatus = create<ServerStatusState>((set) => ({
  // Start optimistic: assume up until a poll or request proves otherwise.
  reachable: true,
  bootId: null,
  unreachableSince: null,
  markReachable: (bootId) => set({ reachable: true, bootId, unreachableSince: null }),
  markUnreachable: () =>
    set((state) => (state.reachable
      ? { reachable: false, unreachableSince: Date.now() }
      // Already down: keep the original timestamp so the stage doesn't reset on every retry.
      : { unreachableSince: state.unreachableSince ?? Date.now() })),
}));
