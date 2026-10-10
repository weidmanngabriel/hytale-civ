import { defineConfig } from 'vite';
import { resolve } from 'node:path';
export default defineConfig({
  base: './',
  build: { rollupOptions: { input: { replay: resolve(import.meta.dirname, 'index.html'), live: resolve(import.meta.dirname, 'live.html') } } }
});
