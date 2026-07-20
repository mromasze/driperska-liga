import { cn } from '../../lib/cn';

export interface ChampionIconProps {
  src?: string | null;
  name: string;
  size?: number; // px
  className?: string;
}

/** Square champion icon (docs/07 §7.3) — lazy-loaded, no layout shift. */
export function ChampionIcon({ src, name, size = 32, className }: ChampionIconProps) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 overflow-hidden rounded-sm border border-line bg-bg-2',
        className,
      )}
      style={{ width: size, height: size }}
    >
      {src ? (
        <img
          src={src}
          alt={name}
          title={name}
          loading="lazy"
          width={size}
          height={size}
          className="h-full w-full object-cover"
        />
      ) : (
        <span
          aria-hidden="true"
          className="num flex h-full w-full items-center justify-center text-text-lo"
          style={{ fontSize: size * 0.4 }}
        >
          {name.slice(0, 2)}
        </span>
      )}
    </span>
  );
}
