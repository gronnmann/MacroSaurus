import { expect, type Page, test } from '@playwright/test'

const date = '2026-08-17'
const banana = {
    id: 'food-1',
    revisionId: 'food-revision-1',
    revision: 1,
    name: 'Banana, raw',
    brand: null,
    barcode: null,
    source: 'USDA',
    basisType: 'PER_100_G',
    basisAmount: 100,
    basisUnit: 'g',
    nutrients: {
        energy_kcal: 89,
        protein_g: 1.1,
        carbohydrate_g: 22.8,
        fat_g: 0.3,
        fiber_g: 2.6,
    },
    portions: [
        {
            id: 'portion-1',
            name: 'medium banana',
            quantity: 1,
            gramWeight: 118,
            default: true,
        },
    ],
    createdAt: `${date}T08:00:00Z`,
}
const entry = {
    id: 'entry-1',
    localDate: date,
    consumedAt: `${date}T08:15:00+02:00`,
    meal: 'BREAKFAST',
    displayName: 'Banana, raw',
    entryType: 'FOOD',
    sourceRevisionId: 'food-revision-1',
    quantity: 118,
    unit: 'g',
    nutrients: {
        energy_kcal: 105,
        protein_g: 1.3,
        carbohydrate_g: 26.9,
        fat_g: 0.4,
    },
}
const nutrients = [
    {
        code: 'energy_kcal',
        displayName: 'Energy',
        category: 'ENERGY',
        unit: 'kcal',
        sortOrder: 1,
    },
    {
        code: 'protein_g',
        displayName: 'Protein',
        category: 'MACRONUTRIENT',
        unit: 'g',
        sortOrder: 2,
    },
    {
        code: 'carbohydrate_g',
        displayName: 'Carbohydrate',
        category: 'MACRONUTRIENT',
        unit: 'g',
        sortOrder: 3,
    },
    {
        code: 'fat_g',
        displayName: 'Fat',
        category: 'MACRONUTRIENT',
        unit: 'g',
        sortOrder: 4,
    },
    {
        code: 'fiber_g',
        displayName: 'Fiber',
        category: 'MACRONUTRIENT',
        unit: 'g',
        sortOrder: 5,
    },
    {
        code: 'iron_mg',
        displayName: 'Iron',
        category: 'MINERAL',
        unit: 'mg',
        sortOrder: 10,
    },
]

async function mockApi(page: Page) {
    await page.route('**/api/v1/**', async (route) => {
        const url = new URL(route.request().url())
        const path = url.pathname
        const method = route.request().method()
        const from = url.searchParams.get('from') ?? date
        const to = url.searchParams.get('to') ?? date
        let data: unknown = {}
        if (path === '/api/v1/nutrients') data = nutrients
        else if (path === '/api/v1/me/targets')
            data = nutrients.map((item) => ({
                nutrientCode: item.code,
                displayName: item.displayName,
                unit: item.unit,
                targetAmount: null,
                minimumAmount: null,
                maximumAmount: null,
            }))
        else if (path === '/api/v1/me/goals/resolved')
            data = dateRange(from, to).map((day) => ({
                date: day,
                energyKcal: 2200,
                proteinG: 165,
                carbohydrateG: 240,
                fatG: 70,
                expenditureKcal: 2380,
                energyRule: 'PERCENT_DELTA',
                warnings: [],
            }))
        else if (path === '/api/v1/me/goals' && method === 'GET')
            data = {
                configured: true,
                energyMode: 'PERCENT_DELTA',
                energyValue: -10,
                macroMode: 'GUIDED',
                proteinGPerKg: 1.8,
                fatEnergyPercent: 25,
                weightBasis: 'LATEST_WEIGHT',
            }
        else if (path === '/api/v1/me/goals' && method === 'PUT')
            data = { configured: true, ...route.request().postDataJSON() }
        else if (path === '/api/v1/diary-days')
            data = dateRange(from, to).map((day) => ({
                date: day,
                entries: day === date ? [entry] : [],
                totals:
                    day === date
                        ? {
                              energy_kcal: 1840,
                              protein_g: 142,
                              carbohydrate_g: 205,
                              fat_g: 61,
                              fiber_g: 28,
                              iron_mg: 8.4,
                          }
                        : {},
            }))
        else if (path.startsWith('/api/v1/diary-days/'))
            data = {
                date: path.slice(-10),
                entries: [entry],
                totals: {
                    energy_kcal: 1840,
                    protein_g: 142,
                    carbohydrate_g: 205,
                    fat_g: 61,
                    fiber_g: 28,
                    iron_mg: 8.4,
                },
            }
        else if (path === '/api/v1/trackables')
            data = [
                {
                    type: 'FOOD',
                    id: banana.id,
                    revisionId: banana.revisionId,
                    name: banana.name,
                    brand: null,
                    servingLabel: 'medium banana',
                    nutrients: banana.nutrients,
                },
            ]
        else if (
            path === '/api/v1/foods/food-1' ||
            path === '/api/v1/food-revisions/food-revision-1'
        )
            data = banana
        else if (path.startsWith('/api/v1/barcodes/')) data = []
        else if (path === '/api/v1/weight-measurements')
            data =
                method === 'POST'
                    ? {
                          id: 'weight-new',
                          weightKg: route.request().postDataJSON().weightKg,
                          measuredAt: route.request().postDataJSON().measuredAt,
                          note: route.request().postDataJSON().note,
                      }
                    : [{ id: 'weight-1', weightKg: 80, measuredAt: `${date}T07:00:00Z` }]
        else if (path === '/api/v1/expenditure-estimates/current')
            data = {
                date,
                baselineKcal: 2380,
                adaptiveKcal: null,
                suggestedKcal: 2380,
                confidence: 'LOW',
                adaptiveEligible: false,
                algorithmVersion: 'energy-v1',
                explanation: ['Add more logged days to improve this estimate.'],
                requirements: { loggedDays: 3, weighIns: 1, weightSpanDays: 0 },
            }
        else if (path === '/api/v1/me/profile')
            data = {
                userId: 'dev-user',
                displayName: 'Macro Athlete',
                locale: 'en-NO',
                timezone: 'Europe/Oslo',
                unitSystem: 'METRIC',
                birthDate: '1990-05-10',
                heightCm: 178,
                formulaSex: 'MALE',
                activityMultiplier: 1.55,
            }
        else if (path.includes('/copies') || path === '/api/v1/diary-entries/entry-1') data = entry
        await route.fulfill({
            status: method === 'DELETE' ? 204 : 200,
            contentType: 'application/json',
            body: method === 'DELETE' ? '' : JSON.stringify(data),
        })
    })
}

test.beforeEach(async ({ page }) => {
    await mockApi(page)
})

test('dashboard presents the week and consumed or remaining views', async ({ page }, testInfo) => {
    await page.goto(`/dashboard?date=${date}`)
    await expect(page.getByRole('heading', { name: 'Today', exact: true })).toBeVisible()
    await expect(page.locator('.week-chart > button')).toHaveCount(7)
    const weeklyTargets = page.locator('.week-targets')
    await expect(weeklyTargets.getByText('1,840', { exact: true })).toBeVisible()
    await expect(weeklyTargets.getByText('/ 2,200 kcal', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'Remaining' }).click()
    await expect(page.getByText('360', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: /View food log/ })).toBeVisible()
    await expect(page.getByText(/Open Food Facts|immutable|USDA seeded/)).toHaveCount(0)
    if (testInfo.project.name === 'desktop-chromium')
        await page.screenshot({
            path: testInfo.outputPath('dashboard-desktop.png'),
            fullPage: true,
        })
})

test('Track logs at the current time and includes weigh-ins', async ({ page }) => {
    await page.goto('/track')
    await expect(page.getByRole('button', { name: /Log weight/ })).toBeVisible()

    await page.getByRole('button', { name: /Quick track/ }).click()
    await expect(page.getByText('Logged at the current date and time')).toBeVisible()
    await expect(page.getByLabel('Date', { exact: true })).toHaveCount(0)
    await expect(page.getByLabel('Time', { exact: true })).toHaveCount(0)
    await expect(page.getByLabel('Meal', { exact: true })).toHaveCount(0)

    await page.getByRole('button', { name: 'All tracking options' }).click()
    await page.getByRole('button', { name: /Log weight/ }).click()
    await page.getByLabel('Weight (kg)').fill('81.5')
    await page.getByRole('button', { name: 'Add weigh-in' }).click()
    await expect(page.getByText('Weigh-in added')).toBeVisible()
})

test('center Track action searches foods and recipes together', async ({ page }) => {
    await page.goto(`/dashboard?date=${date}`)
    await page.getByRole('link', { name: 'Track', exact: true }).first().click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: /Search foods & recipes/ }).click()
    await expect(page.getByPlaceholder('Search foods and recipes…')).toBeVisible()
    await page.getByText('Banana, raw').click()
    await expect(page.getByRole('button', { name: 'Add to Food Log' })).toBeVisible()
})

test('Food Log exposes edit, copy, custom date, and delete actions', async ({ page }) => {
    await page.goto(`/food-log?date=${date}`)
    await expect(page.getByRole('heading', { name: 'Today’s timeline' })).toBeVisible()
    await expect(
        page.getByRole('heading', { name: /Breakfast|Lunch|Dinner|Snack|Other/ }),
    ).toHaveCount(0)
    await page.getByLabel('Actions for Banana, raw').click()
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Copy to today' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Copy to yesterday' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Choose date & time' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible()
})

test('label photo appears only after an unmatched barcode', async ({ page }) => {
    await page.goto('/track')
    await page.getByRole('button', { name: /Scan barcode/ }).click()
    await expect(page.getByText('Take one label photo')).toHaveCount(0)
    await page.getByLabel('Enter barcode').fill('3017620422003')
    await page.getByRole('button', { name: 'Look up' }).click()
    await expect(page.getByText('Take one label photo')).toBeVisible()
    await expect(page.locator('input[type=file]')).not.toHaveAttribute('multiple', '')
})

test('mobile layout has the raised centered Track action', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile-chromium', 'mobile-only assertion')
    await page.goto(`/dashboard?date=${date}`)
    await expect(page.getByRole('heading', { name: 'Today', exact: true })).toBeVisible()
    const navigation = page.getByRole('navigation', {
        name: 'Mobile navigation',
    })
    await expect(navigation).toBeVisible()
    await expect(navigation.getByRole('link', { name: 'Track' })).toBeVisible()
    await expect(navigation.getByRole('link', { name: 'Food Log' })).toBeVisible()
    await expect(navigation.getByRole('link', { name: 'Profile' })).toBeVisible()
    await page.screenshot({
        path: testInfo.outputPath('dashboard-mobile.png'),
        fullPage: true,
    })
})

function dateRange(from: string, to: string) {
    const dates: string[] = []
    const current = new Date(`${from}T12:00:00Z`)
    const end = new Date(`${to}T12:00:00Z`)
    while (current <= end) {
        dates.push(current.toISOString().slice(0, 10))
        current.setUTCDate(current.getUTCDate() + 1)
    }
    return dates
}
