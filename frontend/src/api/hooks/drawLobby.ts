import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { API_BASE, api } from '../client';
import type { DrawLobby, DrawVoteDecision } from '../types';
import { useAuthStore } from '../../store/auth';

const DRAW_KEY = ['draw-lobby', 'active'] as const;

export function useDrawLobby() {
  const queryClient = useQueryClient();
  const token = useAuthStore((state) => state.accessToken);
  const query = useQuery({
    queryKey: DRAW_KEY,
    queryFn: () => api.get<DrawLobby | undefined>('/draw-lobby/active'),
    refetchOnWindowFocus: true,
  });

  useEffect(() => {
    if (!token) return;
    const controller = new AbortController();
    let stopped = false;

    const connect = async () => {
      while (!stopped) {
        try {
          const response = await fetch(`${API_BASE}/draw-lobby/stream`, {
            headers: { Authorization: `Bearer ${useAuthStore.getState().accessToken}` },
            signal: controller.signal,
          });
          if (response.status === 401) {
            // The normal API client performs the single-flight refresh.
            await api.get('/auth/me');
            continue;
          }
          if (!response.ok || !response.body) throw new Error('stream unavailable');
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
              queryClient.setQueryData(DRAW_KEY, JSON.parse(data) as DrawLobby);
            }
          }
        } catch {
          if (controller.signal.aborted) return;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 1200));
      }
    };
    void connect();
    return () => { stopped = true; controller.abort(); };
  }, [token, queryClient]);

  return query;
}

export function useVoteOnDraw() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ matchId, decision }: { matchId: string; decision: DrawVoteDecision }) =>
      api.post<DrawLobby>('/draw-lobby/vote', { matchId, decision }),
    onSuccess: (state) => queryClient.setQueryData(DRAW_KEY, state),
  });
}