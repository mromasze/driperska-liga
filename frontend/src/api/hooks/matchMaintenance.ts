import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import { queryKeys } from '../queryKeys';
import type { AffectedResponse, MatchMaintenanceSummary } from '../types';

/**
 * Admin housekeeping for the match list (ADMIN only).
 *
 * Everything here is destructive, so each hook invalidates both the maintenance counts and every
 * match listing — the numbers on the buttons must never outlive the rows they describe.
 */

/** GET /admin/matches/maintenance — how much each button would affect. */
export function useMatchMaintenance() {
  return useQuery({
    queryKey: queryKeys.matchMaintenance,
    queryFn: () => api.get<MatchMaintenanceSummary>('/admin/matches/maintenance'),
  });
}

function useMaintenanceAction(path: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<AffectedResponse>(`/admin/matches/maintenance/${path}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.matchMaintenance });
      void queryClient.invalidateQueries({ queryKey: ['matches'] });
    },
  });
}

/** Cancels every match still in flight; the rows stay as CANCELLED. */
export const useStopAllMatches = () => useMaintenanceAction('stop-all');

/** Deletes every match whose champion draft had already started. Irreversible. */
export const useDeleteDraftsInProgress = () => useMaintenanceAction('delete-drafts-in-progress');

/** Deletes every match that is not APPROVED. Irreversible. */
export const usePurgeUnapprovedMatches = () => useMaintenanceAction('purge-unapproved');

/** DELETE /admin/matches/{id} — removes one match outright. Irreversible. */
export function useDeleteMatch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/admin/matches/${id}`),
    onSuccess: (_result, id) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.matchMaintenance });
      void queryClient.invalidateQueries({ queryKey: ['matches'] });
      queryClient.removeQueries({ queryKey: queryKeys.match(id) });
    },
  });
}
