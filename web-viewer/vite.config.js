import { defineConfig } from 'vite';
import { resolve } from 'node:path';
export default defineConfig({
  base: './',
  build: { rollupOptions: { input: { live: resolve(import.meta.dirname, 'index.html'), liveAlias: resolve(import.meta.dirname, 'live.html'), replay: resolve(import.meta.dirname, 'replay.html') } } }
});
