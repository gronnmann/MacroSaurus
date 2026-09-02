import assert from 'node:assert/strict'
import test from 'node:test'

import { prepareMatvareRelease, prepareUsdaRelease } from './prepare-catalog-release.mjs'

test('prepares Matvaretabellen English foods with Norwegian aliases and named portions', () => {
    const release = prepareMatvareRelease(
        {
            foods: [
                {
                    foodId: '06.178',
                    foodName: 'Adzuki beans, uncooked',
                    calories: { quantity: 310, unit: 'kcal' },
                    constituents: [
                        { nutrientId: 'Protein', quantity: 20.1, unit: 'g' },
                        { nutrientId: 'NaCl', quantity: 0.1, unit: 'g' },
                        { nutrientId: 'Vit D', quantity: 2, unit: 'µg' },
                    ],
                    portions: [{ portionName: 'decilitre', quantity: 85, unit: 'g' }],
                },
            ],
        },
        { foods: [{ foodId: '06.178', foodName: 'Adzukibønner, tørre' }] },
        '2026',
    )

    assert.equal(release.source, 'MATVARETABELLEN')
    assert.deepEqual(release.foods[0].aliases, { nb: 'Adzukibønner, tørre' })
    assert.deepEqual(release.foods[0].nutrients, {
        energy_kcal: 310,
        protein_g: 20.1,
        vitamin_d_ug: 2,
        sodium_mg: 40,
    })
    assert.deepEqual(release.foods[0].portions, [{ name: 'decilitre', gramWeight: 85, default: true }])
})

test('prepares USDA Foundation foods and uses stable nutrient ids', () => {
    const release = prepareUsdaRelease(
        {
            FoundationFoods: [
                {
                    fdcId: 123,
                    description: 'Example food',
                    foodNutrients: [
                        { nutrient: { id: 1008, name: 'Energy', unitName: 'KCAL' }, amount: 89 },
                        { nutrient: { id: 1003, name: 'Protein', unitName: 'G' }, amount: 1.09 },
                        { nutrient: { id: 1103, name: 'Selenium, Se', unitName: 'UG' }, amount: 0.5 },
                    ],
                    foodPortions: [
                        { amount: 0.5, gramWeight: 118, modifier: 'medium banana', measureUnit: { name: 'portion' } },
                    ],
                },
            ],
        },
        'USDA_FOUNDATION',
        '2026-04',
    )

    assert.equal(release.foods[0].externalId, '123')
    assert.deepEqual(release.foods[0].nutrients, { energy_kcal: 89, protein_g: 1.09, selenium_ug: 0.5 })
    assert.deepEqual(release.foods[0].portions, [{ name: '0.5 medium banana', gramWeight: 118, default: true }])
})

test('ignores null placeholders in USDA exports', () => {
    const release = prepareUsdaRelease(
        {
            FoundationFoods: [{ fdcId: 123, description: 'Example food' }, null],
        },
        'USDA_FOUNDATION',
        '2026-04',
    )

    assert.equal(release.foods.length, 1)
    assert.equal(release.foods[0].externalId, '123')
})

test('rejects a USDA dataset that does not match the selected source', () => {
    assert.throws(
        () => prepareUsdaRelease({ BrandedFoods: [] }, 'USDA_FOUNDATION', '2026-04'),
        /must contain FoundationFoods/,
    )
})
