import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import pkg from './package.json';

// Single source of truth for the app version: package.json. Injected as __APP_VERSION__
// (see src/version.ts) so the UI never hardcodes a version number.

// Dev server proxies /api and /media to the backend so the SPA behaves
// exactly as it does behind the nginx reverse proxy in production.
export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/media': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
