import type { NutrientDefinition, Nutrients } from '../types'
import { parseDecimal } from './utils'

// EU Regulation 1169/2011, Annexes XIII and XV. Storage keeps sodium in mg.
export const labelNutrientOrder = [
    'energy_kcal',
    'fat_g',
    'saturated_fat_g',
    'monounsaturated_fat_g',
    'polyunsaturated_fat_g',
    'carbohydrate_g',
    'sugars_g',
    'polyols_g',
    'starch_g',
    'fiber_g',
    'protein_g',
    'sodium_mg',
    'vitamin_a_ug',
    'vitamin_d_ug',
    'vitamin_e_mg',
    'vitamin_k_ug',
    'vitamin_c_mg',
    'thiamin_mg',
    'riboflavin_mg',
    'niacin_mg',
    'vitamin_b6_mg',
    'folate_ug',
    'vitamin_b12_ug',
    'biotin_ug',
    'pantothenic_acid_mg',
    'potassium_mg',
    'chloride_mg',
    'calcium_mg',
    'phosphorus_mg',
    'magnesium_mg',
    'iron_mg',
    'zinc_mg',
    'copper_mg',
    'manganese_mg',
    'fluoride_mg',
    'selenium_ug',
    'chromium_ug',
    'molybdenum_ug',
    'iodine_ug',
]
export const labelMacroCodes = new Set(labelNutrientOrder.slice(0, 12))
const basicCodes = new Set(['energy_kcal', 'fat_g', 'carbohydrate_g', 'protein_g'])
const fallback: NutrientDefinition[] = [
    ['energy_kcal', 'Energy', 'kcal'],
    ['fat_g', 'Fat', 'g'],
    ['saturated_fat_g', 'Saturated fat', 'g'],
    ['carbohydrate_g', 'Carbohydrate', 'g'],
    ['sugars_g', 'Sugars', 'g'],
    ['fiber_g', 'Fiber', 'g'],
    ['protein_g', 'Protein', 'g'],
    ['sodium_mg', 'Sodium', 'mg'],
].map(([code, displayName, unit], sortOrder) => ({
    code,
    displayName,
    unit,
    sortOrder,
    category: 'MACRO',
}))

export function nutrientFields(definitions: NutrientDefinition[] = []) {
    const fields = new Map(fallback.map((field) => [field.code, field]))
    for (const field of definitions) fields.set(field.code, field)
    const rank = (code: string) => {
        const index = labelNutrientOrder.indexOf(code)
        return index < 0 ? labelNutrientOrder.length : index
    }
    return [...fields.values()]
        .sort(
            (a, b) =>
                rank(a.code) - rank(b.code) ||
                a.sortOrder - b.sortOrder ||
                a.code.localeCompare(b.code),
        )
        .map((field) =>
            field.code === 'sodium_mg'
                ? { ...field, displayName: 'Salt', unit: 'g' }
                : { ...field, unit: field.unit === 'ug' ? 'µg' : field.unit },
        )
}

export const nutrientInputValue = (code: string, value: number) =>
    code === 'sodium_mg' ? value / 400 : value
export const nutrientStoredValue = (code: string, value: number) =>
    code === 'sodium_mg' ? value * 400 : value
export const editableNutrients = (nutrients: Nutrients) =>
    Object.fromEntries(
        Object.entries(nutrients).map(([code, value]) => [code, nutrientInputValue(code, value)]),
    )
export const storedNutrients = (nutrients: Nutrients) =>
    Object.fromEntries(
        Object.entries(nutrients).map(([code, value]) => [code, nutrientStoredValue(code, value)]),
    )
export const additionalNutrientFields = (definitions?: NutrientDefinition[]) =>
    nutrientFields(definitions).filter((field) => !basicCodes.has(field.code))

export function readQuickNutrients(data: FormData) {
    const additionalNutrients: Nutrients = {}
    for (const [name, value] of data.entries()) {
        if (name.startsWith('nutrient.') && String(value).trim() !== '') {
            const code = name.slice('nutrient.'.length)
            additionalNutrients[code] = nutrientStoredValue(code, parseDecimal(value))
        }
    }
    const fiberG = additionalNutrients.fiber_g ?? null
    delete additionalNutrients.fiber_g
    return {
        calories: String(data.get('calories') ?? '').trim()
            ? parseDecimal(data.get('calories'))
            : null,
        fatG: parseDecimal(data.get('fatG') || 0),
        carbohydrateG: parseDecimal(data.get('carbohydrateG') || 0),
        proteinG: parseDecimal(data.get('proteinG') || 0),
        fiberG,
        additionalNutrients,
    }
}
