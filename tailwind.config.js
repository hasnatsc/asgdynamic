/** @type {import('tailwindcss').Config} */
module.exports = {
  // app.js builds grid rows, badges and toasts from class strings, so it is scanned too.
  content: [
    './src/main/resources/templates/**/*.html',
    './src/main/resources/static/js/**/*.js'
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // FABRICS LTD. red. 500 is the logo's exact red (#ED1C24) - use it for the mark, accents and
        // large fills. 600 is the action red: white 14px text on #ED1C24 is 4.4:1, just short of AA,
        // so primary buttons sit one step darker (5.2:1) and read as the same red.
        brand: {
          50:'#fef2f2',100:'#fde4e4',200:'#fbcdce',300:'#f6a3a6',400:'#f0686d',
          500:'#ed1c24',600:'#d7141b',700:'#b3121a',800:'#92141a',900:'#79161b',950:'#42070a'
        },
        // Neutral gray with only a trace of cool: Tailwind's slate is blue enough to fight the red.
        gray: {
          50:'#f7f7f8',100:'#efeff1',200:'#e3e4e8',300:'#cfd1d6',400:'#9ea2ab',
          500:'#6e737d',600:'#50555e',700:'#3c4048',800:'#26292f',900:'#17191d',950:'#0e0f12'
        }
      },
      fontFamily: {
        sans: ['Inter', 'Segoe UI', 'ui-sans-serif', 'system-ui', '-apple-system', 'Roboto', 'sans-serif']
      },
      fontSize: {
        '2xs': ['0.6875rem', { lineHeight: '1rem' }]
      },
      boxShadow: {
        // Surfaces separate by border first; shadows stay faint.
        xs:    '0 1px 2px 0 rgb(23 25 29 / 0.04)',
        card:  '0 1px 2px 0 rgb(23 25 29 / 0.04), 0 1px 3px 0 rgb(23 25 29 / 0.03)',
        float: '0 12px 32px -8px rgb(23 25 29 / 0.18), 0 4px 8px -4px rgb(23 25 29 / 0.08)'
      },
      keyframes: {
        'toast-in':  { from: { opacity: 0, transform: 'translateY(8px)' }, to: { opacity: 1, transform: 'none' } },
        'drawer-in': { from: { transform: 'translateX(24px)', opacity: 0 }, to: { transform: 'none', opacity: 1 } }
      },
      animation: {
        'toast-in':  'toast-in .18s ease-out',
        'drawer-in': 'drawer-in .2s ease-out'
      }
    }
  },
  plugins: [require('@tailwindcss/forms')]
}
