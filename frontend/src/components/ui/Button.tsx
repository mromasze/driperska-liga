import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/cn';

type Variant = 'primary' | 'gold' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  leftIcon?: ReactNode;
}

const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-[var(--blue)] text-white hover:brightness-110 shadow-[0_8px_24px_-8px_rgba(76,130,245,0.7)]',
  gold: 'bg-gradient-to-b from-gold-soft to-gold text-[#1a1205] font-semibold hover:brightness-105 shadow-glow-gold',
  ghost: 'bg-[var(--glass)] text-text hover:text-text-hi hover:bg-[var(--glass-strong)] border border-line',
  danger: 'bg-[var(--red)] text-white hover:brightness-110',
};

const SIZES: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-6 text-base',
};

export function Button({
  variant = 'primary',
  size = 'md',
  leftIcon,
  className,
  children,
  disabled,
  ...rest
}: ButtonProps) {
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-md font-medium tracking-wide',
        'transition-all duration-150 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-45 disabled:active:scale-100',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      disabled={disabled}
      {...rest}
    >
      {leftIcon}
      {children}
    </button>
  );
}
