import { cn } from '../../lib/cn';
import { prColor, prTier } from '../../lib/pr';

export interface PrBadgeProps {
  /** Performance Rating 0–100. */
  value: number | null | undefined;
  size?: 'sm' | 'md';
  className?: string;
}

const TIER_LABEL = {
  low: 'niski',
  mid: 'solidny',
  high: 'wysoki',
  elite: 'elitarny',
} as const;

/**
 * PR value coloured by the --pr-* scale (docs/06 §6.6). The number itself
 * carries the meaning — colour is never the only signal (docs/06 §6.8).
 */
export function PrBadge({ value, size = 'md', className }: PrBadgeProps) {
  if (value == null) {
    return <span className={cn('num text-text-lo', className)}>—</span>;
  }
  const color = prColor(value);
  const tier = prTier(value);
  return (
    <span
      className={cn(
        'num inline-flex items-center justify-center rounded-sm font-bold tabular-nums',
        size === 'sm' ? 'min-w-[2rem] px-1.5 py-0.5 text-xs' : 'min-w-[2.5rem] px-2 py-0.5 text-sm',
        className,
      )}
      style={{ color, backgroundColor: `${color}22`, border: `1px solid ${color}55` }}
      title={`PR ${Math.round(value)} — ${TIER_LABEL[tier]}`}
    >
      {Math.round(value)}
    </span>
  );
}
