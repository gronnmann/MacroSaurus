# Barcode scanning

The live barcode scanner runs ZXing-C++ WebAssembly in a dedicated worker. The worker is embedded in the cached scanner route bundle for reliable WebKit offline startup, and the app precaches its matching WASM binary; there is no CDN dependency at runtime. Camera frames are transferred only to the local worker. After detection, the existing import endpoint receives the barcode number.

The scanner requests a 1080p rear camera, processes at most 15 frames per second with one decode in flight, and uses full-resolution pixels from the guide area. Every third attempt checks the full frame with harder decoding and a fixed-threshold fallback for low contrast. The preview keeps the camera's aspect ratio so its guide corresponds to the decoding crop. UPC-E and zero-prefixed EAN representations of UPC-A normalize to UPC-A before lookup.

Camera selection is remembered on the device. Focus, torch and zoom depend on the capabilities the browser exposes. Closing, backgrounding or successfully scanning stops all camera tracks and destroys the worker, including when camera permission resolves late. Returning from the background requires Restart camera.

## Automated checks

From `web/`:

```sh
pnpm quality
pnpm test
pnpm build
pnpm exec playwright install chromium webkit
pnpm exec playwright test e2e/scanner.spec.ts
E2E_PREVIEW=1 pnpm exec playwright test e2e/scanner.spec.ts --grep 'production cache'
```

The browser suite uses generated barcode fixtures and a canvas camera stream with the real decoder worker. It checks all supported formats, rotation, small/off-centre symbols, a blur/contrast case, offline detection, one import request with no image payload, and stopping/reopening. The production test starts a fresh worker offline from the service-worker cache. WebKit's production offline test blocks HTTP requests and sets the offline UI state: Playwright's `setOffline` also rejects a minimal local blob worker in this engine. Physical airplane-mode validation remains pending. These checks do not simulate real camera focus, lens selection or sensor noise.

## Physical iPhone acceptance — pending

Compare the previous version and this version on an older/standard iPhone and a recent Pro. Test both Safari and the home-screen app, noting model and iOS version. Use 20 grocery products, three attempts each, in normal indoor light with no manual zoom. Include small barcodes, curved packaging and angled placement; record low-light and glare cases separately.

Measure camera startup separately from detection (first readable barcode in the preview to “Barcode found”) and online product loading. Use a high-frame-rate recording for detection timing. Target a median below 300 ms and 95% of ordinary scans below one second. Record failures and incorrect results as well as latency; a fast wrong result is a failure. Repeat after backgrounding and on each exposed rear lens. Check torch/zoom only where offered.

No physical-device speed or accuracy claim is established by the automated tests. Offline recognition requires a previous successful asset load/precache; product lookup and saving still require connectivity.
