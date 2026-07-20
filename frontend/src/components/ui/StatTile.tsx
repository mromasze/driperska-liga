import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface StatTileProps {
  label: string;
  value: ReactNode;
  /** Optional trend/delta line under the value. */
  delta?: string;
  deltaTone?: 'up' | 'down' | 'neutral';
  accent?: string; // CSS colour for the value (e.g. gold for a headline stat)
  className?: string;
}

const DELTA_TONE: Record<NonNullable<StatTileProps['deltaTone']>, string> = {
  up: 'text-win',
  down: 'text-loss',
  neutral: 'text-text-lo',
};

export function StatTile({
  label,
  value,
  delta,
  deltaTone = 'neutral',
  accent,
  className,
}: StatTileProps) {
  return (
    <div className={cn('rounded-md border border-line bg-bg-1 px-4 py-3', className)}>
      <div className="text-xs uppercase tracking-wide text-text-lo">{label}</div>
      <div
        className="num mt-1 text-2xl font-bold leading-none text-text-hi"
        style={accent ? { color: accent } : undefined}
      >
        {value}
      </div>
      {delta && <div className={cn('num mt-1 text-xs', DELTA_TONE[deltaTone])}>{delta}</div>}
    </div>
  );
}
