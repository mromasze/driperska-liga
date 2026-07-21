import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

type Tone = 'default' | 'win' | 'loss' | 'gold' | 'blue' | 'red' | 'pending' | 'info';

const COLOR: Record<Tone, string | null> = {
  default: null,
  win: 'var(--win)',
  loss: 'var(--loss)',
  gold: 'var(--gold)',
  blue: 'var(--blue)',
  red: 'var(--red)',
  pending: 'var(--pending)',
  info: 'var(--info)',
};

export function Badge({
  children,
  tone = 'default',
  className,
}: {
  children: ReactNode;
  tone?: Tone;
  className?: string;
}) {
  const color = COLOR[tone];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-semibold',
        className,
      )}
      style={
        color
          ? {
              color,
              borderColor: `color-mix(in srgb, ${color} 45%, transparent)`,
              background: `color-mix(in srgb, ${color} 12%, transparent)`,
            }
          : undefined
      }
    >
      {children}
    </span>
  );
}
