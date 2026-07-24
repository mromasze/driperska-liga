import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { PublicConfig } from '../../api/types';
import { useServerStatus } from '../../store/serverStatus';
import { useAuthStore } from '../../store/auth';
import { Spinner } from '../ui/States';

/** Persisted so a full page reload during an outage can still detect a restart on recovery. */
const BOOT_KEY = 'driperska-boot';

/**
 * Polls the public config endpoint to track backend availability. When the backend is unreachable
 * it renders a full-screen "technical break" overlay and keeps retrying. When the backend comes
 * back with a different bootId (i.e. it restarted) every session is invalidated, so we log out.
 */
export function ServerGate({ children }: { children: ReactNode }) {
  const reachable = useServerStatus((s) => s.reachable);
  const markReachable = useServerStatus((s) => s.markReachable);
  const markUnreachable = useServerStatus((s) => s.markUnreachable);

  const query = useQuery({
    queryKey: ['server-health'],
    queryFn: () => api.get<PublicConfig>('/config', { skipAuth: true }),
    // Poll slowly while healthy, quickly while trying to reconnect.
    refetchInterval: reachable ? 30_000 : 3_000,
    refetchIntervalInBackground: true,
    refetchOnWindowFocus: true,
    retry: false,
    gcTime: Infinity,
  });

  const bootId = query.data?.bootId;

  useEffect(() => {
    if (!query.isSuccess || !bootId) return;
    markReachable(bootId);
    const previous = localStorage.getItem(BOOT_KEY);
    if (previous && previous !== bootId) {
      // Backend restarted: tokens minted before the restart are rejected server-side, so drop them.
      useAuthStore.getState().clear();
    }
    localStorage.setItem(BOOT_KEY, bootId);
  }, [query.isSuccess, bootId, markReachable]);

  useEffect(() => {
    if (query.isError) markUnreachable();
  }, [query.isError, query.errorUpdatedAt, markUnreachable]);

  if (!reachable) {
    return (
      <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-5 bg-[var(--bg)] px-6 text-center">
        <Spinner className="h-8 w-8" />
        <div className="space-y-2">
          <h1 className="font-display text-2xl text-text-hi">Przerwa techniczna</h1>
          <p className="mx-auto max-w-md text-sm text-text-lo">
            Trwają prace techniczne lub chwilowo nie ma połączenia z serwerem.
            Połączymy Cię ponownie automatycznie, gdy tylko wróci dostępność.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
