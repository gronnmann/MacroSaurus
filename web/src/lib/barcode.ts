export function barcodeError(code: string) {
    if (!/^\d{8}$|^\d{12,14}$/.test(code)) return 'Enter an 8, 12, 13, or 14 digit barcode.'
    const check = [...code.slice(0, -1)]
        .reverse()
        .reduce((sum, digit, index) => sum + Number(digit) * (index % 2 === 0 ? 3 : 1), 0)
    return (10 - (check % 10)) % 10 === Number(code.at(-1))
        ? ''
        : 'That barcode number is not valid. Try scanning again.'
}

// UPC-E has the UPC-A checksum, not the checksum of its eight printed digits.
export function normalizeDetection(text: string, format: string): string | undefined {
    // ZXing-C++ can return UPC values padded to an EAN-13 representation.
    let code =
        (format === 'UPCE' || format === 'EAN13') && /^0\d{12}$/.test(text) ? text.slice(1) : text
    if (format === 'EAN13' && code.length === 12) format = 'UPCA'
    if (format === 'UPCE' && code.length !== 12) {
        if (!/^[01]\d{7}$/.test(code)) return
        const [system, a, b, c, d, e, last, check] = code
        const body =
            last <= '2'
                ? `${a}${b}${last}0000${c}${d}${e}`
                : last === '3'
                  ? `${a}${b}${c}00000${d}${e}`
                  : last === '4'
                    ? `${a}${b}${c}${d}00000${e}`
                    : `${a}${b}${c}${d}${e}0000${last}`
        code = `${system}${body}${check}`
    } else {
        const lengths: Record<string, number> = {
            EAN8: 8,
            EAN13: 13,
            UPCA: 12,
            UPCE: 12,
            ITF: 14,
            ITF14: 14,
        }
        if (code.length !== lengths[format]) return
    }
    return barcodeError(code) ? undefined : code
}

// The video uses object-fit: contain. Map the guide to the displayed image,
// excluding letterboxing, then keep the original camera pixels in the crop.
export function scannerCrop(width: number, height: number) {
    const x = Math.floor(width * 0.1)
    const y = Math.floor(height * 0.2)
    return { x, y, width: width - 2 * x, height: height - 2 * y }
}

export type ScannerRequest = {
    type: 'decode'
    data: Uint8ClampedArray<ArrayBuffer>
    width: number
    height: number
    fullFrame: boolean
}

export type ScannerResponse =
    | { type: 'ready' }
    | { type: 'result'; code?: string }
    | { type: 'error'; message: string }
