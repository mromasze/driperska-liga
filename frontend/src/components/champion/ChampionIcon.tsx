import { cn } from '../../lib/cn';

interface ChampionIconProps {
  iconUrl?: string | null;
  name?: string | null;
  size?: number;
  className?: string;
}

/** Square champion portrait with a subtle frame. */
export function ChampionIcon({ iconUrl, name, size = 36, className }: ChampionIconProps) {
  return (
    <span
      className={cn(
        'relative inline-block shrink-0 overflow-hidden rounded-md bg-bg-2 ring-1 ring-line',
        className,
      )}
      style={{ width: size, height: size }}
      title={name ?? undefined}
    >
      {iconUrl ? (
        <img
          src={iconUrl}
          alt={name ?? 'champion'}
          width={size}
          height={size}
          loading="lazy"
          className="h-full w-full object-cover"
        />
      ) : (
        <span className="flex h-full w-full items-center justify-center text-[10px] text-text-lo">
          ?
        </span>
      )}
    </span>
  );
}
