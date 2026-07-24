import { create } from 'zustand';

/**
 * Tracks reachability of the backend so the app can show a "technical break" overlay when it is
 * down. `bootId` is the backend's per-process id (from GET /config); the monitor compares it to
 * detect a restart. Kept separate from the auth store so a network blip never touches tokens.
 */
interface ServerStatusState {
  reachable: boolean;
  bootId: string | null;
  markReachable: (bootId: string) => void;
  markUnreachable: () => void;
}

export const useServerStatus = create<ServerStatusState>((set) => ({
  // Start optimistic: assume up until a poll or request proves otherwise.
  reachable: true,
  bootId: null,
  markReachable: (bootId) => set({ reachable: true, bootId }),
  markUnreachable: () => set({ reachable: false }),
}));
