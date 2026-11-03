import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8000', changeOrigin: true },
      '/graphql': { target: 'http://localhost:8000', changeOrigin: true },
      '/graphql-ws': { target: 'ws://localhost:8000', changeOrigin: true, ws: true },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;

          // Keep Ant Design ecosystem isolated; it's the largest UI dependency group.
          if (
            id.includes('/antd/') ||
            id.includes('/@ant-design/') ||
            id.includes('/rc-') ||
            id.includes('/@rc-component/')
          ) {
            return 'vendor-antd';
          }

          // Consolidate the rest into one chunk to avoid cross-chunk dependency cycles.
          return 'vendor-core';
        },
      },
    },
  },
});
