import { prepareZXingModule, type ReaderOptions, readBarcodes } from 'zxing-wasm/reader'
import wasmUrl from 'zxing-wasm/reader/zxing_reader.wasm?url'
import { normalizeDetection, type ScannerRequest, type ScannerResponse } from './barcode'

const send = (message: ScannerResponse) => self.postMessage(message)
async function prepareReader() {
    // An inline worker has a blob URL; root-relative fetches need an explicit origin.
    const url = new URL(wasmUrl, self.location.origin).href
    const cached =
        typeof caches === 'undefined'
            ? undefined
            : await caches.match(url, { ignoreSearch: true }).catch(() => undefined)
    return prepareZXingModule({
        overrides: {
            locateFile: (path: string, prefix: string) =>
                path.endsWith('.wasm') ? url : prefix + path,
            // Read the precache directly: worker fetches need not be controlled by
            // the document's service worker, particularly in WebKit.
            ...(cached ? { wasmBinary: await cached.arrayBuffer() } : {}),
        },
        fireImmediately: true,
    })
}
const ready = prepareReader()

ready.then(
    () => send({ type: 'ready' }),
    () => send({ type: 'error', message: 'Could not load the barcode reader. Please try again.' }),
)

self.onmessage = async (event: MessageEvent<ScannerRequest>) => {
    if (event.data.type !== 'decode') return
    try {
        await ready
        const { data, width, height, fullFrame } = event.data
        const pixels = new ImageData(data, width, height)
        const options: ReaderOptions = {
            formats: ['EAN8', 'EAN13', 'UPCA', 'UPCE', 'ITF'],
            tryRotate: true,
            tryHarder: fullFrame,
            tryInvert: fullFrame,
            maxNumberOfSymbols: 4,
        }
        let results = await readBarcodes(pixels, options)
        // Low-contrast bars on a bright background can defeat adaptive thresholding.
        // Keep this extra pass off the fast crop path.
        if (fullFrame && results.length === 0) {
            results = await readBarcodes(pixels, { ...options, binarizer: 'FixedThreshold' })
        }
        const codes = results
            .filter((result) => result.isValid)
            .map((result) => normalizeDetection(result.text, result.format))
            .filter((code): code is string => Boolean(code))
        // Do not arbitrarily choose a product if several are in the frame.
        const unique = [...new Set(codes)]
        send({ type: 'result', code: unique.length === 1 ? unique[0] : undefined })
    } catch {
        send({ type: 'error', message: 'The barcode reader stopped. Please try again.' })
    }
}
