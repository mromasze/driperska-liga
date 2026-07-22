import type { Config } from 'tailwindcss';

// Tailwind consumes the "Rift Nights" theme tokens defined in src/index.css.
// Components reference these semantic names, never raw hex, so the light theme
// (and any future retheme) works by remapping CSS variables alone.
const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          DEFAULT: 'var(--bg)',
          0: 'var(--bg)',
          1: 'var(--bg-1)',
          2: 'var(--bg-2)',
          3: 'var(--bg-3)',
        },
        line: {
          DEFAULT: 'var(--line)',
          strong: 'var(--line-strong)',
        },
        text: {
          hi: 'var(--text-hi)',
          DEFAULT: 'var(--text)',
          lo: 'var(--text-lo)',
        },
        gold: {
          DEFAULT: 'var(--gold)',
          soft: 'var(--gold-soft)',
        },
        cyan: 'var(--cyan)',
        violet: 'var(--violet)',
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
        display: ['Chakra Petch', 'Inter', 'sans-serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
        numbers: ['Chakra Petch', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        sm: 'var(--r-sm)',
        md: 'var(--r-md)',
        lg: 'var(--r-lg)',
        xl: 'var(--r-xl)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        pop: 'var(--shadow-pop)',
        'glow-gold': 'var(--glow-gold)',
        'glow-cyan': 'var(--glow-cyan)',
      },
      maxWidth: {
        content: '1200px',
      },
    },
  },
  plugins: [],
};

export default config;
