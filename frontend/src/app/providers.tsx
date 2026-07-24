import { useState } from 'react';
import type { ReactNode } from 'react';
import { QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from '../api/client';
import { useServerStatus } from '../store/serverStatus';

/**
 * Distinguishes a genuine backend outage from an ordinary HTTP error. Behind nginx a redeploy
 * surfaces as 502/503/504 (gateway can't reach the backend); a fully unreachable host makes fetch
 * throw a raw TypeError. A plain 500 means the backend answered (a bug, not an outage), so it is
 * left to the page's own ErrorState rather than blanking the whole app.
 */
function isBackendOutage(error: unknown): boolean {
  if (error instanceof ApiError) return [502, 503, 504].includes(error.status);
  return error instanceof Error; // raw fetch TypeError, e.g. "Failed to fetch"
}

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        // Surface backend outages immediately (without waiting for the health poll) so the
        // "technical break" overlay can appear the moment any query hits a dead backend.
        queryCache: new QueryCache({
          onError: (error) => {
            if (isBackendOutage(error)) {
              useServerStatus.getState().markUnreachable();
            }
          },
        }),
        defaultOptions: {
          queries: {
            // No client-side staleness: always refetch on mount/focus so players never need Ctrl+F5.
            staleTime: 0,
            refetchOnMount: 'always',
            refetchOnWindowFocus: true,
            retry: (failureCount, error) => {
              // Don't retry auth/permission/not-found errors.
              if (error instanceof ApiError && [401, 403, 404].includes(error.status)) {
                return false;
              }
              return failureCount < 2;
            },
          },
        },
      }),
  );

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
