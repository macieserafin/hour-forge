import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  // Only the public backend address is read. Root database/seed secrets are never exposed.
  const env = loadEnv(mode, process.cwd(), 'BACKEND_')
  const proxy = {
    '/api': {
      target: process.env.BACKEND_URL ?? env.BACKEND_URL ?? 'http://localhost:8080',
      changeOrigin: true,
    },
  }
  return {
    plugins: [react(), tailwindcss()],
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy, headers: { 'Referrer-Policy': 'no-referrer' } },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy, headers: { 'Referrer-Policy': 'no-referrer' } },
  }
})
