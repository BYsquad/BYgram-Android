import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: './',
  plugins: [react()],
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
        admin: resolve('src/admin.jsx'),
      },
      output: {
        entryFileNames: (chunk) => chunk.name === 'main' ? 'assets/[name]-[hash].js' : 'assets/[name].js',
        chunkFileNames: (chunk) => chunk.name === 'main' ? 'assets/[name]-[hash].js' : 'assets/[name].js',
        assetFileNames: (asset) => asset.names.includes('main.css') ? 'assets/[name]-[hash][extname]' : 'assets/[name][extname]',
      },
    },
  },
});
