export async function prepareLabelImage(file: File): Promise<string> {
    if (file.size > 20_000_000) throw new Error('Choose an image smaller than 20 MB.')
    let bitmap: ImageBitmap | HTMLImageElement
    try {
        bitmap = await createImageBitmap(file)
    } catch {
        // Some browsers expose createImageBitmap but cannot decode every camera format.
        bitmap = await new Promise<HTMLImageElement>((resolve, reject) => {
            const image = new Image()
            const url = URL.createObjectURL(file)
            image.onload = () => {
                URL.revokeObjectURL(url)
                resolve(image)
            }
            image.onerror = () => {
                URL.revokeObjectURL(url)
                reject(new Error('This photo could not be opened. Try a JPEG, PNG, or WebP image.'))
            }
            image.src = url
        })
    }
    try {
        const maximum = 1_800
        const scale = Math.min(1, maximum / Math.max(bitmap.width, bitmap.height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(bitmap.width * scale))
        canvas.height = Math.max(1, Math.round(bitmap.height * scale))
        const context = canvas.getContext('2d')
        if (!context) throw new Error('This browser could not prepare the image.')
        context.fillStyle = '#fff'
        context.fillRect(0, 0, canvas.width, canvas.height)
        context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
        const result = canvas.toDataURL('image/jpeg', 0.86)
        if (!result.startsWith('data:image/jpeg;base64,') || result.length > 4_000_000) {
            throw new Error(
                'This photo is too large to send. Try cropping it or choosing a smaller image.',
            )
        }
        return result
    } finally {
        if ('close' in bitmap) bitmap.close()
    }
}
