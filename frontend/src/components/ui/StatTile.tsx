import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface StatTileProps {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  accent?: 'default' | 'gold' | 'cyan' | 'violet' | 'win';
  className?: string;
}

const ACCENT: Record<NonNullable<StatTileProps['accent']>, string> = {
  default: 'text-text-hi',
  gold: 'text-gradient-gold',
  cyan: 'text-cyan',
  violet: 'text-violet',
  win: 'text-win',
};

export function StatTile({ label, value, sub, accent = 'default', className }: StatTileProps) {
  return (
    <div className={cn('glass p-4 sm:p-5', className)}>
      <div className="kicker">{label}</div>
      <div className={cn('num mt-1.5 text-2xl font-bold sm:text-3xl', ACCENT[accent])}>{value}</div>
      {sub && <div className="mt-1 text-xs text-text-lo">{sub}</div>}
    </div>
  );
}
