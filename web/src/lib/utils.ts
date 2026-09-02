import type { Nutrients } from '../types'

export const localDate = (date = new Date()) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
}
export const today = () => localDate()
export const round = (value?: number, digits = 1) => Number((value ?? 0).toFixed(digits))
export const kcal = (nutrients?: Nutrients) => Math.round(nutrients?.energy_kcal ?? 0)
export const formatNumber = (value?: number, unit?: string) =>
    value == null
        ? '—'
        : `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value)}${unit ? ` ${unit}` : ''}`
export const formatDate = (value: string) =>
    new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value))
export const macroCodes = ['protein_g', 'carbohydrate_g', 'fat_g'] as const

/** Parses an ungrouped user-entered decimal using either comma or dot. */
export function parseDecimal(value: unknown): number {
    const raw = String(value ?? '').trim()
    if (
        !raw ||
        (raw.includes(',') && raw.includes('.')) ||
        !/^[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)$/.test(raw)
    ) {
        return Number.NaN
    }
    return Number(raw.replace(',', '.'))
}

export const optionalDecimal = (value: unknown) => {
    if (String(value ?? '').trim() === '') return undefined
    const parsed = parseDecimal(value)
    return Number.isFinite(parsed) ? parsed : undefined
}
