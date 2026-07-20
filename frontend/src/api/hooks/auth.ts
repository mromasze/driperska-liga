import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { Account, AuthTokens, LoginRequest } from '../types';
import { useAuthStore } from '../../store/auth';

/** POST /auth/login → stores tokens + account in the auth store. */
export function useLogin() {
  const setAuth = useAuthStore((s) => s.setAuth);
  return useMutation({
    mutationFn: (body: LoginRequest) =>
      api.post<AuthTokens>('/auth/login', body, { skipAuth: true }),
    onSuccess: (data) => setAuth(data),
  });
}

/** POST /auth/logout → clears local auth state regardless of outcome. */
export function useLogout() {
  const clear = useAuthStore((s) => s.clear);
  return useMutation({
    mutationFn: () => api.post<void>('/auth/logout'),
    onSettled: () => clear(),
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
