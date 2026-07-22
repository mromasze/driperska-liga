import { cn } from '../../lib/cn';

interface SparklineProps {
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

/** Minimal inline sparkline for recent-form (PR over time). */
export function Sparkline({ data, width = 96, height = 28, color = 'var(--cyan)', className }: SparklineProps) {
  if (!data || data.length < 2) {
    return <span className={cn('text-xs text-text-lo', className)}>—</span>;
  }
  const min = Math.min(...data);
  const max = Math.max(...data);
  const span = max - min || 1;
  const step = width / (data.length - 1);
  const points = data.map((v, i) => {
    const x = i * step;
    const y = height - ((v - min) / span) * (height - 4) - 2;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const area = `0,${height} ${points.join(' ')} ${width},${height}`;
  return (
    <svg width={width} height={height} className={className} aria-hidden>
      <polygon points={area} fill={color} opacity={0.12} />
      <polyline
        points={points.join(' ')}
        fill="none"
        stroke={color}
        strokeWidth={1.75}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
