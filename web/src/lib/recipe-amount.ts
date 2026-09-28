import type { Nutrients, Recipe } from '../types'

export type RecipeUnit = 'serving' | 'g'

export function recipeYieldG(recipe: Recipe): number | undefined {
    return recipe.explicitYieldG ?? recipe.estimatedYieldG
}

export function recipeUnits(recipe: Recipe): Array<{ value: RecipeUnit; label: string }> {
    const units: Array<{ value: RecipeUnit; label: string }> = [
        { value: 'serving', label: 'servings' },
    ]
    if (recipeYieldG(recipe)) units.push({ value: 'g', label: 'grams' })
    return units
}

export function recipeNutrients(
    recipe: Recipe,
    quantity: number,
    unit: string,
): Nutrients | undefined {
    if (unit === 'serving') return scaleNutrients(recipe.nutrientsPerServing, quantity)
    if (unit === 'g' && recipe.nutrientsPer100G)
        return scaleNutrients(recipe.nutrientsPer100G, quantity / 100)
    return undefined
}

export function recipeBatchFraction(
    recipe: Recipe,
    quantity: number,
    unit: string,
): number | undefined {
    if (unit === 'serving') return quantity / recipe.servings
    const yieldG = recipeYieldG(recipe)
    if (unit === 'g' && yieldG) return quantity / yieldG
    return undefined
}

function scaleNutrients(nutrients: Nutrients, factor: number): Nutrients {
    return Object.fromEntries(
        Object.entries(nutrients).map(([code, value]) => [code, value * factor]),
    )
}
