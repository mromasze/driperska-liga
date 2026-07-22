import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Solid panel instead of frosted glass. */
  solid?: boolean;
  /** Add hover-lift interaction. */
  interactive?: boolean;
}

export function Card({ solid, interactive, className, children, ...rest }: CardProps) {
  return (
    <div
      className={cn(solid ? 'panel' : 'glass', interactive && 'lift cursor-pointer', className)}
      {...rest}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  title,
  kicker,
  action,
  className,
}: {
  title: ReactNode;
  kicker?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex items-end justify-between gap-4', className)}>
      <div>
        {kicker && <div className="kicker mb-1">{kicker}</div>}
        <h2 className="text-xl">{title}</h2>
      </div>
      {action}
    </div>
  );
}
