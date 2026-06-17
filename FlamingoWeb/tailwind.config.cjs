const plugin = require('tailwindcss/plugin');

/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}', './shared/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border) / <alpha-value>)',
        input: 'hsl(var(--input) / <alpha-value>)',
        ring: 'hsl(var(--ring) / <alpha-value>)',
        background: 'hsl(var(--background) / <alpha-value>)',
        foreground: 'hsl(var(--foreground) / <alpha-value>)',
        primary: {
          DEFAULT: 'hsl(var(--primary) / <alpha-value>)',
          foreground: 'hsl(var(--primary-foreground) / <alpha-value>)',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary) / <alpha-value>)',
          foreground: 'hsl(var(--secondary-foreground) / <alpha-value>)',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive) / <alpha-value>)',
          foreground: 'hsl(var(--destructive-foreground) / <alpha-value>)',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted) / <alpha-value>)',
          foreground: 'hsl(var(--muted-foreground) / <alpha-value>)',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent) / <alpha-value>)',
          foreground: 'hsl(var(--accent-foreground) / <alpha-value>)',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover) / <alpha-value>)',
          foreground: 'hsl(var(--popover-foreground) / <alpha-value>)',
        },
        card: {
          DEFAULT: 'hsl(var(--card) / <alpha-value>)',
          foreground: 'hsl(var(--card-foreground) / <alpha-value>)',
        },
        sidebar: {
          DEFAULT: 'hsl(var(--sidebar) / <alpha-value>)',
          foreground: 'hsl(var(--sidebar-foreground) / <alpha-value>)',
          primary: 'hsl(var(--sidebar-primary) / <alpha-value>)',
          'primary-foreground': 'hsl(var(--sidebar-primary-foreground) / <alpha-value>)',
          accent: 'hsl(var(--sidebar-accent) / <alpha-value>)',
          'accent-foreground': 'hsl(var(--sidebar-accent-foreground) / <alpha-value>)',
          border: 'hsl(var(--sidebar-border) / <alpha-value>)',
          ring: 'hsl(var(--sidebar-ring) / <alpha-value>)',
        },
        chart: {
          1: 'hsl(var(--chart-1) / <alpha-value>)',
          2: 'hsl(var(--chart-2) / <alpha-value>)',
          3: 'hsl(var(--chart-3) / <alpha-value>)',
          4: 'hsl(var(--chart-4) / <alpha-value>)',
          5: 'hsl(var(--chart-5) / <alpha-value>)',
        },
        flamingo: '#FF7A85',
        navy: '#1A365D',
        gold: '#FCD34D',
        sand: '#FAFAF9',
        mist: '#8FA3B5',
      },
      borderRadius: {
        lg: 'var(--radius-lg)',
        md: 'var(--radius-md)',
        sm: 'var(--radius-sm)',
        '4xl': '2rem',
      },
      fontFamily: {
        sans: ['Geist Variable', 'sans-serif'],
        heading: ['Geist Variable', 'sans-serif'],
      },
      ringWidth: {
        3: '3px',
      },
      backdropBlur: {
        xs: '2px',
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' },
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' },
        },
        enter: {
          from: {
            opacity: 'var(--tw-enter-opacity, 1)',
            transform:
              'translate3d(var(--tw-enter-translate-x, 0), var(--tw-enter-translate-y, 0), 0) scale3d(var(--tw-enter-scale, 1), var(--tw-enter-scale, 1), var(--tw-enter-scale, 1))',
          },
          to: {
            opacity: '1',
            transform: 'translate3d(0, 0, 0) scale3d(1, 1, 1)',
          },
        },
        exit: {
          from: {
            opacity: '1',
            transform: 'translate3d(0, 0, 0) scale3d(1, 1, 1)',
          },
          to: {
            opacity: 'var(--tw-exit-opacity, 1)',
            transform:
              'translate3d(var(--tw-exit-translate-x, 0), var(--tw-exit-translate-y, 0), 0) scale3d(var(--tw-exit-scale, 1), var(--tw-exit-scale, 1), var(--tw-exit-scale, 1))',
          },
        },
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
        enter: 'enter 150ms cubic-bezier(0.16, 1, 0.3, 1) both',
        exit: 'exit 150ms cubic-bezier(0.16, 1, 0.3, 1) both',
      },
    },
  },
  plugins: [
    plugin(function ({ addVariant, addUtilities }) {
      const dataVariants = {
        'data-open': '&[data-open]',
        'data-closed': '&[data-closed]',
        'data-disabled': '&[data-disabled]',
        'data-placeholder': '&[data-placeholder]',
        'data-checked': '&[data-checked]',
        'data-unchecked': '&[data-unchecked]',
        'data-active': '&[data-active]',
        'data-starting-style': '&[data-starting-style]',
        'data-ending-style': '&[data-ending-style]',
      };

      for (const [name, selector] of Object.entries(dataVariants)) {
        addVariant(name, selector);
      }

      addVariant('supports-backdrop-filter', '@supports (backdrop-filter: blur(0))');

      addUtilities({
        '.outline-hidden': {
          outline: '2px solid transparent',
          'outline-offset': '2px',
        },
        '.text-balance': {
          'text-wrap': 'balance',
        },
        '.text-pretty': {
          'text-wrap': 'pretty',
        },
        '.field-sizing-content': {
          'field-sizing': 'content',
        },
        '.animate-in': {
          animation: 'enter 150ms cubic-bezier(0.16, 1, 0.3, 1) both',
        },
        '.animate-out': {
          animation: 'exit 150ms cubic-bezier(0.16, 1, 0.3, 1) both',
        },
        '.fade-in-0': {
          '--tw-enter-opacity': '0',
        },
        '.fade-out-0': {
          '--tw-exit-opacity': '0',
        },
        '.zoom-in-95': {
          '--tw-enter-scale': '0.95',
        },
        '.zoom-out-95': {
          '--tw-exit-scale': '0.95',
        },
        '.slide-in-from-top-2': {
          '--tw-enter-translate-y': '-0.5rem',
        },
        '.slide-in-from-bottom-2': {
          '--tw-enter-translate-y': '0.5rem',
        },
        '.slide-in-from-left-2': {
          '--tw-enter-translate-x': '-0.5rem',
        },
        '.slide-in-from-right-2': {
          '--tw-enter-translate-x': '0.5rem',
        },
      });
    }),
  ],
};
