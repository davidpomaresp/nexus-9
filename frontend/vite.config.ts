import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The build goes straight into the backend's static resources, so the jar serves the panel.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../backend/target/classes/static',
    emptyOutDir: true,
  },
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
});
