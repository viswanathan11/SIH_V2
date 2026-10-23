import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

const target = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/.well-known': { target, changeOrigin: true },
      '/v3': { target, changeOrigin: true },
      '/swagger-ui': { target, changeOrigin: true },
    },
  },
  preview: { port: 5173 },
  build: { chunkSizeWarningLimit: 1500 },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
