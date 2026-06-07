/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        background: '#0a0a0a',
        neonBlue: '#00f0ff',
        darkSurface: '#121212',
      },
      boxShadow: {
        'neon': '0 0 10px #00f0ff, 0 0 20px #00f0ff',
      }
    },
  },
  plugins: [],
}
