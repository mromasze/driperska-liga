import type { CSSProperties, ReactNode } from 'react';
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

/**
 * Shimmering placeholder block. Prefer these over a bare spinner wherever the final shape is known:
 * the layout stops jumping when data lands, and a slow section no longer blanks the whole page.
 */
export function Skeleton({ className, style }: { className?: string; style?: CSSProperties }) {
  return <div aria-hidden style={style} className={cn('skeleton', className)} />;
}

/** Placeholder for a single panel/card: a heading plus a few text lines. */
export function CardSkeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div role="status" aria-label="Ładowanie" className={cn('panel space-y-3 p-5 sm:p-7', className)}>
      <Skeleton className="h-3 w-24" />
      <Skeleton className="h-7 w-1/2" />
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} className="h-4" style={{ width: `${90 - i * 12}%` }} />
      ))}
    </div>
  );
}

/** Placeholder for a titled list section (schedule, match list, approvals queue…). */
export function SectionSkeleton({ title, rows = 3 }: { title?: string; rows?: number }) {
  return (
    <section role="status" aria-label="Ładowanie" className="space-y-3">
      {title ? (
        <h2 className="font-display text-xl text-text-lo">{title}</h2>
      ) : (
        <Skeleton className="h-6 w-40" />
      )}
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-16 rounded-lg" />
      ))}
    </section>
  );
}

/** Placeholder for a responsive grid of cards (match cards, player cards…). */
export function CardGridSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div role="status" aria-label="Ładowanie" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} className="h-44 rounded-lg" />
      ))}
    </div>
  );
}

/** Placeholder rows for a table body, so headers stay put while data loads. */
export function TableSkeleton({ rows = 6, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div role="status" aria-label="Ładowanie" className="space-y-2">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex items-center gap-3">
          {Array.from({ length: columns }).map((_, c) => (
            <Skeleton key={c} className={cn('h-9 flex-1', c === 0 && 'max-w-10 shrink-0')} />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Placeholder grid of stat tiles. */
export function TilesSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div role="status" aria-label="Ładowanie" className="grid grid-cols-2 gap-4 sm:grid-cols-3">
      {Array.from({ length: count }).map((_, i) => (
        <Skeleton key={i} className="h-24 rounded-lg" />
      ))}
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
