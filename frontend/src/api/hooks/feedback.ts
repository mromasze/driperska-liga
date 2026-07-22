import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import type { MyFeedback, RateableMatch } from '../types';

const KEY = ['rateable-matches'] as const;

export function useRateableMatches() {
  return useQuery({ queryKey: KEY, queryFn: () => api.get<RateableMatch[]>('/matches/rateable') });
}

export function useSubmitFeedback() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ matchId, upvotePlayerId, downvotePlayerId, note }:
      { matchId: string; upvotePlayerId: string | null; downvotePlayerId: string | null; note: string | null }) =>
      api.post<MyFeedback>(`/matches/${matchId}/feedback`, { upvotePlayerId, downvotePlayerId, note }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
