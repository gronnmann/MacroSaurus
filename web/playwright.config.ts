import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
    testDir: './e2e',
    timeout: 30_000,
    fullyParallel: true,
    use: {
        baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173',
        trace: 'retain-on-failure',
    },
    webServer: {
        command: 'pnpm dev --host 127.0.0.1',
        url: 'http://127.0.0.1:5173',
        reuseExistingServer: true,
    },
    projects: [
        { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
        { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
    ],
})
