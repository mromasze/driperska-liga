import { cn } from '../../lib/cn';

interface AvatarProps {
  src?: string | null;
  name: string;
  size?: number;
  ring?: boolean;
  className?: string;
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function Avatar({ src, name, size = 40, ring, className }: AvatarProps) {
  const style = { width: size, height: size } as const;
  return (
    <span
      className={cn(
        'relative inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-bg-2',
        ring && 'ring-2 ring-[color:var(--gold)]/60 ring-offset-2 ring-offset-[var(--bg-1)]',
        className,
      )}
      style={style}
    >
      {src ? (
        <img src={src} alt={name} className="h-full w-full object-cover" loading="lazy" />
      ) : (
        <span
          className="num font-semibold text-text-lo"
          style={{ fontSize: Math.max(11, size * 0.36) }}
        >
          {initials(name)}
        </span>
      )}
    </span>
  );
}
