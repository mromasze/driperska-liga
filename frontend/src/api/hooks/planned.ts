import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import type { CreatePlannedMatchResult, PlannedMatch, RsvpResponse } from '../types';

const KEY = ['planned-matches'] as const;

export function usePlannedMatches() {
  return useQuery({ queryKey: KEY, queryFn: () => api.get<PlannedMatch[]>('/planned-matches') });
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
