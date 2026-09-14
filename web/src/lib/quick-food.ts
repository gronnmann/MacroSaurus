import type { Nutrients } from '../types'
import { api } from './api'

export type QuickFoodInput = {
    name: string
    calories: number | null
    proteinG: number
    carbohydrateG: number
    fatG: number
    fiberG: number | null
    additionalNutrients?: Nutrients
    saveAsFood: boolean
}

export function createQuickFood(input: QuickFoodInput) {
    return api.createFood({
        name: input.name,
        basisType: 'PER_SERVING',
        basisAmount: 1,
        basisUnit: 'serving',
        nutrients: {
            ...input.additionalNutrients,
            energy_kcal:
                input.calories ?? input.proteinG * 4 + input.carbohydrateG * 4 + input.fatG * 9,
            protein_g: input.proteinG,
            carbohydrate_g: input.carbohydrateG,
            fat_g: input.fatG,
            ...(input.fiberG == null ? {} : { fiber_g: input.fiberG }),
        },
        portions: [],
    })
}
