import type { ScannerResponse } from './barcode'
import { startBarcodeScanner } from './barcode-scanner'

const { FakeWorker } = vi.hoisted(() => {
    class FakeWorker {
        static latest: FakeWorker
        onmessage?: (event: { data: ScannerResponse }) => void
        onerror?: () => void
        postMessage = vi.fn()
        terminate = vi.fn()
        constructor() {
            FakeWorker.latest = this
        }
        emit(data: ScannerResponse) {
            this.onmessage?.({ data })
        }
    }

    return { FakeWorker }
})
vi.mock('./barcode.worker?worker&inline', () => ({ default: FakeWorker }))

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((done) => {
        resolve = done
    })
    return { promise, resolve }
}

function setup() {
    const track = {
        stop: vi.fn(),
        addEventListener: vi.fn(),
        getCapabilities: () => ({}),
        getSettings: () => ({ deviceId: 'rear' }),
    }
    const stream = {
        getTracks: () => [track],
        getVideoTracks: () => [track],
    } as unknown as MediaStream
    const getUserMedia = vi.fn().mockResolvedValue(stream)
    vi.stubGlobal('navigator', {
        mediaDevices: { getUserMedia, enumerateDevices: vi.fn().mockResolvedValue([]) },
    })
    const video = document.createElement('video')
    Object.defineProperties(video, {
        videoWidth: { value: 1920 },
        videoHeight: { value: 1080 },
        readyState: { value: 2 },
    })
    video.play = vi.fn().mockResolvedValue(undefined)
    let nextFrame: FrameRequestCallback | undefined
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
        nextFrame = callback
        return 1
    })
    const cancel = vi.fn(() => {
        nextFrame = undefined
    })
    vi.stubGlobal('cancelAnimationFrame', cancel)
    const detected = vi.fn(),
        error = vi.fn(),
        background = vi.fn()
    const start = () =>
        startBarcodeScanner({
            video,
            onCamera: vi.fn(),
            onReady: vi.fn(),
            onDetected: detected,
            onError: error,
            onBackground: background,
        })
    const frame = (time: number) => {
        const callback = nextFrame
        nextFrame = undefined
        callback?.(time)
    }
    return { track, stream, video, getUserMedia, detected, error, background, start, frame, cancel }
}

beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('Worker', FakeWorker)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
        drawImage: vi.fn(),
        getImageData: () => ({ data: new Uint8ClampedArray(4) }),
    } as unknown as CanvasRenderingContext2D)
})
afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
})

it('stops a camera that resolves after cancellation', async () => {
    const s = setup(),
        pending = deferred<MediaStream>()
    s.getUserMedia.mockReturnValue(pending.promise)
    const stop = s.start()
    stop()
    pending.resolve(s.stream)
    await vi.advanceTimersByTimeAsync(0)
    expect(s.track.stop).toHaveBeenCalledOnce()
    expect(s.video.play).not.toHaveBeenCalled()
    expect(FakeWorker.latest.terminate).toHaveBeenCalledOnce()
})

it('allows one frame in flight, throttles attempts and checks every third full frame', async () => {
    const s = setup(),
        stop = s.start(),
        worker = FakeWorker.latest
    worker.emit({ type: 'ready' })
    await vi.advanceTimersByTimeAsync(0)
    s.frame(0)
    s.frame(100)
    expect(worker.postMessage).toHaveBeenCalledTimes(1)
    worker.emit({ type: 'result' })
    s.frame(20)
    expect(worker.postMessage).toHaveBeenCalledTimes(1)
    s.frame(80)
    worker.emit({ type: 'result' })
    s.frame(160)
    expect(worker.postMessage.mock.calls.map(([message]) => message.fullFrame)).toEqual([
        false,
        false,
        true,
    ])
    worker.emit({ type: 'result', code: '3017620422003' })
    worker.emit({ type: 'result', code: '3017620422003' })
    expect(s.detected).toHaveBeenCalledOnce()
    expect(s.track.stop).toHaveBeenCalledOnce()
    expect(s.video.srcObject).toBeNull()
    stop()
})

it('stops decoding on backgrounding and ignores late results', async () => {
    const s = setup(),
        stop = s.start()
    await vi.advanceTimersByTimeAsync(0)
    window.dispatchEvent(new Event('pagehide'))
    FakeWorker.latest.emit({ type: 'result', code: '3017620422003' })
    expect(s.background).toHaveBeenCalledOnce()
    expect(s.detected).not.toHaveBeenCalled()
    expect(s.track.stop).toHaveBeenCalledOnce()
    stop()
})

it('releases camera resources on worker initialization failure', async () => {
    const s = setup(),
        stop = s.start()
    await vi.advanceTimersByTimeAsync(0)
    FakeWorker.latest.emit({ type: 'error', message: 'Could not load decoder' })
    expect(s.error).toHaveBeenCalledWith('Could not load decoder')
    expect(s.track.stop).toHaveBeenCalledOnce()
    stop()
})

it('falls back from an unavailable saved camera', async () => {
    const s = setup()
    s.getUserMedia.mockRejectedValueOnce(new DOMException('missing', 'OverconstrainedError'))
    const stop = s.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(s.getUserMedia).toHaveBeenCalledTimes(2)
    expect(s.getUserMedia).toHaveBeenLastCalledWith({
        audio: false,
        video: { facingMode: { ideal: 'environment' } },
    })
    stop()
})

it('does not retry permission denial', async () => {
    const s = setup()
    s.getUserMedia.mockRejectedValueOnce(new DOMException('denied', 'NotAllowedError'))
    const stop = s.start()
    await vi.advanceTimersByTimeAsync(0)
    expect(s.getUserMedia).toHaveBeenCalledOnce()
    expect(s.error).toHaveBeenCalledWith(expect.stringContaining('Allow camera access'))
    stop()
})
