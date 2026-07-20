import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

type Tone = 'neutral' | 'gold' | 'blue' | 'red' | 'win' | 'loss' | 'pending' | 'info';

const TONES: Record<Tone, string> = {
  neutral: 'bg-bg-2 text-text border border-line',
  gold: 'bg-[var(--gold)]/15 text-[var(--gold-soft)] border border-[var(--gold)]/40',
  blue: 'bg-[var(--blue-bg)] text-[var(--blue)] border border-[var(--blue)]/40',
  red: 'bg-[var(--red-bg)] text-[var(--red)] border border-[var(--red)]/40',
  win: 'bg-[var(--win)]/15 text-[var(--win)] border border-[var(--win)]/40',
  loss: 'bg-[var(--loss)]/15 text-[var(--loss)] border border-[var(--loss)]/40',
  pending: 'bg-[var(--pending)]/15 text-[var(--pending)] border border-[var(--pending)]/40',
  info: 'bg-[var(--info)]/15 text-[var(--info)] border border-[var(--info)]/40',
};

export interface BadgeProps {
  tone?: Tone;
  children: ReactNode;
  className?: string;
}

export function Badge({ tone = 'neutral', children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-sm px-2 py-0.5 text-xs font-medium',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
