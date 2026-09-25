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
        // Placeholder brand ramp - replace with ASG brand values.
        brand: {
          50:'#eef6ff',100:'#d9eaff',200:'#bcdaff',300:'#8ec2ff',
          400:'#59a1ff',500:'#337dff',600:'#1d5df5',700:'#1749e1',
          800:'#193eb6',900:'#1a388f'
        }
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif']
      },
      keyframes: {
        'toast-in': { from: { opacity: 0, transform: 'translateY(8px)' }, to: { opacity: 1, transform: 'none' } }
      },
      animation: {
        'toast-in': 'toast-in .18s ease-out'
      }
    }
  },
  plugins: [require('@tailwindcss/forms')]
}
