import { Camera, ImagePlus } from 'lucide-react'
import { useRef } from 'react'
import { Button } from './ui'

export function PhotoInput({
    onFiles,
    disabled = false,
    multiple = false,
    subject = 'photo',
}: {
    onFiles: (files: File[]) => void
    disabled?: boolean
    multiple?: boolean
    subject?: string
}) {
    const upload = useRef<HTMLInputElement>(null)
    const camera = useRef<HTMLInputElement>(null)
    const select = (input: HTMLInputElement) => {
        const files = Array.from(input.files || [])
        input.value = ''
        if (files.length) onFiles(files)
    }
    return (
        <div className="inline-actions">
            <Button variant="secondary" disabled={disabled} onClick={() => upload.current?.click()}>
                <ImagePlus /> Upload {subject}
            </Button>
            <Button variant="secondary" disabled={disabled} onClick={() => camera.current?.click()}>
                <Camera /> Take {subject}
            </Button>
            <input
                ref={upload}
                hidden
                type="file"
                aria-label={`Upload ${subject}`}
                accept="image/*"
                multiple={multiple}
                disabled={disabled}
                onChange={(event) => select(event.currentTarget)}
            />
            <input
                ref={camera}
                hidden
                type="file"
                aria-label={`Take ${subject}`}
                accept="image/*"
                capture="environment"
                disabled={disabled}
                onChange={(event) => select(event.currentTarget)}
            />
        </div>
    )
}
