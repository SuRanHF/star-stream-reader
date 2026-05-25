import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  theme: {
    extend: {
      colors: {
        void: '#080b0d',
        panel: '#11171b',
        line: '#27323a',
        star: '#c9a86a',
        spirit: '#98c9bb',
        danger: '#d97b6c',
      },
      fontFamily: {
        serif: ['"Source Han Serif SC"', '"Noto Serif SC"', 'SimSun', 'serif'],
      },
    },
  },
  plugins: [],
} satisfies Config;
