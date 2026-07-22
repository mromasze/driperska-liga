import { cn } from '../../lib/cn';
import { prColor, prTier } from '../../lib/pr';

interface PrBadgeProps {
  value: number | null | undefined;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

/** Performance Rating chip, coloured by the --pr-* scale. */
export function PrBadge({ value, size = 'md', className }: PrBadgeProps) {
  if (value == null) {
    return <span className={cn('num text-text-lo', className)}>—</span>;
  }
  const color = prColor(value);
  const tier = prTier(value);
  const sizes = {
    sm: 'h-6 min-w-[2.2rem] text-xs',
    md: 'h-7 min-w-[2.6rem] text-sm',
    lg: 'h-9 min-w-[3.2rem] text-base',
  }[size];
  return (
    <span
      className={cn(
        'num inline-flex items-center justify-center rounded-md border px-1.5 font-semibold tabnum',
        sizes,
        className,
      )}
      style={{
        color,
        borderColor: `color-mix(in srgb, ${color} 45%, transparent)`,
        background: `color-mix(in srgb, ${color} 14%, transparent)`,
        boxShadow: tier === 'elite' ? `0 0 16px -4px ${color}` : undefined,
      }}
      title={`Performance Rating: ${value.toFixed(1)}`}
    >
      {Math.round(value)}
    </span>
  );
}
