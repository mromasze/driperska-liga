import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';
import type { HighlightVideo } from '../types';

const HIGHLIGHTS_KEY = ['highlights'] as const;

export function useHighlights() {
  return useQuery({
    queryKey: HIGHLIGHTS_KEY,
    queryFn: () => api.get<HighlightVideo[]>('/highlights'),
    staleTime: 60_000,
  });
}

export function useUploadHighlight() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.upload<HighlightVideo>('/highlights', form);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: HIGHLIGHTS_KEY }),
  });
}

export function useDeleteHighlight() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/highlights/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: HIGHLIGHTS_KEY }),
  });
}
