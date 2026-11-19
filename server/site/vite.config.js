import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { englishPages } from './docs/english-pages.js';

export default defineConfig({
  base: './',
  plugins: [react(), englishPages()],
  build: {
    manifest: true,
    rollupOptions: {
      input: {
        index: resolve('index.html'),
        download: resolve('download.html'),
        features: resolve('features.html'),
        changelog: resolve('changelog.html'),
        editions: resolve('editions.html'),
        thanks: resolve('thanks.html'),
        docs: resolve('docs/index.html'),
        purchases: resolve('docs/purchases.html'),
        'admin-console': resolve('src/admin.jsx'),
      },
      output: {
        entryFileNames: (chunk) => ['main', 'docs'].includes(chunk.name) ? 'assets/[name]-[hash].js' : 'assets/[name].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: (asset) => asset.names.some((name) => name === 'main.css' || name === 'docs.css') ? 'assets/[name]-[hash][extname]' : 'assets/[name][extname]',
      },
    },
  },
});
