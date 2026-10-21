import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      colors: {
        void: 'var(--color-void)',
        panel: 'var(--color-panel)',
        control: 'var(--color-control)',
        card: 'var(--color-card)',
        log: 'var(--color-log)',
        line: 'var(--color-border)',
        star: 'var(--color-star)',
        'star-bright': 'var(--color-star-bright)',
        spirit: 'var(--color-spirit)',
        danger: 'var(--color-danger)',
        muted: 'var(--color-muted)',
        system: 'var(--color-system)',
        purple: 'var(--color-purple)',
        blue: {
          300: 'var(--color-system-bright)',
          400: 'var(--color-system-bright)',
          500: 'var(--color-system)',
          600: 'var(--color-system)',
        },
      },
      fontFamily: {
        serif: ['"Source Han Serif SC"', '"Noto Serif SC"', 'SimSun', 'serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
