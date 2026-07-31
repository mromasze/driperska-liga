import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface StatTileProps {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  accent?: 'default' | 'gold' | 'cyan' | 'violet' | 'win' | 'loss';
  /**
   * `sm` is for a dense row of many numbers (the landing page shows seven), `md` for a handful of
   * headline figures. The big variant used to be the only one, which is why six tiles took up as much
   * height as the results grid below them.
   */
  size?: 'sm' | 'md';
  className?: string;
  title?: string;
}

const ACCENT: Record<NonNullable<StatTileProps['accent']>, string> = {
  default: 'text-text-hi',
  gold: 'text-gradient-gold',
  cyan: 'text-cyan',
  violet: 'text-violet',
  win: 'text-win',
  loss: 'text-loss',
};

export function StatTile({
  label,
  value,
  sub,
  accent = 'default',
  size = 'md',
  className,
  title,
}: StatTileProps) {
  const small = size === 'sm';
  return (
    <div className={cn('glass', small ? 'p-3' : 'p-4 sm:p-5', className)} title={title}>
      <div className={cn('kicker', small && 'text-[0.6rem]')}>{label}</div>
      <div
        className={cn(
          'num font-bold',
          small ? 'mt-1 text-xl' : 'mt-1.5 text-2xl sm:text-3xl',
          ACCENT[accent],
        )}
      >
        {value}
      </div>
      {sub && <div className={cn('text-text-lo', small ? 'mt-0.5 text-[11px]' : 'mt-1 text-xs')}>{sub}</div>}
    </div>
  );
}
