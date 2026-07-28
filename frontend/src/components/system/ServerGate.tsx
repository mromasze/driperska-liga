import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { PublicConfig } from '../../api/types';
import { useServerStatus } from '../../store/serverStatus';
import { useAuthStore } from '../../store/auth';
import { restoreSession } from '../../lib/session';
import { Spinner } from '../ui/States';

/** Persisted so a full page reload during an outage can still detect a restart on recovery. */
const BOOT_KEY = 'driperska-boot';

/** How long the "connection lost" stage shows before it turns into the maintenance notice. */
const DISCONNECT_NOTICE_MS = 5_000;

/**
 * Polls the public config endpoint to track backend availability.
 *
 * Losing the backend ends the session: tokens are boot-scoped server-side, so one that spans an
 * outage cannot be trusted anyway. The overlay runs in two stages — first "connection lost, signing
 * out", then the technical-break notice — while retrying in the background.
 *
 * On recovery with a different bootId (the backend restarted) the old tokens are dead. If the user
 * ticked "remember me" the session is re-established silently from the stored credentials; otherwise
 * they land back on the login screen.
 */
export function ServerGate({ children }: { children: ReactNode }) {
  const reachable = useServerStatus((s) => s.reachable);
  const unreachableSince = useServerStatus((s) => s.unreachableSince);
  const markReachable = useServerStatus((s) => s.markReachable);
  const markUnreachable = useServerStatus((s) => s.markUnreachable);
  const [stage, setStage] = useState<'lost' | 'maintenance'>('lost');

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
    localStorage.setItem(BOOT_KEY, bootId);
    if (previous && previous !== bootId) {
      // Backend restarted: tokens minted before the restart are rejected server-side.
      const { remember, clear } = useAuthStore.getState();
      if (remember) void restoreSession();
      else clear();
    }
  }, [query.isSuccess, bootId, markReachable]);

  useEffect(() => {
    if (query.isError) markUnreachable();
  }, [query.isError, query.errorUpdatedAt, markUnreachable]);

  // Sign out the moment the backend is gone, keeping whatever was remembered so the session can come
  // back on its own once the server does.
  useEffect(() => {
    if (!reachable) useAuthStore.getState().clear();
  }, [reachable]);

  // Advance from "connection lost" to the maintenance notice after a few seconds.
  useEffect(() => {
    if (reachable || !unreachableSince) {
      setStage('lost');
      return;
    }
    const elapsed = Date.now() - unreachableSince;
    if (elapsed >= DISCONNECT_NOTICE_MS) {
      setStage('maintenance');
      return;
    }
    const id = window.setTimeout(() => setStage('maintenance'), DISCONNECT_NOTICE_MS - elapsed);
    return () => window.clearTimeout(id);
  }, [reachable, unreachableSince]);

  if (!reachable) {
    return stage === 'lost' ? <ConnectionLostScreen /> : <MaintenanceScreen />;
  }

  return <>{children}</>;
}

function Overlay({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <div
      role="alertdialog"
      aria-live="assertive"
      className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-5 bg-[var(--bg)] px-6 text-center"
    >
      {icon}
      <div className="space-y-2">
        <h1 className="font-display text-2xl text-text-hi">{title}</h1>
        <div className="mx-auto max-w-md text-sm text-text-lo">{children}</div>
      </div>
    </div>
  );
}

function ConnectionLostScreen() {
  return (
    <Overlay icon={<div className="text-4xl">📡</div>} title="Utracono połączenie z serwerem">
      <p>Zamykamy sesję i wylogowujemy Cię ze względów bezpieczeństwa.</p>
      <p className="mt-2">Próbujemy połączyć się ponownie…</p>
    </Overlay>
  );
}

function MaintenanceScreen() {
  const remember = useAuthStore((s) => s.remember);
  return (
    <Overlay icon={<Spinner className="h-8 w-8" />} title="Przerwa techniczna">
      <p>
        Trwają prace techniczne lub chwilowo nie ma połączenia z serwerem.
        Połączymy Cię ponownie automatycznie, gdy tylko wróci dostępność.
      </p>
      <p className="mt-2">
        {remember
          ? 'Twoja sesja zostanie przywrócona samoczynnie (zapamiętane logowanie).'
          : 'Po powrocie serwera trzeba będzie zalogować się ponownie.'}
      </p>
    </Overlay>
  );
}
