
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { getDevProxyTarget } from './src/config/api'

const devProxyTarget = getDevProxyTarget()

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: devProxyTarget,
        changeOrigin: true,
        timeout: 31 * 60 * 1000,
      },
      '/ws': {
        target: devProxyTarget,
        ws: true,
      },
    },
  },
})
