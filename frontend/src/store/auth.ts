import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Account, AuthTokens } from '../api/types';

/**
 * Auth store.
 *
 * Per docs/06 §6.7 the access token lives in memory and the refresh token in a
 * secure store. Here we keep both in a persisted Zustand slice (localStorage
 * stands in for "secure storage" until the backend issues an httpOnly refresh
 * cookie). The fetch client reads the access token from here and the 401
 * interceptor calls `setAccessToken` / `clear`.
 *
 * Remembered credentials
 * ----------------------
 * Both tokens are scoped to the backend's boot id (see `AuthService.refresh`), so every restart or
 * redeploy invalidates all sessions server-side — no amount of token juggling survives it. To make a
 * session last "until you log out", `remember` stores the login and password so it can be
 * re-established silently instead of dropping the player on the login screen mid-evening.
 *
 * That is a genuine trade-off: a password in localStorage is readable by any script that reaches this
 * origin and by anyone with access to the device profile. It is opt-in, off by default, and wiped by
 * an explicit logout.
 */
interface StoredCredentials {
  username: string;
  password: string;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  account: Account | null;
  /** True when the user asked to stay signed in until they log out explicitly. */
  remember: boolean;
  /** Only populated while `remember` is true. */
  credentials: StoredCredentials | null;
  setAuth: (tokens: AuthTokens) => void;
  setAccessToken: (accessToken: string, refreshToken?: string) => void;
  setAccount: (account: Account) => void;
  rememberCredentials: (credentials: StoredCredentials) => void;
  forgetCredentials: () => void;
  /** Drops the session; remembered credentials survive so it can be restored automatically. */
  clear: () => void;
  /** Explicit sign-out: drops the session *and* anything remembered. */
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      account: null,
      remember: false,
      credentials: null,
      setAuth: (tokens) =>
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          account: tokens.account,
        }),
      setAccessToken: (accessToken, refreshToken) =>
        set((state) => ({
          accessToken,
          refreshToken: refreshToken ?? state.refreshToken,
        })),
      setAccount: (account) => set({ account }),
      rememberCredentials: (credentials) => set({ remember: true, credentials }),
      forgetCredentials: () => set({ remember: false, credentials: null }),
      clear: () => set({ accessToken: null, refreshToken: null, account: null }),
      logout: () =>
        set({
          accessToken: null, refreshToken: null, account: null,
          remember: false, credentials: null,
        }),
    }),
    { name: 'driperska-auth' },
  ),
);

/** Non-reactive selectors for use outside React (e.g. the fetch client). */
export const authSelectors = {
  isAuthenticated: (): boolean => Boolean(useAuthStore.getState().accessToken),
  isAdmin: (): boolean => useAuthStore.getState().account?.role === 'ADMIN',
};
