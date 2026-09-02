export async function prepareLabelImage(file: File): Promise<string> {
    if (file.size > 20_000_000) throw new Error('Choose an image smaller than 20 MB.')
    const bitmap =
        'createImageBitmap' in window
            ? await createImageBitmap(file)
            : await new Promise<HTMLImageElement>((resolve, reject) => {
                  const image = new Image()
                  const url = URL.createObjectURL(file)
                  image.onload = () => {
                      URL.revokeObjectURL(url)
                      resolve(image)
                  }
                  image.onerror = () => {
                      URL.revokeObjectURL(url)
                      reject(new Error('This browser could not decode the image.'))
                  }
                  image.src = url
              })
    const maximum = 1_800
    const scale = Math.min(1, maximum / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('This browser could not prepare the image.')
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    if ('ImageBitmap' in window && bitmap instanceof ImageBitmap) bitmap.close()
    return canvas.toDataURL('image/jpeg', 0.86)
}
