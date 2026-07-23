import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';

export interface AdminSettings { riotEnabled: boolean; }

const SETTINGS_KEY = ['admin', 'settings'] as const;

export function useAdminSettings() {
  return useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: () => api.get<AdminSettings>('/admin/settings'),
  });
}

export function useUpdateAdminSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (settings: AdminSettings) => api.put<AdminSettings>('/admin/settings', settings),
    onSuccess: (data) => queryClient.setQueryData(SETTINGS_KEY, data),
  });
}
