import { cn } from '../../lib/cn';

export interface SparklineProps {
  /** Series of values, oldest → newest. */
  data: number[];
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

/** Tiny inline form/trend sparkline (docs/06 ranking form column). */
export function Sparkline({
  data,
  width = 72,
  height = 24,
  color = 'var(--info)',
  className,
}: SparklineProps) {
  if (data.length < 2) {
    return <span className={cn('text-xs text-text-lo', className)}>—</span>;
  }

  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const stepX = width / (data.length - 1);

  const points = data
    .map((value, i) => {
      const x = i * stepX;
      const y = height - ((value - min) / range) * height;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');

  return (
    <svg
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      className={className}
      role="img"
      aria-label="Forma z ostatnich meczów"
      preserveAspectRatio="none"
    >
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
