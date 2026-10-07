import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies the backend so API calls are same-origin (no CORS) during development.
// When the Spring Boot service runs on :8080, calls to /admin/* and /api/* are forwarded.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/admin': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
    },
  },
})
