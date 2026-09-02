import { describe, expect, it } from 'vitest'
import { optionalDecimal, parseDecimal } from './utils'

describe('localized decimals', () => {
    it('accepts comma and dot decimal separators', () => {
        expect(parseDecimal('12,5')).toBe(12.5)
        expect(parseDecimal('12.5')).toBe(12.5)
    })

    it('does not interpret mixed or grouped separators', () => {
        expect(parseDecimal('1,234.5')).toBeNaN()
        expect(parseDecimal('1.234,5')).toBeNaN()
        expect(parseDecimal('+')).toBeNaN()
        expect(parseDecimal('-')).toBeNaN()
    })

    it('keeps optional blank values empty', () => {
        expect(optionalDecimal('')).toBeUndefined()
    })
})
