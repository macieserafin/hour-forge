import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5186'

export default defineConfig({
  testDir: './tests/e2e',
  globalSetup: './tests/support/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL,
    channel: 'chrome',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } },
    { name: 'tablet', grep: /@responsive/, use: { ...devices['Desktop Chrome'], viewport: { width: 900, height: 1100 } } },
    { name: 'mobile', grep: /@responsive/, use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
  ],
  webServer: {
    command: `npm run dev -- --port ${new URL(baseURL).port || '5173'}`,
    url: baseURL,
    reuseExistingServer: false,
    env: { BACKEND_URL: `http://127.0.0.1:${process.env.E2E_BACKEND_PORT ?? '18086'}` },
  },
})
