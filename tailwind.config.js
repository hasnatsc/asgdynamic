/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/main/resources/templates/**/*.html'],
  theme: {
    extend: {
      colors: {
        // Placeholder brand ramp - replace with ASG brand values.
        brand: {
          50:'#eef6ff',100:'#d9eaff',200:'#bcdaff',300:'#8ec2ff',
          400:'#59a1ff',500:'#337dff',600:'#1d5df5',700:'#1749e1',
          800:'#193eb6',900:'#1a388f'
        }
      }
    }
  },
  plugins: [require('@tailwindcss/forms')]
}
