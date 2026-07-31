import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import type { CreatePlannedMatchResult, PlannedMatch, RsvpResponse } from '../types';

const KEY = ['planned-matches'] as const;

/**
 * GET /planned-matches — upcoming terms only; a match whose date has passed can no longer be
 * confirmed, so the backend stops listing it. `includePast` is honoured for ADMIN/EDITOR and lets the
 * schedule page keep the history of what was planned.
 */
export function usePlannedMatches(includePast = false) {
  return useQuery({
    queryKey: [...KEY, { includePast }] as const,
    queryFn: () =>
      api.get<PlannedMatch[]>('/planned-matches', {
        query: includePast ? { includePast: true } : undefined,
      }),
  });
}

export function useCreatePlannedMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { scheduledAt: string; note?: string | null }) =>
      api.post<CreatePlannedMatchResult>('/planned-matches', body),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useRsvpPlannedMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, response }: { id: string; response: RsvpResponse }) =>
      api.post<PlannedMatch>(`/planned-matches/${id}/rsvp`, { response }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useCancelPlannedMatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.post<void>(`/planned-matches/${id}/cancel`),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
