import { defineConfig } from '@playwright/test';

// Needs the backend on :8080 with the panel built in, started with nexus.demo.scenario set.
export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  use: { baseURL: 'http://localhost:8080', trace: 'retain-on-failure' },
});
