import { readFile } from 'node:fs/promises'
import { expect, type Page, test } from '@playwright/test'

async function mockApi(page: Page) {
    // Mock at fetch so this works before and after the production service worker
    // takes control. Playwright cannot route WebKit service-worker requests.
    await page.addInitScript(() => {
        const originalFetch = window.fetch.bind(window)
        const requests: { url: string; body: string | null }[] = []
        Reflect.set(window, 'scannerRequests', requests)
        window.fetch = async (input, init) => {
            const url = new URL(
                typeof input === 'string' ? input : input instanceof URL ? input.href : input.url,
                location.href,
            )
            const path = url.pathname
            if (url.origin !== location.origin || !path.startsWith('/api/v1/'))
                return originalFetch(input, init)
            requests.push({ url: url.href, body: init?.body ? String(init.body) : null })
            const missing = path.includes('/barcodes/')
            const data = missing
                ? { status: 404, detail: 'Barcode was not found' }
                : path.endsWith('/coaching/status')
                  ? { setupComplete: true }
                  : path.endsWith('/features')
                    ? { aiLabelScan: { granted: false, available: false } }
                    : path.endsWith('/time-of-day')
                      ? { anchorHour: 11, items: [] }
                      : []
            return new Response(JSON.stringify(data), {
                status: missing ? 404 : 200,
                headers: { 'Content-Type': 'application/json' },
            })
        }
    })
}

type Fixture = {
    name: string
    code: string
    angle?: number
    scale?: number
    blur?: number
    contrast?: number
    offCentre?: boolean
}
async function cameraFixture(page: Page, fixture: Fixture) {
    const svg = await readFile(
        new URL(`./fixtures/barcodes/${fixture.name}.svg`, import.meta.url),
        'utf8',
    )
    await page.addInitScript(
        ({ svg, fixture }) => {
            Object.defineProperty(MediaDevices.prototype, 'getUserMedia', {
                configurable: true,
                value: async () => {
                    const image = new Image()
                    image.src = `data:image/svg+xml;base64,${btoa(svg)}`
                    await image.decode()
                    const canvas = document.createElement('canvas')
                    canvas.width = 1280
                    canvas.height = 720
                    const context = canvas.getContext('2d')
                    if (!context) throw new Error('Canvas unavailable')
                    const stream = canvas.captureStream(30)
                    const track = stream.getVideoTracks()[0]
                    const originalStop = track.stop.bind(track)
                    track.stop = () => {
                        document.documentElement.dataset.cameraStopped = 'true'
                        originalStop()
                    }
                    const draw = () => {
                        if (track.readyState === 'ended') return
                        context.fillStyle = 'white'
                        context.fillRect(0, 0, canvas.width, canvas.height)
                        if (document.documentElement.dataset.showBarcode === 'true') {
                            context.save()
                            context.translate(
                                fixture.offCentre ? 220 : 640,
                                fixture.offCentre ? 90 : 360,
                            )
                            context.rotate(((fixture.angle || 0) * Math.PI) / 180)
                            context.filter = `blur(${fixture.blur || 0}px) contrast(${fixture.contrast ?? 1})`
                            const scale = fixture.scale ?? 1
                            context.drawImage(
                                image,
                                (-image.width * scale) / 2,
                                (-image.height * scale) / 2,
                                image.width * scale,
                                image.height * scale,
                            )
                            context.restore()
                        }
                        requestAnimationFrame(draw)
                    }
                    draw()
                    return stream
                },
            })
            Object.defineProperty(MediaDevices.prototype, 'enumerateDevices', {
                configurable: true,
                value: async () => [],
            })
        },
        { svg, fixture },
    )
}

async function openScanner(page: Page) {
    await page.goto('/track')
    await page.getByRole('tab', { name: 'Scan', exact: true }).click()
    await page.getByRole('button', { name: 'Open camera' }).click()
    await expect(page.getByRole('status')).toHaveText('Hold the barcode inside the frame')
}

const fixtures: Fixture[] = [
    { name: 'ean13', code: '3017620422003' },
    { name: 'ean8', code: '96385074' },
    { name: 'upca', code: '012345678905' },
    { name: 'upce', code: '042100005264' },
    { name: 'itf14', code: '10012345000017' },
    { name: 'ean13', code: '3017620422003', angle: 90 },
    { name: 'ean13', code: '3017620422003', angle: 20, scale: 0.7 },
    { name: 'ean13', code: '3017620422003', blur: 0.7, contrast: 0.45 },
    { name: 'ean13', code: '3017620422003', offCentre: true },
]

for (const fixture of fixtures) {
    test(`real decoder reads ${JSON.stringify(fixture)}`, async ({ page }) => {
        await mockApi(page)
        await cameraFixture(page, fixture)
        await openScanner(page)
        // Blank frames must not produce a barcode.
        await expect(page.getByLabel('Enter barcode')).toHaveValue('')
        await page.evaluate(() => {
            document.documentElement.dataset.showBarcode = 'true'
        })
        await expect(page.getByLabel('Enter barcode')).toHaveValue(fixture.code)
        await expect(page.getByRole('button', { name: 'Create food manually' })).toBeVisible()
        const requests = await page.evaluate(
            () => Reflect.get(window, 'scannerRequests') as { url: string; body: string | null }[],
        )
        const imports = requests.filter((request) => request.url.includes('/barcodes/'))
        expect(imports).toHaveLength(1)
        expect(requests.every((request) => request.body === null)).toBe(true)
        expect(imports[0].url).toContain(`/barcodes/${fixture.code}/import`)
        await expect(page.locator('html')).toHaveAttribute('data-camera-stopped', 'true')
    })
}

test('detects locally offline and retains the code for retry', async ({ page, context }) => {
    await mockApi(page)
    await cameraFixture(page, fixtures[0])
    await openScanner(page)
    await context.setOffline(true)
    await page.evaluate(() => {
        document.documentElement.dataset.showBarcode = 'true'
    })
    await expect(page.getByLabel('Enter barcode')).toHaveValue(fixtures[0].code)
    await expect(page.getByRole('alert')).toContainText('Reconnect')
    await context.setOffline(false)
    await page.getByRole('button', { name: 'Retry lookup' }).click()
    await expect(page.getByRole('button', { name: 'Create food manually' })).toBeVisible()
})

test('production cache starts a fresh decoder while offline', async ({
    page,
    context,
    browserName,
}) => {
    test.skip(process.env.E2E_PREVIEW !== '1', 'Requires the production service worker')
    await mockApi(page)
    await cameraFixture(page, fixtures[0])
    await openScanner(page)
    await page.evaluate(async () => {
        await navigator.serviceWorker.ready
    })
    await expect
        .poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller)))
        .toBe(true)
    const assets = await page.evaluate(async () => {
        const stores = await Promise.all((await caches.keys()).map((name) => caches.open(name)))
        return (await Promise.all(stores.map((cache) => cache.keys())))
            .flat()
            .map((entry) => entry.url)
    })
    // The worker is embedded in the cached scanner route chunk for WebKit offline startup.
    expect(assets.some((url) => /\/scan-.*\.js/.test(url))).toBe(true)
    expect(assets.some((url) => /zxing_reader-.*\.wasm/.test(url))).toBe(true)
    await page.getByRole('button', { name: 'Stop camera' }).click()
    if (browserName === 'webkit') {
        // Playwright WebKit's setOffline also rejects a minimal, network-free blob
        // worker. Block actual HTTP traffic instead, and expose offline UI state.
        await context.route('**/*', (route) =>
            /^https?:/.test(route.request().url())
                ? route.abort('internetdisconnected')
                : route.continue(),
        )
        await page.evaluate(() => Object.defineProperty(navigator, 'onLine', { get: () => false }))
    } else {
        await context.setOffline(true)
    }
    await page.getByRole('button', { name: 'Open camera' }).click()
    await expect(page.getByRole('status')).toHaveText('Hold the barcode inside the frame')
    await page.evaluate(() => {
        document.documentElement.dataset.showBarcode = 'true'
    })
    await expect(page.getByLabel('Enter barcode')).toHaveValue(fixtures[0].code)
    await expect(page.getByRole('alert')).toContainText('Reconnect')
})

test('stops camera on close and starts a fresh session', async ({ page }) => {
    await mockApi(page)
    await cameraFixture(page, fixtures[0])
    await openScanner(page)
    await page.getByRole('button', { name: 'Stop camera' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-camera-stopped', 'true')
    await page.getByRole('button', { name: 'Open camera' }).click()
    await expect(page.getByRole('status')).toHaveText('Hold the barcode inside the frame')
    await page.evaluate(() => {
        document.documentElement.dataset.showBarcode = 'true'
    })
    await expect(page.getByLabel('Enter barcode')).toHaveValue(fixtures[0].code)
})
