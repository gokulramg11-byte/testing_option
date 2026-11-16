/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        canvas: '#0B0F19',
        surface: {
          lowest: '#0a0e18',
          low: '#111827',
          DEFAULT: '#161F30',
          high: '#1F2937',
          highest: '#374151'
        },
        border: {
          subtle: '#1F2937',
          DEFAULT: '#374151',
          accent: '#3c4a42'
        },
        emerald: {
          glow: '#10B981',
          DEFAULT: '#10B981',
          bright: '#4edea3',
          dark: '#003824'
        },
        amber: {
          glow: '#F59E0B',
          DEFAULT: '#F59E0B',
          dark: '#472a00'
        },
        crimson: {
          glow: '#EF4444',
          DEFAULT: '#DC2626',
          dark: '#7F1D1D',
          bright: '#FCA5A5'
        },
        cyan: {
          electric: '#38BDF8',
          DEFAULT: '#19AEE8'
        },
        text: {
          primary: '#DFE2F1',
          muted: '#86948A',
          dim: '#6B7280'
        }
      },
      fontFamily: {
        sans: ['Geist', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Consolas', 'monospace']
      },
      boxShadow: {
        'glow-emerald': '0 0 12px rgba(16, 185, 129, 0.35)',
        'glow-amber': '0 0 12px rgba(245, 158, 11, 0.30)',
        'glow-crimson': '0 0 16px rgba(220, 38, 38, 0.40)',
      }
    },
  },
  plugins: [],
}
