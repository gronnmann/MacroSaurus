import { useQuery } from '@tanstack/react-query'
import type { FormEvent } from 'react'
import { api, queryKeys } from '../lib/api'
import { additionalNutrientFields, nutrientInputValue } from '../lib/nutrient-fields'
import { parseDecimal } from '../lib/utils'
import type { Nutrients } from '../types'
import { Button, Field } from './ui'

const core = [
    ['calories', 'energy_kcal', 'Calories'],
    ['fatG', 'fat_g', 'Fat (g)'],
    ['carbohydrateG', 'carbohydrate_g', 'Carbs (g)'],
    ['proteinG', 'protein_g', 'Protein (g)'],
] as const

function validate(event: FormEvent<HTMLInputElement>) {
    const input = event.currentTarget
    const amount = parseDecimal(input.value)
    input.setCustomValidity(
        input.value.trim() && (!Number.isFinite(amount) || amount < 0)
            ? 'Enter a number that is zero or greater.'
            : '',
    )
}

export function QuickNutrientFields({ nutrients = {} }: { nutrients?: Nutrients }) {
    const definitions = useQuery({ queryKey: queryKeys.nutrients, queryFn: api.nutrients })
    const fields = additionalNutrientFields(definitions.data)
    const rendered = new Set<string>([
        ...core.map(([, code]) => code),
        ...fields.map((field) => field.code),
    ])
    return (
        <>
            {core.map(([name, code, label]) => (
                <Field key={code} label={label}>
                    <input
                        name={name}
                        type="text"
                        inputMode="decimal"
                        onInput={validate}
                        defaultValue={nutrients[code] ?? (name === 'calories' ? '' : 0)}
                        placeholder={name === 'calories' ? 'Calculated if empty' : undefined}
                    />
                </Field>
            ))}
            {Object.entries(nutrients)
                .filter(([code]) => !rendered.has(code))
                .map(([code, value]) => (
                    <input
                        key={code}
                        type="hidden"
                        name={`nutrient.${code}`}
                        value={nutrientInputValue(code, value)}
                    />
                ))}
            <details
                className="micro-editor span-2"
                onInvalid={(event) => {
                    event.currentTarget.open = true
                }}
            >
                <summary>
                    More nutrients <span>Saturated fat, fiber, vitamins…</span>
                </summary>
                <div className="form-grid">
                    {fields.map((field) => (
                        <Field key={field.code} label={`${field.displayName} (${field.unit})`}>
                            <input
                                name={`nutrient.${field.code}`}
                                type="text"
                                inputMode="decimal"
                                onInput={validate}
                                defaultValue={
                                    nutrients[field.code] == null
                                        ? ''
                                        : nutrientInputValue(field.code, nutrients[field.code])
                                }
                            />
                        </Field>
                    ))}
                </div>
                {definitions.isError && (
                    <p className="field-error">
                        Could not load all nutrients.{' '}
                        <Button variant="ghost" onClick={() => definitions.refetch()}>
                            Try again
                        </Button>
                    </p>
                )}
            </details>
        </>
    )
}
