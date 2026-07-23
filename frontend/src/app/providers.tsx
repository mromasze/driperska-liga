import { useState } from 'react';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from '../api/client';

export function AppProviders({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
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
