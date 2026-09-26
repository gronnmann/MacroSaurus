import { type ScannerRequest, type ScannerResponse, scannerCrop } from './barcode'
import BarcodeWorker from './barcode.worker?worker&inline'

export type CameraCapabilities = MediaTrackCapabilities & {
    focusMode?: string[]
    torch?: boolean
    zoom?: { min: number; max: number; step: number }
}
export type CameraOptions = { focusMode?: string; torch?: boolean; zoom?: number }
export const cameraPreferenceKey = 'macrosaurus.scan.camera'

export function applyCameraOptions(track: MediaStreamTrack, options: CameraOptions) {
    return track.applyConstraints({ advanced: [options as MediaTrackConstraintSet] })
}

export function startBarcodeScanner({
    video,
    deviceId,
    onCamera,
    onReady,
    onDetected,
    onError,
    onBackground,
}: {
    video: HTMLVideoElement
    deviceId?: string
    onCamera: (track: MediaStreamTrack, devices: MediaDeviceInfo[]) => void
    onReady: () => void
    onDetected: (code: string) => void
    onError: (message: string) => void
    onBackground: () => void
}) {
    let stopped = false
    let stream: MediaStream | undefined
    let worker: Worker | undefined
    let ready = false
    let playing = false
    let busy = false
    let frameId: number | undefined
    let lastAttempt = -Infinity
    let attempt = 0
    let timeout: ReturnType<typeof setTimeout> | undefined
    const canvas = document.createElement('canvas')
    const context = canvas.getContext('2d', { willReadFrequently: true })
    const videoFrames = typeof video.requestVideoFrameCallback === 'function'

    function stop() {
        if (stopped) return
        stopped = true
        clearTimeout(timeout)
        if (frameId !== undefined) {
            if (videoFrames) video.cancelVideoFrameCallback(frameId)
            else cancelAnimationFrame(frameId)
        }
        worker?.terminate()
        stream?.getTracks().forEach((track) => {
            track.stop()
        })
        video.srcObject = null
        canvas.width = canvas.height = 0
        document.removeEventListener('visibilitychange', background)
        window.removeEventListener('pagehide', background)
    }

    function fail(message: string) {
        if (stopped) return
        stop()
        onError(message)
    }

    function background(event: Event) {
        if (document.hidden || event.type === 'pagehide') {
            stop()
            onBackground()
        }
    }

    function schedule() {
        if (stopped || !ready || !playing || busy) return
        frameId = videoFrames
            ? video.requestVideoFrameCallback(capture)
            : requestAnimationFrame(capture)
    }

    function capture(now: number) {
        frameId = undefined
        if (stopped) return
        if (now - lastAttempt < 1000 / 15 || !video.videoWidth || video.readyState < 2) {
            schedule()
            return
        }
        try {
            if (!context) throw new Error('Canvas unavailable')
            lastAttempt = now
            const fullFrame = ++attempt % 3 === 0
            const crop = fullFrame
                ? { x: 0, y: 0, width: video.videoWidth, height: video.videoHeight }
                : scannerCrop(video.videoWidth, video.videoHeight)
            if (canvas.width !== crop.width) canvas.width = crop.width
            if (canvas.height !== crop.height) canvas.height = crop.height
            context.drawImage(
                video,
                crop.x,
                crop.y,
                crop.width,
                crop.height,
                0,
                0,
                crop.width,
                crop.height,
            )
            const { data } = context.getImageData(0, 0, crop.width, crop.height)
            const message: ScannerRequest = {
                type: 'decode',
                data,
                width: crop.width,
                height: crop.height,
                fullFrame,
            }
            busy = true
            timeout = setTimeout(
                () => fail('The barcode reader timed out. Please try again.'),
                10000,
            )
            worker?.postMessage(message, [data.buffer])
        } catch {
            fail('Could not read the camera image. Please try again.')
        }
    }

    function begin() {
        if (stopped || !ready || !playing) return
        onReady()
        schedule()
    }

    document.addEventListener('visibilitychange', background)
    window.addEventListener('pagehide', background)
    try {
        // Embed the worker in the cached route chunk so startup requires no extra
        // worker script request, including when the network is unavailable.
        worker = new BarcodeWorker()
        timeout = setTimeout(
            () => fail('Could not load the barcode reader. Please try again.'),
            15000,
        )
        worker.onerror = () => fail('Could not start the barcode reader. Please try again.')
        worker.onmessage = (event: MessageEvent<ScannerResponse>) => {
            if (stopped) return
            clearTimeout(timeout)
            const message = event.data
            if (message.type === 'error') return fail(message.message)
            if (message.type === 'ready') {
                ready = true
                begin()
            } else {
                busy = false
                if (message.code) {
                    stop()
                    onDetected(message.code)
                } else schedule()
            }
        }
    } catch {
        fail('This browser could not start the barcode reader. You can enter the number manually.')
        return stop
    }

    async function openCamera() {
        if (!navigator.mediaDevices?.getUserMedia) {
            fail(
                'Camera access is unavailable. Open the app over HTTPS or enter the barcode manually.',
            )
            return
        }
        try {
            try {
                stream = await navigator.mediaDevices.getUserMedia({
                    audio: false,
                    video: {
                        ...(deviceId
                            ? { deviceId: { exact: deviceId } }
                            : { facingMode: { ideal: 'environment' } }),
                        width: { ideal: 1920 },
                        height: { ideal: 1080 },
                        frameRate: { ideal: 30, max: 30 },
                    },
                })
            } catch (error) {
                if (stopped) return
                if (
                    !(error instanceof DOMException) ||
                    !['OverconstrainedError', 'NotFoundError'].includes(error.name)
                )
                    throw error
                stream = await navigator.mediaDevices.getUserMedia({
                    audio: false,
                    video: { facingMode: { ideal: 'environment' } },
                })
            }
            if (stopped) {
                stream.getTracks().forEach((track) => {
                    track.stop()
                })
                return
            }
            const track = stream.getVideoTracks()[0]
            track.addEventListener(
                'ended',
                () => fail('The camera disconnected. Please try again.'),
                { once: true },
            )
            const capabilities: CameraCapabilities = track.getCapabilities?.() ?? {}
            if (capabilities.focusMode?.includes('continuous')) {
                await applyCameraOptions(track, { focusMode: 'continuous' }).catch(() => {})
            }
            if (stopped) return
            video.muted = true
            video.playsInline = true
            video.srcObject = stream
            await video.play()
            if (stopped) return
            playing = true
            begin()
            // Device enumeration is optional and must not delay decoding.
            const devices = await navigator.mediaDevices.enumerateDevices().catch(() => [])
            if (stopped) return
            onCamera(
                track,
                devices.filter((device) => device.kind === 'videoinput'),
            )
            try {
                const selected = track.getSettings().deviceId
                if (selected) localStorage.setItem(cameraPreferenceKey, selected)
            } catch {
                /* Storage can be unavailable in private browsing. */
            }
        } catch (error) {
            const denied = error instanceof DOMException && error.name === 'NotAllowedError'
            fail(
                denied
                    ? 'Allow camera access in your browser settings, then try again.'
                    : 'Could not open the camera. Close other camera apps and try again.',
            )
        }
    }
    void openCamera()
    return stop
}
