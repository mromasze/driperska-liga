import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../client';
import type { ServiceHealth } from '../types';

export type DiagService = 'ollama' | 'discord' | 'riot';

/** Runs a connectivity check on demand (button-triggered). */
export function useCheckService() {
  return useMutation({
    mutationFn: (service: DiagService) => api.get<ServiceHealth>(`/admin/diagnostics/${service}`),
  });
}

/**
 * Ollama reachability, checked on mount. Never cached: the whole point of the AI panel is that the
 * key, host and model can change between two visits to the page.
 */
export function useOllamaHealth() {
  return useQuery({
    queryKey: ['admin', 'diagnostics', 'ollama'] as const,
    queryFn: () => api.get<ServiceHealth>('/admin/diagnostics/ollama'),
    staleTime: 0,
    retry: false,
  });
}
