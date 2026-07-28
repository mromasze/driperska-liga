import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { Account, AuthTokens, ChangePasswordRequest, LoginRequest, PublicConfig } from '../types';
import { useAuthStore } from '../../store/auth';

/** GET /config — public runtime config (Turnstile site key etc.). */
export function usePublicConfig() {
  return useQuery({
    queryKey: ['public-config'],
    queryFn: () => api.get<PublicConfig>('/config', { skipAuth: true }),
    staleTime: Infinity,
  });
}

/** POST /auth/change-password for the logged-in account. */
export function useChangePassword() {
  return useMutation({
    mutationFn: (body: ChangePasswordRequest) => api.post<void>('/auth/change-password', body),
  });
}

/** POST /auth/login → stores tokens + account in the auth store. */
export function useLogin() {
  const setAuth = useAuthStore((s) => s.setAuth);
  return useMutation({
    mutationFn: (body: LoginRequest) =>
      api.post<AuthTokens>('/auth/login', body, { skipAuth: true }),
    onSuccess: (data) => setAuth(data),
  });
}

/**
 * POST /auth/logout → clears local auth state regardless of outcome. This is the explicit sign-out,
 * so it also drops any remembered credentials — otherwise the next health poll would log the user
 * straight back in.
 */
export function useLogout() {
  const logout = useAuthStore((s) => s.logout);
  return useMutation({
    mutationFn: () => api.post<void>('/auth/logout'),
    onSettled: () => logout(),
  });
}

/** GET /auth/me — current account + role. Enabled only when authenticated. */
export function useMe() {
  const accessToken = useAuthStore((s) => s.accessToken);
  const setAccount = useAuthStore((s) => s.setAccount);
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: async () => {
      const account = await api.get<Account>('/auth/me');
      setAccount(account);
      return account;
    },
    enabled: Boolean(accessToken),
  });
}
