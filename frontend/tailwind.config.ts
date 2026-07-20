import type { Config } from 'tailwindcss';

// Tailwind consumes the Hextech Arena theme tokens defined in src/index.css.
// Components only ever reference these semantic names, never raw hex values,
// so a future light theme can remap the CSS variables without touching markup.
const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          0: 'var(--bg-0)',
          1: 'var(--bg-1)',
          2: 'var(--bg-2)',
        },
        line: 'var(--border)',
        text: {
          hi: 'var(--text-hi)',
          DEFAULT: 'var(--text)',
          lo: 'var(--text-lo)',
        },
        gold: {
          DEFAULT: 'var(--gold)',
          soft: 'var(--gold-soft)',
        },
        blue: 'var(--blue)',
        'blue-bg': 'var(--blue-bg)',
        red: 'var(--red)',
        'red-bg': 'var(--red-bg)',
        win: 'var(--win)',
        loss: 'var(--loss)',
        pending: 'var(--pending)',
        info: 'var(--info)',
        pr: {
          low: 'var(--pr-low)',
          mid: 'var(--pr-mid)',
          high: 'var(--pr-high)',
          elite: 'var(--pr-elite)',
        },
      },
      fontFamily: {
        display: ['Marcellus', 'Georgia', 'serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
        numbers: ['Rajdhani', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        sm: 'var(--r-sm)',
        md: 'var(--r-md)',
        lg: 'var(--r-lg)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        'glow-gold': 'var(--glow-gold)',
      },
    },
  },
  plugins: [],
};

export default config;
