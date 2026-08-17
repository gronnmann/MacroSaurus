import { expect, test } from '@playwright/test'

const testUser = 'live-e2e-user'
const entryPrefix = 'Live browser check '

test.describe('live backend integration', () => {
    test.skip(
        process.env.E2E_LIVE_BACKEND !== '1',
        'Set E2E_LIVE_BACKEND=1 when the Spring backend and PostgreSQL are running.',
    )

    test.afterEach(async ({ request }) => {
        const date = new Date().toISOString().slice(0, 10)
        const headers = { 'X-User-Id': testUser }
        const response = await request.get(`/api/v1/diary-days/${date}`, { headers })
        if (!response.ok()) return
        const day = (await response.json()) as {
            entries: Array<{ id: string; displayName: string }>
        }
        await Promise.all(
            day.entries
                .filter((entry) => entry.displayName.startsWith(entryPrefix))
                .map((entry) => request.delete(`/api/v1/diary-entries/${entry.id}`, { headers })),
        )
    })

    test('tracks and deletes a quick entry through the real API', async ({ page }) => {
        const failedApiResponses: string[] = []
        const browserErrors: string[] = []
        await page.route('**/api/v1/**', async (route) => {
            await route.continue({
                headers: {
                    ...route.request().headers(),
                    'X-User-Id': testUser,
                },
            })
        })
        page.on('response', (response) => {
            if (response.url().includes('/api/v1/') && response.status() >= 400) {
                failedApiResponses.push(`${response.status()} ${response.url()}`)
            }
        })
        page.on('pageerror', (error) => browserErrors.push(error.message))
        page.on('console', (message) => {
            if (message.type() === 'error') browserErrors.push(message.text())
        })

        await page.goto('/dashboard')
        await expect(page.getByRole('heading', { name: 'Today', exact: true })).toBeVisible()

        const entryName = `${entryPrefix}${Date.now()}`
        await page.getByRole('link', { name: 'Track', exact: true }).first().click()
        await page.getByRole('button', { name: 'Quick track' }).click()
        await page.getByLabel('Name').fill(entryName)
        await page.getByLabel('Protein (g)').fill('20')
        await page.getByLabel('Carbs (g)').fill('30')
        await page.getByLabel('Fat (g)').fill('10')
        const quickEntryResponse = page.waitForResponse(
            (response) =>
                response.url().endsWith('/api/v1/quick-entries') &&
                response.request().method() === 'POST',
        )
        await page.getByRole('button', { name: 'Add to Food Log' }).click()
        const response = await quickEntryResponse.catch((error: Error) => {
            throw new Error(
                `Quick-entry request was not completed. Browser errors: ${browserErrors.join(' | ') || 'none'}`,
                { cause: error },
            )
        })
        expect(response.status(), await response.text()).toBe(200)

        await page.goto('/food-log')
        await expect(page.getByText(entryName)).toBeVisible()

        await page.getByLabel(`Actions for ${entryName}`).click()
        await page.getByRole('button', { name: 'Delete', exact: true }).click()
        const confirmation = page.getByRole('dialog', { name: `Delete ${entryName}?` })
        await confirmation.getByRole('button', { name: 'Delete', exact: true }).click()
        await expect(page.getByText(entryName)).toHaveCount(0)

        expect(failedApiResponses).toEqual([])
        expect(browserErrors).toEqual([])
    })
})
