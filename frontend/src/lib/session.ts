import { api } from '../api/client';
import type { AuthTokens } from '../api/types';
import { useAuthStore } from '../store/auth';

/**
 * Re-establishes a session from remembered credentials.
 *
 * Needed because the backend scopes every token to its boot id: after a restart or redeploy the old
 * tokens are rejected, and without this the player is bounced to the login screen even though they
 * asked to stay signed in. Returns false when nothing is remembered or the login is refused (wrong
 * password, disabled account, or a Turnstile challenge that only a human can pass) — the caller then
 * falls back to the normal login screen.
 */
export async function restoreSession(): Promise<boolean> {
  const { remember, credentials } = useAuthStore.getState();
  if (!remember || !credentials) return false;
  try {
    const tokens = await api.post<AuthTokens>(
      '/auth/login',
      { username: credentials.username, password: credentials.password },
      { skipAuth: true },
    );
    useAuthStore.getState().setAuth(tokens);
    return true;
  } catch {
    // Stale or now-invalid credentials: stop retrying them on every reconnect.
    useAuthStore.getState().forgetCredentials();
    return false;
  }
}
