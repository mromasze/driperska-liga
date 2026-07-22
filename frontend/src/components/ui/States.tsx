import type { ReactNode } from 'react';
import { ApiError } from '../../api/client';
import { cn } from '../../lib/cn';

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="Ładowanie"
      className={cn(
        'inline-block h-5 w-5 animate-spin rounded-full border-2 border-line border-t-[var(--gold)]',
        className,
      )}
    />
  );
}

export function LoadingState({ label = 'Ładowanie…' }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-text-lo">
      <Spinner />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function EmptyState({
  title = 'Brak danych',
  description,
  action,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-line bg-bg-1 py-16 text-center">
      <p className="text-base font-medium text-text-hi">{title}</p>
      {description && <p className="max-w-sm text-sm text-text-lo">{description}</p>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, title = 'Coś poszło nie tak' }: { error: unknown; title?: string }) {
  const message =
    error instanceof ApiError
      ? (error.problem?.detail ?? error.message)
      : error instanceof Error
        ? error.message
        : 'Nieznany błąd. Backend może być niedostępny.';
  return (
    <div className="rounded-lg border border-[var(--loss)]/40 bg-[var(--loss)]/10 px-5 py-8 text-center">
      <p className="text-base font-medium text-loss">{title}</p>
      <p className="mt-1 text-sm text-text-lo">{message}</p>
    </div>
  );
}
