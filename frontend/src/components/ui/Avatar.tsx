import { cn } from '../../lib/cn';

export interface AvatarProps {
  src?: string | null;
  name: string;
  size?: number; // px
  className?: string;
  ring?: boolean; // gold ring accent
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const second = parts.length > 1 ? (parts[parts.length - 1][0] ?? '') : '';
  return (first + second).toUpperCase() || '?';
}

export function Avatar({ src, name, size = 40, className, ring = false }: AvatarProps) {
  const dimension = { width: size, height: size };
  return (
    <div
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-bg-2 text-text-hi',
        ring && 'ring-2 ring-[var(--gold)] ring-offset-2 ring-offset-[var(--bg-0)]',
        className,
      )}
      style={dimension}
    >
      {src ? (
        <img
          src={src}
          alt={name}
          loading="lazy"
          width={size}
          height={size}
          className="h-full w-full object-cover"
        />
      ) : (
        <span className="num font-semibold" style={{ fontSize: size * 0.4 }}>
          {initials(name)}
        </span>
      )}
    </div>
  );
}
