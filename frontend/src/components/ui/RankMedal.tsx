import { cn } from '../../lib/cn';

export interface RankMedalProps {
  rank: number;
  className?: string;
}

const MEDAL: Record<number, { bg: string; ring: string; label: string }> = {
  1: { bg: '#C8A24B', ring: 'rgba(200,162,75,.5)', label: 'Złoty medal' },
  2: { bg: '#C0C6D0', ring: 'rgba(192,198,208,.45)', label: 'Srebrny medal' },
  3: { bg: '#B07A46', ring: 'rgba(176,122,70,.45)', label: 'Brązowy medal' },
};

/** Gold/silver/bronze medal for the top 3; plain number otherwise. */
export function RankMedal({ rank, className }: RankMedalProps) {
  const medal = MEDAL[rank];
  if (!medal) {
    return (
      <span className={cn('num inline-block w-8 text-center text-text-lo', className)}>{rank}</span>
    );
  }
  return (
    <span
      aria-label={medal.label}
      title={medal.label}
      className={cn(
        'num inline-flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold text-[var(--bg-0)]',
        className,
      )}
      style={{ background: medal.bg, boxShadow: `0 0 12px ${medal.ring}` }}
    >
      {rank}
    </span>
  );
}
