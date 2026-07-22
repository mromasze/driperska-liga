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
 */
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  account: Account | null;
  setAuth: (tokens: AuthTokens) => void;
  setAccessToken: (accessToken: string, refreshToken?: string) => void;
  setAccount: (account: Account) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      account: null,
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
      clear: () => set({ accessToken: null, refreshToken: null, account: null }),
    }),
    { name: 'driperska-auth' },
  ),
);

/** Non-reactive selectors for use outside React (e.g. the fetch client). */
export const authSelectors = {
  isAuthenticated: (): boolean => Boolean(useAuthStore.getState().accessToken),
  isAdmin: (): boolean => useAuthStore.getState().account?.role === 'ADMIN',
};
