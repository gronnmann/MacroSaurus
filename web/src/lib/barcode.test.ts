import { barcodeError, normalizeDetection, scannerCrop } from './barcode'

it.each([
    ['EAN13', '3017620422003', '3017620422003'],
    ['EAN8', '96385074', '96385074'],
    ['UPCA', '012345678905', '012345678905'],
    ['UPCE', '04252614', '042100005264'],
    ['UPCE', '042100005264', '042100005264'],
    ['UPCE', '0042100005264', '042100005264'],
    ['EAN13', '0012345678905', '012345678905'],
    ['ITF14', '10012345000017', '10012345000017'],
])('normalizes %s without losing leading zeros', (format, value, expected) => {
    expect(normalizeDetection(value, format)).toBe(expected)
    expect(barcodeError(expected)).toBe('')
})

it.each([
    ['EAN13', '3017620422004'],
    ['EAN8', 'abcdefgh'],
    ['UPCE', '04252615'],
    ['UPCE', '24252614'],
    ['ITF', '12345670'],
    ['QRCode', '3017620422003'],
])('rejects invalid or unsupported %s results', (format, value) => {
    expect(normalizeDetection(value, format)).toBeUndefined()
})

it('maps the guide to camera pixels in either orientation', () => {
    expect(scannerCrop(1920, 1080)).toEqual({ x: 192, y: 216, width: 1536, height: 648 })
    expect(scannerCrop(1080, 1920)).toEqual({ x: 108, y: 384, width: 864, height: 1152 })
})
