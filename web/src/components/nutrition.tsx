import { formatNumber, kcal } from '../lib/utils'
import type { NutrientDefinition, Nutrients } from '../types'
import { Badge } from './ui'

const macros = [
    { code: 'protein_g', label: 'Protein', color: 'green' },
    { code: 'carbohydrate_g', label: 'Carbs', color: 'orange' },
    { code: 'fat_g', label: 'Fat', color: 'teal' },
] as const

export function MacroSummary({
    nutrients,
    targets = {},
}: {
    nutrients?: Nutrients
    targets?: Nutrients
}) {
    const energy = kcal(nutrients)
    return (
        <div className="macro-summary">
            <div className="energy-card">
                <span>Energy logged</span>
                <strong>{energy.toLocaleString()}</strong>
                <small>kcal</small>
                <Progress value={energy} max={targets.energy_kcal || 2200} color="dark" />
                <em>A total, not a verdict.</em>
            </div>
            {macros.map((item) => (
                <div className={`macro-tile macro-tile--${item.color}`} key={item.code}>
                    <span>{item.label}</span>
                    <strong>{formatNumber(nutrients?.[item.code])}</strong>
                    <small>grams</small>
                    <Progress
                        value={nutrients?.[item.code] || 0}
                        max={targets[item.code] || 150}
                        color={item.color}
                    />
                </div>
            ))}
        </div>
    )
}

export function Progress({
    value,
    max,
    color = 'green',
}: {
    value: number
    max: number
    color?: string
}) {
    const percentage = Math.min(100, max > 0 ? (value / max) * 100 : 0)
    return (
        <div
            className={`progress progress--${color}`}
            role="progressbar"
            aria-valuenow={value}
            aria-valuemin={0}
            aria-valuemax={max}
        >
            <i style={{ width: `${percentage}%` }} />
        </div>
    )
}

export function NutrientFacts({
    nutrients,
    definitions,
    targets = {},
    title = 'Nutrition',
}: {
    nutrients: Nutrients
    definitions?: NutrientDefinition[]
    targets?: Record<string, number | undefined>
    title?: string
}) {
    const fallback = Object.keys(nutrients).map((code, index) => ({
        code,
        displayName: code.replaceAll('_', ' '),
        category: code.includes('_g') ? 'MACRONUTRIENT' : 'OTHER',
        unit: code.endsWith('_g') ? 'g' : '',
        sortOrder: index,
    }))
    const rows = (definitions?.length ? definitions : fallback).filter(
        (item) => nutrients[item.code] != null,
    )
    const groups = rows.reduce<Record<string, typeof rows>>((result, item) => {
        const group = result[item.category] ?? []
        group.push(item)
        result[item.category] = group
        return result
    }, {})
    return (
        <div className="nutrient-facts">
            <div className="nutrient-facts__title">
                <h3>{title}</h3>
                <Badge tone="dark">Nutrition details</Badge>
            </div>
            {Object.entries(groups).map(([category, items]) => (
                <section key={category}>
                    <h4>{category.toLowerCase().replaceAll('_', ' ')}</h4>
                    {items.map((item) => {
                        const target = targets[item.code]
                        const amount = nutrients[item.code]
                        return (
                            <div className="nutrient-row" key={item.code}>
                                <div>
                                    <b>{item.displayName}</b>
                                    <span>{formatNumber(amount, item.unit)}</span>
                                </div>
                                {target != null && (
                                    <Progress value={amount} max={target} color="green" />
                                )}
                            </div>
                        )
                    })}
                </section>
            ))}
        </div>
    )
}
