import { Camera, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { z } from 'zod'
import { parseDecimal } from '../lib/utils'
import type { Food, FoodInput, NutrientDefinition } from '../types'
import { Button, Card, Field, SectionHeader } from './ui'

const schema = z.object({
    name: z.string().trim().min(1, 'Food name is required'),
    brand: z.string(),
    barcode: z.string(),
    basisType: z.enum(['PER_100_G', 'PER_100_ML', 'PER_SERVING']),
    densityGPerMl: z.number().positive().optional(),
    nutrients: z.record(z.string(), z.number().min(0)),
    portions: z.array(
        z.object({
            name: z.string().trim().min(1, 'Portion name is required'),
            equivalent: z.number().positive('Equivalent must be greater than zero'),
            unit: z.enum(['g', 'ml']),
        }),
    ),
})
type FoodFormValues = z.infer<typeof schema>

const decimalValue = (value: unknown) => {
    if (String(value ?? '').trim() === '') return undefined
    return parseDecimal(value)
}

export function FoodForm({
    food,
    definitions = [],
    submitLabel = 'Save food',
    pending,
    aiLabelEnabled = false,
    onLabelPhoto,
    onSubmit,
}: {
    food?: Food
    definitions?: NutrientDefinition[]
    submitLabel?: string
    pending?: boolean
    aiLabelEnabled?: boolean
    onLabelPhoto?: (file: File) => void
    onSubmit: (input: FoodInput) => void
}) {
    const defaults: FoodFormValues = {
        name: food?.name || '',
        brand: food?.brand || '',
        barcode: food?.barcode || '',
        basisType: food?.basisType || 'PER_100_G',
        densityGPerMl: food?.densityGPerMl,
        nutrients: food?.nutrients || {},
        portions:
            food?.portions.map((portion) => ({
                name: portion.name,
                equivalent: portion.gramWeight ?? portion.milliliterVolume ?? 1,
                unit: portion.gramWeight != null ? ('g' as const) : ('ml' as const),
            })) || [],
    }
    const {
        register,
        control,
        watch,
        handleSubmit,
        setError,
        formState: { errors },
    } = useForm<FoodFormValues>({ defaultValues: defaults })
    const portions = useFieldArray({ control, name: 'portions' })
    const [defaultPortionIndex, setDefaultPortionIndex] = useState(() => {
        const index = food?.portions.findIndex((portion) => portion.default) ?? -1
        return index >= 0 ? index : 0
    })
    const basis = watch('basisType')
    const submit = (values: FoodFormValues) => {
        const nutrients = Object.fromEntries(
            Object.entries(values.nutrients).filter(([, value]) => Number.isFinite(value)),
        )
        const result = schema.safeParse({ ...values, nutrients })
        if (!result.success) {
            result.error.issues.forEach((issue) => {
                setError(issue.path.join('.') as keyof FoodFormValues, { message: issue.message })
            })
            return
        }
        const basisAmount = result.data.basisType === 'PER_SERVING' ? 1 : 100
        const basisUnit =
            result.data.basisType === 'PER_100_G'
                ? 'g'
                : result.data.basisType === 'PER_100_ML'
                  ? 'ml'
                  : 'serving'
        onSubmit({
            name: result.data.name,
            brand: result.data.brand || null,
            barcode: result.data.barcode || null,
            basisType: result.data.basisType,
            basisAmount,
            basisUnit,
            densityGPerMl: result.data.densityGPerMl || null,
            nutrients: result.data.nutrients,
            portions: result.data.portions.map((portion, index) => ({
                name: portion.name,
                quantity: 1,
                gramWeight: portion.unit === 'g' ? portion.equivalent : null,
                milliliterVolume: portion.unit === 'ml' ? portion.equivalent : null,
                default: index === defaultPortionIndex,
            })),
        })
    }
    const macroDefs = definitions.filter(
        (item) =>
            item.category === 'MACRO' ||
            item.category === 'MACRONUTRIENT' ||
            item.code === 'energy_kcal',
    )
    const microDefs = definitions.filter((item) => !macroDefs.includes(item))
    const shownMacro = macroDefs.length
        ? macroDefs
        : [
              { code: 'energy_kcal', displayName: 'Energy', unit: 'kcal' },
              { code: 'protein_g', displayName: 'Protein', unit: 'g' },
              { code: 'carbohydrate_g', displayName: 'Carbohydrate', unit: 'g' },
              { code: 'fat_g', displayName: 'Fat', unit: 'g' },
              { code: 'fiber_g', displayName: 'Fiber', unit: 'g' },
          ]
    return (
        <form className="editor-form" onSubmit={handleSubmit(submit)}>
            {aiLabelEnabled && onLabelPhoto && (
                <Card tone="green">
                    <SectionHeader
                        eyebrow="AI ASSIST"
                        title="Fill from the nutrition label"
                        aside={<Camera />}
                    />
                    <p className="muted">
                        Take a clear photo, then review every extracted value before saving.
                    </p>
                    <label className="button button--secondary">
                        <Camera /> Take label photo
                        <input
                            hidden
                            type="file"
                            accept="image/jpeg,image/png,image/webp"
                            capture="environment"
                            onChange={(event) => {
                                const file = event.target.files?.[0]
                                if (file) onLabelPhoto(file)
                            }}
                        />
                    </label>
                </Card>
            )}
            <Card>
                <SectionHeader eyebrow="IDENTITY" title="What is it?" />
                <div className="form-grid">
                    <Field label="Food name" error={errors.name?.message} className="span-2">
                        <input {...register('name')} placeholder="Homemade granola" />
                    </Field>
                    <Field label="Brand" className="span-2">
                        <input {...register('brand')} placeholder="Optional" />
                    </Field>
                    <Field label="Barcode">
                        <input
                            {...register('barcode')}
                            inputMode="numeric"
                            placeholder="Optional EAN / UPC"
                        />
                    </Field>
                    <Field label="Nutrition values are shown">
                        <select {...register('basisType')}>
                            <option value="PER_100_G">Per 100 g</option>
                            <option value="PER_100_ML">Per 100 ml</option>
                            <option value="PER_SERVING">Per serving</option>
                        </select>
                    </Field>
                </div>
                {basis !== 'PER_SERVING' && (
                    <details className="micro-editor">
                        <summary>Advanced conversion</summary>
                        <Field
                            label="Density (g/ml)"
                            hint="Only needed to convert between weight and volume"
                        >
                            <input
                                type="text"
                                inputMode="decimal"
                                {...register('densityGPerMl', { setValueAs: decimalValue })}
                            />
                        </Field>
                    </details>
                )}
            </Card>
            <Card>
                <SectionHeader
                    eyebrow="NUTRITION"
                    title="Macros and energy"
                    aside="Unknown stays blank"
                />
                <div className="nutrient-editor">
                    {shownMacro.map((item) => (
                        <Field label={`${item.displayName} (${item.unit})`} key={item.code}>
                            <input
                                type="text"
                                inputMode="decimal"
                                {...register(`nutrients.${item.code}`, {
                                    setValueAs: decimalValue,
                                })}
                            />
                        </Field>
                    ))}
                </div>
                <details className="micro-editor">
                    <summary>
                        All micronutrients <span>{microDefs.length} available</span>
                    </summary>
                    <div className="nutrient-editor">
                        {microDefs.map((item) => (
                            <Field label={`${item.displayName} (${item.unit})`} key={item.code}>
                                <input
                                    type="text"
                                    inputMode="decimal"
                                    {...register(`nutrients.${item.code}`, {
                                        setValueAs: decimalValue,
                                    })}
                                />
                            </Field>
                        ))}
                    </div>
                </details>
            </Card>
            <Card>
                <SectionHeader
                    eyebrow="REAL-WORLD AMOUNTS"
                    title="Named portions"
                    aside={
                        <Button
                            type="button"
                            variant="secondary"
                            onClick={() => portions.append({ name: '', equivalent: 1, unit: 'g' })}
                        >
                            <Plus /> Add portion
                        </Button>
                    }
                />
                <p className="muted">
                    Add directly selectable sizes such as “1 pizza”, “1 spoon”, or “1 package”. The
                    selected default is offered first when tracking.
                </p>
                <div className="portion-editor">
                    {portions.fields.map((field, index) => (
                        <div className="portion-row" key={field.id}>
                            <Field
                                label="Portion name"
                                error={errors.portions?.[index]?.name?.message}
                            >
                                <input
                                    {...register(`portions.${index}.name`)}
                                    placeholder="1 spoon"
                                />
                            </Field>
                            <Field
                                label="Equivalent"
                                error={errors.portions?.[index]?.equivalent?.message}
                            >
                                <input
                                    type="text"
                                    inputMode="decimal"
                                    {...register(`portions.${index}.equivalent`, {
                                        setValueAs: parseDecimal,
                                    })}
                                />
                            </Field>
                            <Field label="Unit">
                                <select {...register(`portions.${index}.unit`)}>
                                    <option value="g">grams</option>
                                    <option value="ml">millilitres</option>
                                </select>
                            </Field>
                            <label className="check">
                                <input
                                    type="radio"
                                    name="defaultPortion"
                                    checked={defaultPortionIndex === index}
                                    onChange={() => setDefaultPortionIndex(index)}
                                />
                                Default
                            </label>
                            <Button
                                type="button"
                                variant="ghost"
                                aria-label="Remove portion"
                                onClick={() => {
                                    portions.remove(index)
                                    setDefaultPortionIndex((current) =>
                                        current > index
                                            ? current - 1
                                            : current === index
                                              ? 0
                                              : current,
                                    )
                                }}
                            >
                                <Trash2 />
                            </Button>
                        </div>
                    ))}
                </div>
            </Card>
            <div className="sticky-actions">
                <span>You can update portions and nutrition later.</span>
                <Button type="submit" disabled={pending}>
                    {pending ? 'Saving…' : submitLabel}
                </Button>
            </div>
        </form>
    )
}
