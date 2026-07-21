import { cn } from '../../lib/cn';

const MEDALS: Record<number, { bg: string; fg: string; glow: string }> = {
  1: { bg: 'linear-gradient(135deg,#ffe9a8,#f2c14e 55%,#b6851f)', fg: '#2a1c02', glow: 'rgba(242,193,78,0.55)' },
  2: { bg: 'linear-gradient(135deg,#eef2f8,#c3ccdb 55%,#8b96ab)', fg: '#20262f', glow: 'rgba(195,204,219,0.4)' },
  3: { bg: 'linear-gradient(135deg,#f0c39a,#cd7f4b 55%,#9a5a2f)', fg: '#2a1608', glow: 'rgba(205,127,75,0.4)' },
};

/** Medal for ranks 1–3, plain number otherwise. */
export function RankMedal({ rank, className }: { rank: number; className?: string }) {
  const medal = MEDALS[rank];
  if (!medal) {
    return (
      <span className={cn('num inline-flex w-8 justify-center text-base font-semibold text-text-lo', className)}>
        {rank}
      </span>
    );
  }
  return (
    <span
      className={cn('num inline-flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold', className)}
      style={{ background: medal.bg, color: medal.fg, boxShadow: `0 0 18px -3px ${medal.glow}` }}
    >
      {rank}
    </span>
  );
}
