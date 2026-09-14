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
        else if (path === '/api/v1/me/features')
            data = {
                isAdmin: false,
                aiLabelScan: { granted: true, available: true },
            }
        else if (path === '/api/v1/me/coaching/status')
            data = {
                setupComplete: true,
                goal: {
                    id: 'goal-1',
                    type: 'MAINTAIN',
                    startingWeightKg: 80,
                    weeklyRatePercent: 0,
                    status: 'ACTIVE',
                    startedOn: date,
                },
                program: {
                    id: 'program-1',
                    goalId: 'goal-1',
                    style: 'COACHED',
                    effectiveFrom: date,
                    energyKcal: 2200,
                    proteinG: 165,
                    carbohydrateG: 240,
                    fatG: 70,
                    source: 'ONBOARDING',
                },
                nextCheckInDate: '2026-08-24',
                checkInDue: false,
            }
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
        else if (path === '/api/v1/trackables/suggestions/time-of-day')
            data = { anchorHour: 11, items: [] }
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
        else if (path.endsWith('/last-amount')) data = undefined
        else if (path === '/api/v1/food-revisions/food-revision-1/resolve') {
            const input = route.request().postDataJSON()
            const factor = input.quantity / 100
            data = {
                foodRevisionId: banana.revisionId,
                displayName: banana.name,
                quantity: input.quantity,
                unit: input.unit,
                resolvedGrams: input.quantity,
                nutrients: Object.fromEntries(
                    Object.entries(banana.nutrients).map(([code, value]) => [code, value * factor]),
                ),
            }
        } else if (
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
                lowerKcal: 1900,
                upperKcal: 2860,
                confidence: 'LOW',
                adaptiveEligible: false,
                algorithmVersion: 'energy-v2',
                modelState: 'BASELINE',
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
        else if (path === '/api/v1/admin/users') data = []
        else if (path.includes('/copies') || path === '/api/v1/diary-entries/entry-1') data = entry
        const noContent = method === 'DELETE' || path.endsWith('/last-amount')
        await route.fulfill({
            status: noContent ? 204 : 200,
            contentType: 'application/json',
            body: noContent ? '' : JSON.stringify(data),
        })
    })
}

test.beforeEach(async ({ page }) => {
    await mockApi(page)
})

test('dashboard presents daily nutrition and real habit activity', async ({ page }, testInfo) => {
    await page.goto(`/dashboard?date=${date}`)
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Daily nutrition' })).toBeVisible()
    await expect(page.getByLabel('Dashboard date')).toHaveValue(date)
    await expect(page.getByRole('img', { name: /1,840 of 2,200 calories consumed/ })).toBeVisible()
    await expect(page.getByRole('link', { name: /Weigh-in: 1 of 7/ })).toBeVisible()
    await expect(page.getByRole('link', { name: /Food logging: 1 of 7/ })).toBeVisible()
    await expect(page.locator('.habit-calendar i')).toHaveCount(60)
    await expect(page.locator('.habit-card--green .habit-calendar i.complete')).toHaveCount(1)
    await expect(page.locator('.habit-card--orange .habit-calendar i.complete')).toHaveCount(1)
    await page.getByRole('button', { name: 'Remaining' }).click()
    await expect(page.locator('.energy-ring').getByText('360', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: /View food log/ })).toBeVisible()
    await expect(page.getByText(/Open Food Facts|immutable|USDA seeded/)).toHaveCount(0)
    if (testInfo.project.name === 'desktop-chromium')
        await page.screenshot({
            path: testInfo.outputPath('dashboard-desktop.png'),
            fullPage: true,
        })
})

test('Track lets you choose date and time and includes weigh-ins', async ({ page }) => {
    await page.goto('/track')
    await expect(
        page.getByRole('dialog').getByRole('link', { name: 'Macrosaurus dashboard' }),
    ).toBeVisible()
    await page.getByRole('tab', { name: 'Quick Add' }).click()
    await expect(page.getByLabel('Date', { exact: true })).toBeVisible()
    await expect(page.getByLabel('Time', { exact: true })).toBeVisible()
    await expect(page.getByLabel('Meal', { exact: true })).toHaveCount(0)

    await page.getByRole('tab', { name: 'More' }).click()
    await page.getByRole('button', { name: /Log weight/ }).click()
    await page.getByLabel('Weight (kg)').fill('81.5')
    await page.getByRole('button', { name: 'Add weigh-in' }).click()
    await expect(page.getByText('Weigh-in added')).toBeVisible()
})

test('center Track action searches foods and recipes together', async ({ page }) => {
    await page.goto(`/dashboard?date=${date}`)
    await page.getByRole('link', { name: 'Track', exact: true }).first().click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByPlaceholder('Search foods and recipes…')).toBeVisible()
    await page.getByText('Banana, raw').click()
    await expect(page.getByRole('button', { name: 'Add to Food Log' })).toBeVisible()
})

test('Food Log exposes accessible actions above mobile navigation', async ({ page }, testInfo) => {
    await page.goto(`/food-log?date=${date}`)
    await expect(page.getByRole('heading', { name: 'Monday, August 17' })).toBeVisible()
    await expect(
        page.getByRole('heading', { name: /Breakfast|Lunch|Dinner|Snack|Other/ }),
    ).toHaveCount(0)
    await page.getByLabel('Actions for Banana, raw').click()
    await expect(page.getByRole('button', { name: 'Edit' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Copy to today' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Copy to yesterday' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Choose date & time' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Delete' })).toBeVisible()
    const popover = page.locator('.entry-menu-popover')
    const bounds = await popover.boundingBox()
    expect(bounds).not.toBeNull()
    expect(bounds?.y).toBeGreaterThanOrEqual(0)
    expect((bounds?.y ?? 0) + (bounds?.height ?? 0)).toBeLessThanOrEqual(
        page.viewportSize()?.height ?? 0,
    )
    if (testInfo.project.name === 'mobile-chromium') {
        expect(
            Number(await popover.evaluate((element) => getComputedStyle(element).zIndex)),
        ).toBeGreaterThan(70)
    }
    await page.getByRole('button', { name: 'Delete' }).click()
    await expect(page.getByRole('heading', { name: 'Delete Banana, raw?' })).toBeVisible()
    await page.getByRole('button', { name: 'Cancel' }).click()
    await page.getByRole('button', { name: 'Edit' }).click()
    await expect(page.getByRole('heading', { name: 'Edit Banana, raw' })).toBeVisible()
})

test('non-admin accounts do not receive an administration link', async ({ page }) => {
    await page.goto('/profile')
    await expect(page.getByRole('heading', { name: 'Macro Athlete' })).toBeVisible()
    await expect(page.getByRole('link', { name: /Administration/ })).toHaveCount(0)
})

test('administration is linked from Account without adding navigation items', async ({ page }) => {
    await page.route('**/api/v1/me/features', async (route) => {
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({
                isAdmin: true,
                aiLabelScan: { granted: true, available: true },
            }),
        })
    })
    await page.goto('/profile')

    await expect(page.locator('.sidebar > nav > a')).toHaveCount(5)
    await expect(page.locator('.bottom-nav > a')).toHaveCount(5)
    await expect(page.getByRole('link', { name: 'Admin', exact: true })).toHaveCount(0)
    const administration = page.getByRole('link', { name: /Administration/ })
    await expect(administration).toBeVisible()
    await administration.click()
    await expect(page.getByRole('heading', { name: 'Feature access' })).toBeVisible()
})

test('a new account is guided through reload-safe goal setup', async ({ page }) => {
    let setupComplete = false
    const draft = {
        currentStep: 1,
        displayName: 'New Athlete',
        locale: 'en-NO',
        timezone: 'Europe/Oslo',
        birthDate: '1990-05-10',
        heightCm: 178,
        formulaSex: 'MALE',
        activityMultiplier: 1.55,
        weightKg: 80,
        goalType: 'MAINTAIN',
        weeklyRatePercent: 0,
        programStyle: 'COACHED',
        proteinGPerKg: 1.6,
        fatEnergyPercent: 25,
    }
    await page.route('**/api/v1/me/coaching/**', async (route) => {
        const path = new URL(route.request().url()).pathname
        if (path.endsWith('/status')) {
            await route.fulfill({ json: { setupComplete, checkInDue: false } })
        } else if (path.endsWith('/setup-draft/preview')) {
            await route.fulfill({
                json: {
                    expenditure: {
                        date,
                        baselineKcal: 2380,
                        suggestedKcal: 2380,
                        lowerKcal: 1900,
                        upperKcal: 2860,
                        confidence: 'LOW',
                        adaptiveEligible: false,
                        algorithmVersion: 'energy-v2',
                        explanation: [],
                        requirements: {},
                        modelState: 'BASELINE',
                    },
                    energyKcal: 2380,
                    proteinG: 128,
                    carbohydrateG: 318,
                    fatG: 66,
                    warnings: [],
                },
            })
        } else if (path.endsWith('/setup-draft/complete')) {
            setupComplete = true
            await route.fulfill({ json: { setupComplete: true, checkInDue: false } })
        } else if (route.request().method() === 'PUT') {
            Object.assign(draft, route.request().postDataJSON())
            await route.fulfill({ json: draft })
        } else {
            await route.fulfill({ json: draft })
        }
    })

    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/setup$/)
    await expect(page.getByRole('heading', { name: 'Tell your coach about you' })).toBeVisible()
    await page.getByRole('button', { name: 'Continue' }).click()
    await expect(page.getByRole('heading', { name: 'Where are you today?' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Where are you today?' })).toBeVisible()
    await page.getByRole('button', { name: 'Continue' }).click()
    await page.getByRole('button', { name: 'Continue' }).click()
    await page.getByRole('button', { name: 'Continue' }).click()
    await expect(page.getByRole('heading', { name: 'Your first program' })).toBeVisible()
    await page.getByRole('button', { name: 'Start my program' }).click()
    await expect(page).toHaveURL(/\/dashboard$/)
})

test('weekly check-in reviews partial logging before accepting targets', async ({ page }) => {
    let reviewed = false
    let proposalReady = false
    const checkIn = () => ({
        due: true,
        id: '11111111-1111-1111-1111-111111111111',
        weekStart: '2026-08-24',
        periodFrom: '2026-08-17',
        periodTo: '2026-08-23',
        status: 'DRAFT',
        needsWeight: false,
        candidates: [
            {
                date: '2026-08-20',
                loggedEnergyKcal: 800,
                entryCount: 2,
                reason: 'POSSIBLE_PARTIAL',
                review: reviewed ? { date: '2026-08-20', status: 'CONFIRMED_COMPLETE' } : undefined,
            },
        ],
        proposal: proposalReady
            ? {
                  estimate: {
                      date: '2026-08-23',
                      baselineKcal: 2380,
                      adaptiveKcal: 2450,
                      suggestedKcal: 2420,
                      lowerKcal: 2250,
                      upperKcal: 2590,
                      confidence: 'MEDIUM',
                      adaptiveEligible: true,
                      algorithmVersion: 'energy-v2',
                      explanation: [],
                      requirements: {},
                      modelState: 'UPDATING',
                  },
                  previousEnergyKcal: 2200,
                  proposedEnergyKcal: 2250,
                  proposedProteinG: 160,
                  proposedCarbohydrateG: 258,
                  proposedFatG: 65,
                  targetUpdateAvailable: true,
                  warnings: [],
              }
            : undefined,
    })
    await page.route('**/api/v1/me/coaching/status', (route) =>
        route.fulfill({ json: { setupComplete: true, checkInDue: true } }),
    )
    await page.route('**/api/v1/me/coaching/check-ins/**', async (route) => {
        const path = new URL(route.request().url()).pathname
        if (path.endsWith('/refresh')) proposalReady = true
        await route.fulfill({ json: checkIn() })
    })
    await page.route('**/api/v1/diary-days/2026-08-20/analysis', async (route) => {
        reviewed = true
        await route.fulfill({
            json: { date: '2026-08-20', ...route.request().postDataJSON() },
        })
    })

    await page.goto('/check-in')
    await expect(page.getByRole('heading', { name: 'Update your week' })).toBeVisible()
    await page.getByRole('button', { name: 'That is complete' }).click()
    await expect(page.getByText('CONFIRMED COMPLETE')).toBeVisible()
    await page.getByRole('button', { name: 'Calculate update' }).click()
    await expect(page.getByText('2,250 kcal')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Accept new targets' })).toBeEnabled()
})

test('label photo is available without a barcode lookup', async ({ page }) => {
    await page.goto('/track')
    await page.getByRole('tab', { name: 'Scan' }).click()
    await expect(page.getByText('Fill from a label photo')).toBeVisible()
    await page.getByLabel('Enter barcode').fill('3017620422003')
    await page.getByRole('button', { name: 'Look up' }).click()
    await expect(page.getByRole('button', { name: 'Create food manually' })).toBeVisible()
    await expect(page.getByText('Fill from a label photo')).toBeVisible()
    await expect(page.getByLabel('Upload label photo')).not.toHaveAttribute('capture')
    await expect(page.getByLabel('Take label photo')).toHaveAttribute('capture', 'environment')
})

test('AI meal estimate submits a photo with text and logs reviewed calories', async ({ page }) => {
    let estimateInput: { text: string; images: string[] } | undefined
    let tracked: { calories: number } | undefined
    await page.route('**/api/v1/meal-estimates', async (route) => {
        estimateInput = route.request().postDataJSON()
        await route.fulfill({
            json: {
                name: 'Chicken and rice',
                calories: 650,
                proteinG: 45,
                carbohydrateG: 70,
                fatG: 20,
                fiberG: 3,
                assumptions: ['Includes one tablespoon of oil'],
            },
        })
    })
    await page.route('**/api/v1/quick-entries', async (route) => {
        tracked = route.request().postDataJSON()
        await route.fulfill({ json: entry })
    })
    await page.goto('/track')
    await page.getByRole('tab', { name: 'More' }).click()
    await page.getByRole('button', { name: 'AI meal estimate' }).click()
    await page.getByLabel('Upload meal photo').setInputFiles({
        name: 'meal.png',
        mimeType: 'image/png',
        buffer: Buffer.from(
            'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4ioAAAAASUVORK5CYII=',
            'base64',
        ),
    })
    await expect(page.getByAltText('Meal view 1')).toBeVisible()
    await page.getByLabel('Meal description').fill('200 g chicken with rice and olive oil')
    await page.getByRole('button', { name: 'Estimate calories' }).click()
    await expect(page.getByLabel('Meal name')).toHaveValue('Chicken and rice')
    expect(estimateInput?.text).toBe('200 g chicken with rice and olive oil')
    expect(estimateInput?.images[0]).toMatch(/^data:image\/jpeg;base64,/)
    expect(tracked).toBeUndefined()
    await page.getByLabel('Calories', { exact: true }).fill('720')
    await page.getByRole('button', { name: 'Add to Food Log' }).click()
    await expect.poll(() => tracked?.calories).toBe(720)
    await expect(page.getByRole('dialog', { name: 'Estimate a meal' })).toHaveCount(0)
})

test('mobile layout has the raised centered Track action', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile-chromium', 'mobile-only assertion')
    await page.goto(`/dashboard?date=${date}`)
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    const navigation = page.getByRole('navigation', {
        name: 'Mobile navigation',
    })
    await expect(navigation).toBeVisible()
    await expect(navigation.getByRole('link', { name: 'Track' })).toBeVisible()
    await expect(navigation.getByRole('link', { name: 'Food Log' })).toBeVisible()
    await expect(navigation.getByRole('link', { name: 'Profile' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(
        page.viewportSize()?.width,
    )
    await page.screenshot({
        path: testInfo.outputPath('dashboard-mobile.png'),
        fullPage: true,
    })
})

test('mobile tracking surface exactly fills the viewport', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile-chromium', 'mobile-only assertion')
    await page.goto('/track')
    await page.getByRole('tab', { name: 'Scan' }).click()

    const viewport = page.viewportSize()
    expect(viewport).not.toBeNull()
    const overlay = await page.locator('.track-overlay').boundingBox()
    const sheet = await page.locator('.track-sheet').boundingBox()
    expect(overlay).toEqual({ x: 0, y: 0, width: viewport?.width, height: viewport?.height })
    expect(sheet).toEqual({ x: 0, y: 0, width: viewport?.width, height: viewport?.height })
    await expect(page.locator('.track-sheet')).toHaveCSS('border-radius', '0px')
    expect(
        await page.evaluate(() =>
            document.querySelector('meta[name="viewport"]')?.getAttribute('content'),
        ),
    ).toContain('width=device-width')
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(viewport?.width)
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

test('Track preserves a Food Log day and submits the chosen time', async ({ page }, testInfo) => {
    let tracked: { localDate: string; consumedAt: string } | undefined
    await page.route('**/api/v1/diary-entries/food', async (route) => {
        tracked = route.request().postDataJSON()
        await route.fulfill({ json: entry })
    })
    await page.goto(`/food-log?date=${date}`)
    await page.getByRole('link', { name: 'Track', exact: true }).last().click()
    await page.getByRole('button', { name: /Banana, raw/ }).click()
    await expect(page.getByLabel('Date', { exact: true })).toHaveValue(date)
    await page.getByLabel('Time', { exact: true }).fill('19:25')
    await page.screenshot({ path: testInfo.outputPath('tracking-date-time.png') })
    const expected = await page.evaluate((day) => new Date(`${day}T19:25:00`).toISOString(), date)
    await page.getByRole('button', { name: 'Add to Food Log' }).click()
    await expect.poll(() => tracked).toMatchObject({ localDate: date, consumedAt: expected })
})

test('recipe ingredients use Track tabs and preserve portions in the saved recipe', async ({
    page,
}) => {
    let saved: { name: string; ingredients: unknown[] } | undefined
    let logged = false
    page.on('request', (request) => {
        if (/\/(diary-entries\/(food|recipe)|quick-entries)$/.test(new URL(request.url()).pathname))
            logged = true
    })
    await page.route('**/api/v1/recipes', async (route) => {
        saved = route.request().postDataJSON()
        await route.fulfill({ json: { id: 'new-recipe' } })
    })
    await page.goto('/recipes/new')
    await page.getByLabel('Recipe name').fill('Banana bowl')
    await page.getByRole('button', { name: 'Add ingredient', exact: true }).click()
    const sheet = page.getByRole('dialog')
    await expect(sheet.getByRole('tab')).toHaveText(['Search', 'Scan', 'Quick Add', 'More'])
    await sheet.getByRole('button', { name: /Banana, raw/ }).click()
    await expect(sheet.getByLabel('Amount')).toHaveValue('1')
    await sheet.getByLabel('Amount').fill('2')
    await sheet.getByRole('button', { name: 'Add ingredient', exact: true }).click()
    await expect(sheet).toHaveCount(0)
    await expect(page.getByLabel('Recipe name')).toHaveValue('Banana bowl')
    await expect(page.getByLabel('Banana, raw unit')).toHaveValue('portion:portion-1')
    await page.getByRole('button', { name: 'Create recipe', exact: true }).click()
    await expect
        .poll(() => saved)
        .toMatchObject({
            name: 'Banana bowl',
            ingredients: [
                {
                    foodRevisionId: 'food-revision-1',
                    quantity: 2,
                    unit: 'portion',
                    portionId: 'portion-1',
                },
            ],
        })
    expect(logged).toBe(false)
})

test('Quick Add expands nutrients and keeps them when collapsed', async ({ page }, testInfo) => {
    let tracked: unknown
    await page.route('**/api/v1/quick-entries', async (route) => {
        tracked = route.request().postDataJSON()
        await route.fulfill({ json: { entry } })
    })
    await page.goto('/track')
    await page.getByRole('tab', { name: 'Quick Add' }).click()
    await page.getByLabel('Name', { exact: true }).fill('Lunch')
    await expect(page.getByLabel('Saturated fat (g)')).not.toBeVisible()
    await page.getByText('More nutrients', { exact: false }).click()
    await page.getByLabel('Saturated fat (g)').fill('2,5')
    await page.getByLabel('Fiber (g)').fill('3')
    await page.getByLabel('Salt (g)').fill('1,2')
    await page.getByLabel('Iron (mg)').fill('8')
    const ironBounds = await page.getByLabel('Iron (mg)').boundingBox()
    const actionBounds = await page.getByRole('button', { name: 'Add to Food Log' }).boundingBox()
    if (!ironBounds || !actionBounds)
        throw new Error('Nutrient input and submit button must be rendered')
    expect(ironBounds.y + ironBounds.height).toBeLessThan(actionBounds.y)
    await page.screenshot({ path: testInfo.outputPath('quick-nutrients.png'), fullPage: true })
    await page.getByText('More nutrients', { exact: false }).click()
    await page.getByLabel('Save for next time').check()
    await page.getByRole('button', { name: 'Add to Food Log' }).click()
    await expect
        .poll(() => tracked)
        .toMatchObject({
            fiberG: 3,
            additionalNutrients: { saturated_fat_g: 2.5, sodium_mg: 480, iron_mg: 8 },
            saveAsFood: true,
        })
})

test('editing a quick entry preserves and clears additional nutrients', async ({ page }) => {
    let edited: unknown
    const quick = {
        ...entry,
        entryType: 'QUICK',
        nutrients: {
            energy_kcal: 100,
            protein_g: 10,
            carbohydrate_g: 10,
            fat_g: 2,
            fiber_g: 3,
            saturated_fat_g: 1,
            sodium_mg: 480,
            iron_mg: 8,
        },
    }
    await page.route('**/api/v1/diary-days/*', async (route) =>
        route.fulfill({ json: { date, entries: [quick], totals: quick.nutrients } }),
    )
    await page.route('**/api/v1/diary-entries/entry-1', async (route) => {
        edited = route.request().postDataJSON()
        await route.fulfill({ json: quick })
    })
    await page.goto(`/food-log?date=${date}`)
    await page.getByRole('button', { name: 'Actions for Banana, raw' }).click()
    await page.getByRole('button', { name: 'Edit', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Edit Banana, raw' })
    await dialog.getByText('More nutrients', { exact: false }).click()
    await expect(dialog.getByLabel('Salt (g)')).toHaveValue('1.2')
    await expect(dialog.getByLabel('Iron (mg)')).toHaveValue('8')
    await dialog.getByLabel('Saturated fat (g)').fill('0')
    await dialog.getByLabel('Iron (mg)').clear()
    await dialog.getByText('More nutrients', { exact: false }).click()
    await dialog.getByRole('button', { name: 'Save changes' }).click()
    await expect
        .poll(() => edited)
        .toMatchObject({ fiberG: 3, additionalNutrients: { saturated_fat_g: 0, sodium_mg: 480 } })
    expect((edited as { additionalNutrients: object }).additionalNutrients).not.toHaveProperty(
        'iron_mg',
    )
})

test('food creation follows EU label order and converts salt', async ({ page }, testInfo) => {
    let saved: unknown
    await page.route('**/api/v1/foods', async (route) => {
        saved = route.request().postDataJSON()
        await route.fulfill({ json: banana })
    })
    await page.goto('/foods/new')
    await page.getByLabel('Food name').fill('Soup')
    const fields = page.locator('.nutrient-editor').first().locator('input')
    await expect
        .poll(() =>
            fields.evaluateAll((inputs) => inputs.map((input) => input.getAttribute('name'))),
        )
        .toEqual([
            'nutrients.energy_kcal',
            'nutrients.fat_g',
            'nutrients.saturated_fat_g',
            'nutrients.carbohydrate_g',
            'nutrients.sugars_g',
            'nutrients.fiber_g',
            'nutrients.protein_g',
            'nutrients.sodium_mg',
        ])
    await page.getByLabel('Salt (g)').fill('1,2')
    await page.getByLabel('Saturated fat (g)').fill('2,5')
    await page.screenshot({ path: testInfo.outputPath('food-label-order.png'), fullPage: true })
    await page.getByRole('button', { name: 'Create food', exact: true }).click()
    await expect
        .poll(() => saved)
        .toMatchObject({ nutrients: { saturated_fat_g: 2.5, sodium_mg: 480 } })
})
