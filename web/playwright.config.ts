import { defineConfig, devices } from '@playwright/test'

const preview = process.env.E2E_PREVIEW === '1'
const serverUrl = preview ? 'http://127.0.0.1:4173' : 'http://127.0.0.1:5173'

export default defineConfig({
    testDir: './e2e',
    timeout: 30_000,
    fullyParallel: true,
    use: {
        baseURL: process.env.E2E_BASE_URL || serverUrl,
        trace: 'retain-on-failure',
    },
    webServer: {
        command: preview ? 'pnpm exec vite preview --host 127.0.0.1' : 'pnpm dev --host 127.0.0.1',
        url: serverUrl,
        reuseExistingServer: true,
    },
    projects: [
        { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
        { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
        { name: 'mobile-webkit', use: { ...devices['iPhone 13'] } },
    ],
})
