import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../client';

export type SettingType = 'STRING' | 'SECRET' | 'BOOLEAN' | 'INTEGER' | 'CHOICE';

export interface SettingView {
  key: string;
  envName: string;
  label: string;
  description: string;
  type: SettingType;
  /** False for values consumed once at startup — shown for reference, changed only in `.env`. */
  editable: boolean;
  restartNote: string | null;
  options: string[];
  secret: boolean;
  /** Current value; for secrets a masked preview (`abc…7890`), never the real one. */
  value: string | null;
  /** Whether anything is set at all — the only truth available for a masked secret. */
  set: boolean;
  /** True when the panel has overridden what `.env` shipped. */
  overridden: boolean;
  defaultValue: string | null;
}

export interface SettingGroup { name: string; settings: SettingView[]; }
export interface RuntimeConfig { groups: SettingGroup[]; }

const CONFIG_KEY = ['admin', 'config'] as const;
const AI_MODELS_KEY = ['admin', 'ai', 'models'] as const;

export function useRuntimeConfig() {
  return useQuery({
    queryKey: CONFIG_KEY,
    queryFn: () => api.get<RuntimeConfig>('/admin/config'),
  });
}

/**
 * Saves a batch of changes. Only send keys the user actually touched: an omitted key keeps its
 * value (which is the only way to leave a secret alone, since the browser never sees it), and an
 * explicit `null` drops the override and restores what `.env` shipped.
 */
export function useUpdateRuntimeConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: Record<string, string | null>) =>
      api.put<RuntimeConfig>('/admin/config', { values }),
    onSuccess: (data) => {
      queryClient.setQueryData(CONFIG_KEY, data);
      void queryClient.invalidateQueries({ queryKey: AI_MODELS_KEY });
    },
  });
}

export function useResetRuntimeConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (keys: string[]) => api.post<RuntimeConfig>('/admin/config/reset', { keys }),
    onSuccess: (data) => {
      queryClient.setQueryData(CONFIG_KEY, data);
      void queryClient.invalidateQueries({ queryKey: AI_MODELS_KEY });
    },
  });
}

// --- AI ------------------------------------------------------------------------------------

export interface AiModels { models: string[]; activeModel: string | null; ok: boolean; message: string | null; }
export interface AiTestResult {
  ok: boolean; model: string; elapsedMillis: number; reply: string | null; message: string;
}

/** Models the configured Ollama account exposes. Not cached — the key/host can change any time. */
export function useAiModels() {
  return useQuery({
    queryKey: AI_MODELS_KEY,
    queryFn: () => api.get<AiModels>('/admin/ai/models'),
    staleTime: 0,
    retry: false,
  });
}

export function useTestAiModel() {
  return useMutation({
    mutationFn: (request: { model?: string; prompt?: string }) =>
      api.post<AiTestResult>('/admin/ai/test', request),
  });
}
