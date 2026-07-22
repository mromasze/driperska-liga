import { useMutation } from '@tanstack/react-query';
import { api } from '../client';
import type { ServiceHealth } from '../types';

export type DiagService = 'ollama' | 'discord' | 'riot';

/** Runs a connectivity check on demand (button-triggered). */
export function useCheckService() {
  return useMutation({
    mutationFn: (service: DiagService) => api.get<ServiceHealth>(`/admin/diagnostics/${service}`),
  });
}
