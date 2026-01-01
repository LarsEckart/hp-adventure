const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  use: {
    baseURL: 'http://localhost:4173',
    headless: true,
    viewport: { width: 1280, height: 720 },
    serviceWorkers: 'block'
  },
  webServer: {
    command: 'python3 -m http.server 4173 -d backend/src/main/resources/public',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000
  }
});
