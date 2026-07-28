import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { API_BASE, api } from '../client';
import type { DrawLobby, DrawVoteDecision } from '../types';
import { useAuthStore } from '../../store/auth';

const DRAW_KEY = ['draw-lobby', 'active'] as const;

/** Phases where a stale screen is actively harmful, so we poll hard as an SSE backstop. */
const HOT_STATUSES = ['TEAMS_DRAWN', 'DRAFT_READY', 'DRAFTING', 'DRAFTED', 'LOBBY_READY'];

export type StreamState = 'connecting' | 'live' | 'offline';

/**
 * Active lobby for the logged-in player.
 *
 * The push stream is the fast path, but it must not be the only path: SSE dies when a laptop wakes
 * up, a proxy hiccups, a phone switches network, or a token is refreshed. The query previously had
 * no refetch interval at all, so any of those left the draft board frozen until the player pressed
 * F5 — the reported "draft stops working after a refresh". Now REST polling runs on its own whenever
 * a lobby is in a live phase, and `streamState` reports which path is carrying updates so the UI can
 * say so out loud instead of looking broken.
 */
export function useDrawLobby() {
  const queryClient = useQueryClient();
  const token = useAuthStore((state) => state.accessToken);
  const [streamState, setStreamState] = useState<StreamState>('connecting');

  const query = useQuery({
    queryKey: DRAW_KEY,
    // Coalesce the 204/empty "no active lobby" response to null — React Query rejects `undefined`.
    queryFn: async () => (await api.get<DrawLobby | undefined>('/draw-lobby/active')) ?? null,
    refetchOnWindowFocus: true,
    refetchInterval: (q) =>
      HOT_STATUSES.includes(q.state.data?.status ?? '') ? 2_500 : 15_000,
    refetchIntervalInBackground: true,
  });

  useEffect(() => {
    if (!token) {
      setStreamState('offline');
      return;
    }
    const controller = new AbortController();
    let stopped = false;
    // Exponential backoff on repeated failures so a dead session cannot become a request flood.
    let failures = 0;

    const connect = async () => {
      while (!stopped) {
        try {
          setStreamState(failures === 0 ? 'connecting' : 'offline');
          const response = await fetch(`${API_BASE}/draw-lobby/stream`, {
            headers: { Authorization: `Bearer ${useAuthStore.getState().accessToken}` },
            signal: controller.signal,
          });
          if (response.status === 401) {
            // Let the API client run its single-flight refresh, then retry with the new token. This
            // used to `continue` with no delay and no failure counter, which spun into a tight loop
            // hammering the backend the moment a refresh token went stale.
            failures += 1;
            try {
              await api.get('/auth/me');
            } catch {
              // Refresh failed; the auth store is cleared and this effect is about to tear down.
            }
            throw new Error('unauthorized');
          }
          if (!response.ok || !response.body) throw new Error('stream unavailable');

          failures = 0;
          setStreamState('live');
          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (!stopped) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split('\n\n');
            buffer = events.pop() ?? '';
            for (const event of events) {
              const data = event.split('\n').filter((line) => line.startsWith('data:'))
                .map((line) => line.slice(5).trim()).join('\n');
              if (!data || data === '{"ok":true}') continue;
              try {
                queryClient.setQueryData(DRAW_KEY, JSON.parse(data) as DrawLobby);
              } catch {
                // A truncated frame is not worth dropping the whole connection over.
              }
            }
          }
          setStreamState('offline');
        } catch {
          if (controller.signal.aborted) return;
          failures += 1;
          setStreamState('offline');
        }
        // 1.2s, 2.4s, 4.8s … capped at 15s.
        const delay = Math.min(15_000, 1_200 * 2 ** Math.max(0, failures - 1));
        await new Promise((resolve) => window.setTimeout(resolve, delay));
      }
    };
    void connect();
    return () => { stopped = true; controller.abort(); };
  }, [token, queryClient]);

  return { ...query, streamState };
}

/** Pulls the lobby immediately — used after every draft action so nothing waits on the stream. */
function useRefreshLobby() {
  const queryClient = useQueryClient();
  return useCallback(
    () => { void queryClient.invalidateQueries({ queryKey: DRAW_KEY }); },
    [queryClient],
  );
}

export function useVoteOnDraw() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ matchId, decision }: { matchId: string; decision: DrawVoteDecision }) =>
      api.post<DrawLobby>('/draw-lobby/vote', { matchId, decision }),
    onSuccess: (state) => queryClient.setQueryData(DRAW_KEY, state),
  });
}

// --- Draft actions -----------------------------------------------------------------------
// The endpoints return 204 and fresh state normally arrives over SSE, but every action also refetches
// on settle: if the stream happens to be down, the player still sees their own ban/pick land instead
// of staring at a board that looks stuck.

export function useDraftBan(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: (championId: number) => api.post<void>(`/draft/${matchId}/ban`, { championId }),
    onSettled: refresh,
  });
}

export function useDraftPick(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: (championId: number) => api.post<void>(`/draft/${matchId}/pick`, { championId }),
    onSettled: refresh,
  });
}

/** Broadcasts the on-clock player's pre-selection to both teams. Null clears it. */
export function useDraftHover(matchId: string) {
  return useMutation({
    mutationFn: (championId: number | null) =>
      api.post<void>(`/draft/${matchId}/hover`, { championId }),
  });
}

/** Admin/editor correction for a player who locked in the wrong champion. */
export function useAdminSetChampion(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: ({ playerId, championId }: { playerId: string; championId: number | null }) =>
      api.post<void>(`/draft/${matchId}/champion`, { playerId, championId }),
    onSettled: refresh,
  });
}

export function useRequestSwap(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: ({ targetPlayerId, type }: { targetPlayerId: string; type: 'POSITION' | 'CHAMPION' }) =>
      api.post<void>(`/draft/${matchId}/swap`, { targetPlayerId, type }),
    onSettled: refresh,
  });
}

export function useRespondSwap(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: ({ swapId, accept }: { swapId: string; accept: boolean }) =>
      api.post<void>(`/draft/${matchId}/swap/${swapId}/${accept ? 'accept' : 'cancel'}`),
    onSettled: refresh,
  });
}

export function useResetDraft(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: () => api.post<void>(`/draft/${matchId}/reset`),
    onSettled: refresh,
  });
}

export function useStartDraft(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: () => api.post<void>(`/draft/${matchId}/start`),
    onSettled: refresh,
  });
}

export function usePauseDraft(matchId: string) {
  const refresh = useRefreshLobby();
  return useMutation({
    mutationFn: (paused: boolean) => api.post<void>(`/draft/${matchId}/${paused ? 'pause' : 'resume'}`),
    onSettled: refresh,
  });
}

/**
 * Seconds left on a server deadline, ticking locally.
 *
 * `serverNow` is the `updatedAt` stamp the backend put on the same payload as the deadline. The
 * difference between it and the local clock is the client's skew, so a device whose clock is minutes
 * off still counts down to the right moment instead of showing 0:00 (or 4:59) for the whole step.
 */
export function useServerCountdown(deadline: string | null, serverNow?: string): number {
  const [, tick] = useState(0);
  const skewRef = useRef(0);
  const skewAnchor = useRef<string | undefined>(undefined);

  if (serverNow && skewAnchor.current !== serverNow) {
    skewAnchor.current = serverNow;
    skewRef.current = Date.now() - new Date(serverNow).getTime();
  }

  useEffect(() => {
    if (!deadline) return;
    const id = window.setInterval(() => tick((n) => n + 1), 250);
    return () => window.clearInterval(id);
  }, [deadline]);

  if (!deadline) return 0;
  const target = new Date(deadline).getTime() + skewRef.current;
  return Math.max(0, Math.ceil((target - Date.now()) / 1000));
}
