/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        accent: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
        },
      },
      fontFamily: {
        sans: [
          'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Noto Sans', 'Ubuntu', 'Cantarell',
          'Helvetica Neue', 'Arial', 'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Consolas', 'Menlo', 'monospace'],
      },
      keyframes: {
        slidein: { from: { transform: 'translateX(100%)' }, to: { transform: 'translateX(0)' } },
        fadein: { from: { opacity: '0' }, to: { opacity: '1' } },
        toastin: { from: { transform: 'translateY(8px)', opacity: '0' }, to: { transform: 'translateY(0)', opacity: '1' } },
      },
      animation: {
        slidein: 'slidein 180ms ease-out',
        fadein: 'fadein 150ms ease-out',
        toastin: 'toastin 200ms ease-out',
      },
    },
  },
  plugins: [],
};
