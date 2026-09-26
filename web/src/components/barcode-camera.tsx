import { X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import {
    applyCameraOptions,
    type CameraCapabilities,
    type CameraOptions,
    cameraPreferenceKey,
    startBarcodeScanner,
} from '../lib/barcode-scanner'
import { Button } from './ui'

export function BarcodeCamera({
    onDetected,
    onClose,
}: {
    onDetected: (code: string) => void
    onClose: () => void
}) {
    const video = useRef<HTMLVideoElement>(null)
    const stop = useRef<(() => void) | undefined>(undefined)
    const track = useRef<MediaStreamTrack | undefined>(undefined)
    const [deviceId, setDeviceId] = useState(() => {
        try {
            return localStorage.getItem(cameraPreferenceKey) || undefined
        } catch {
            return undefined
        }
    })
    const [devices, setDevices] = useState<MediaDeviceInfo[]>([])
    const [activeDevice, setActiveDevice] = useState('')
    const [capabilities, setCapabilities] = useState<CameraCapabilities>({})
    const [torch, setTorch] = useState(false)
    const [zoom, setZoom] = useState(1)
    const [adjusting, setAdjusting] = useState(false)
    const [error, setError] = useState('')
    const [ready, setReady] = useState(false)
    const [attempt, setAttempt] = useState(0)
    const [paused, setPaused] = useState(false)

    // biome-ignore lint/correctness/useExhaustiveDependencies: Restart must create a new camera session.
    useEffect(() => {
        if (!video.current) return
        setReady(false)
        setError('')
        setPaused(false)
        setCapabilities({})
        setTorch(false)
        track.current = undefined
        stop.current = startBarcodeScanner({
            video: video.current,
            deviceId,
            onCamera: (value, cameras) => {
                track.current = value
                setDevices(cameras)
                setActiveDevice(value.getSettings().deviceId || '')
                setCapabilities(value.getCapabilities?.() ?? {})
                const settings = value.getSettings() as MediaTrackSettings & {
                    zoom?: number
                    torch?: boolean
                }
                setZoom(settings.zoom ?? 1)
                setTorch(settings.torch ?? false)
            },
            onReady: () => setReady(true),
            onDetected,
            onError: (message) => {
                setError(message)
                setReady(false)
            },
            onBackground: () => {
                setPaused(true)
                setReady(false)
            },
        })
        return () => {
            stop.current?.()
            track.current = undefined
        }
    }, [deviceId, attempt, onDetected])

    async function adjust(options: CameraOptions) {
        const current = track.current
        if (!current || adjusting) return
        setAdjusting(true)
        try {
            await applyCameraOptions(current, options)
            if (track.current !== current || current.readyState === 'ended') return
            const settings = current.getSettings() as MediaTrackSettings & {
                zoom?: number
                torch?: boolean
            }
            if (options.torch !== undefined) setTorch(settings.torch ?? options.torch)
            if (options.zoom !== undefined) setZoom(settings.zoom ?? options.zoom)
        } catch {
            // Unsupported constraints should never stop an otherwise working camera.
            if (track.current === current)
                setCapabilities((value) => ({
                    ...value,
                    ...(options.torch !== undefined ? { torch: false } : { zoom: undefined }),
                }))
        } finally {
            setAdjusting(false)
        }
    }

    return (
        <div className="camera-view">
            <div className="camera-preview">
                <video ref={video} muted playsInline aria-label="Barcode camera" />
                <div className="camera-guide" aria-hidden="true" />
            </div>
            <p role="status">
                {error ||
                    (paused
                        ? 'Camera paused. Tap Restart camera to scan again.'
                        : ready
                          ? 'Hold the barcode inside the frame'
                          : 'Starting camera and barcode reader…')}
            </p>
            <div className="camera-controls">
                {(error || paused) && (
                    <Button onClick={() => setAttempt((value) => value + 1)}>Restart camera</Button>
                )}
                {ready && devices.length > 1 && (
                    <label>
                        Camera
                        <select
                            aria-label="Camera"
                            value={activeDevice}
                            onChange={(event) => setDeviceId(event.target.value)}
                        >
                            {devices.map((device, index) => (
                                <option key={device.deviceId} value={device.deviceId}>
                                    {device.label || `Camera ${index + 1}`}
                                </option>
                            ))}
                        </select>
                    </label>
                )}
                {ready && capabilities.torch && (
                    <Button
                        variant="secondary"
                        aria-pressed={torch}
                        disabled={adjusting}
                        onClick={() => void adjust({ torch: !torch })}
                    >
                        Light {torch ? 'off' : 'on'}
                    </Button>
                )}
                {ready && capabilities.zoom && capabilities.zoom.max > capabilities.zoom.min && (
                    <label>
                        Zoom
                        <input
                            aria-label="Camera zoom"
                            type="range"
                            min={capabilities.zoom.min}
                            max={capabilities.zoom.max}
                            step={capabilities.zoom.step || 0.1}
                            value={zoom}
                            disabled={adjusting}
                            onChange={(event) => void adjust({ zoom: Number(event.target.value) })}
                        />
                    </label>
                )}
                <Button
                    variant="secondary"
                    onClick={() => {
                        stop.current?.()
                        onClose()
                    }}
                >
                    <X />
                    Stop camera
                </Button>
            </div>
        </div>
    )
}
