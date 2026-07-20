import { forwardRef } from 'react';
import type { ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'gold';
type Size = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-[var(--blue)] text-white hover:brightness-110 disabled:opacity-50',
  secondary:
    'bg-bg-2 text-text-hi border border-line hover:border-[var(--gold)]/50 disabled:opacity-50',
  ghost: 'bg-transparent text-text hover:bg-bg-2 disabled:opacity-40',
  danger: 'bg-[var(--red)] text-white hover:brightness-110 disabled:opacity-50',
  gold: 'bg-[var(--gold)] text-[var(--bg-0)] font-semibold hover:bg-[var(--gold-soft)] shadow-glow-gold disabled:opacity-50 disabled:shadow-none',
};

const SIZES: Record<Size, string> = {
  sm: 'h-8 px-3 text-sm rounded-sm',
  md: 'h-10 px-4 text-sm rounded-md',
  lg: 'h-12 px-6 text-base rounded-md',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', className, type, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type ?? 'button'}
      className={cn(
        'inline-flex items-center justify-center gap-2 font-medium transition',
        'disabled:cursor-not-allowed',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    />
  );
});
