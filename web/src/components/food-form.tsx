import { Plus, Trash2 } from 'lucide-react'
import { useFieldArray, useForm } from 'react-hook-form'
import { z } from 'zod'
import type { Food, FoodInput, NutrientDefinition } from '../types'
import { Button, Card, Field, SectionHeader } from './ui'

const schema = z.object({
    name: z.string().trim().min(1, 'Food name is required'),
    brand: z.string(),
    barcode: z.string(),
    basisType: z.enum(['PER_100_G', 'PER_100_ML', 'PER_SERVING']),
    basisAmount: z.number().positive(),
    basisUnit: z.string().min(1),
    densityGPerMl: z.number().positive().optional(),
    nutrients: z.record(z.string(), z.number().min(0)),
    portions: z.array(
        z
            .object({
                name: z.string().min(1),
                quantity: z.number().positive(),
                gramWeight: z.number().positive().optional(),
                milliliterVolume: z.number().positive().optional(),
                default: z.boolean(),
            })
            .refine(
                (value) => value.gramWeight != null || value.milliliterVolume != null,
                'Add a gram or millilitre equivalent',
            ),
    ),
})
type FoodFormValues = z.infer<typeof schema>

export function FoodForm({
    food,
    definitions = [],
    submitLabel = 'Save food',
    pending,
    onSubmit,
}: {
    food?: Food
    definitions?: NutrientDefinition[]
    submitLabel?: string
    pending?: boolean
    onSubmit: (input: FoodInput) => void
}) {
    const defaults: FoodFormValues = {
        name: food?.name || '',
        brand: food?.brand || '',
        barcode: food?.barcode || '',
        basisType: food?.basisType || 'PER_100_G',
        basisAmount: food?.basisAmount || 100,
        basisUnit: food?.basisUnit || 'g',
        densityGPerMl: food?.densityGPerMl,
        nutrients: food?.nutrients || {},
        portions:
            food?.portions.map((p) => ({
                name: p.name,
                quantity: p.quantity,
                gramWeight: p.gramWeight,
                milliliterVolume: p.milliliterVolume,
                default: p.default,
            })) || [],
    }
    const {
        register,
        control,
        watch,
        handleSubmit,
        setError,
        setValue,
        formState: { errors },
    } = useForm<FoodFormValues>({ defaultValues: defaults })
    const portions = useFieldArray({ control, name: 'portions' })
    const basis = watch('basisType')
    const submit = (values: FoodFormValues) => {
        const nutrients = Object.fromEntries(
            Object.entries(values.nutrients).filter(([, value]) => Number.isFinite(value)),
        )
        const result = schema.safeParse({ ...values, nutrients })
        if (!result.success) {
            result.error.issues.forEach((issue) => {
                setError(issue.path.join('.') as keyof FoodFormValues, {
                    message: issue.message,
                })
            })
            return
        }
        onSubmit({
            ...result.data,
            brand: result.data.brand || null,
            barcode: result.data.barcode || null,
            densityGPerMl: result.data.densityGPerMl || null,
            portions: result.data.portions.map((p) => ({
                ...p,
                gramWeight: p.gramWeight || null,
                milliliterVolume: p.milliliterVolume || null,
            })),
        })
    }
    const macroDefs = definitions.filter(
        (item) => item.category === 'MACRONUTRIENT' || item.code === 'energy_kcal',
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
                    <Field label="Nutrition basis">
                        <select
                            {...register('basisType')}
                            onChange={(event) => {
                                register('basisType').onChange(event)
                                const next = event.target.value
                                setValue('basisAmount', next === 'PER_SERVING' ? 1 : 100)
                                setValue(
                                    'basisUnit',
                                    next === 'PER_100_G'
                                        ? 'g'
                                        : next === 'PER_100_ML'
                                          ? 'ml'
                                          : 'serving',
                                )
                            }}
                        >
                            <option value="PER_100_G">Per 100 g</option>
                            <option value="PER_100_ML">Per 100 ml</option>
                            <option value="PER_SERVING">Per serving</option>
                        </select>
                    </Field>
                    <Field label="Basis amount">
                        <input
                            type="number"
                            min="0.000001"
                            step="any"
                            {...register('basisAmount', { valueAsNumber: true })}
                        />
                    </Field>
                    <Field label="Basis unit">
                        <input {...register('basisUnit')} readOnly={basis !== 'PER_SERVING'} />
                    </Field>
                    <Field
                        label="Density (g/ml)"
                        hint="Only needed when converting mass and volume"
                    >
                        <input
                            type="number"
                            min="0"
                            step="any"
                            {...register('densityGPerMl', {
                                setValueAs: (value) => (value === '' ? undefined : Number(value)),
                            })}
                        />
                    </Field>
                </div>
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
                                type="number"
                                min="0"
                                step="any"
                                {...register(`nutrients.${item.code}`, {
                                    setValueAs: (value) =>
                                        value === '' ? undefined : Number(value),
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
                                    type="number"
                                    min="0"
                                    step="any"
                                    {...register(`nutrients.${item.code}`, {
                                        setValueAs: (value) =>
                                            value === '' ? undefined : Number(value),
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
                            onClick={() =>
                                portions.append({
                                    name: '',
                                    quantity: 1,
                                    default: portions.fields.length === 0,
                                })
                            }
                        >
                            <Plus />
                            Add portion
                        </Button>
                    }
                />
                {portions.fields.length === 0 ? (
                    <p className="muted">
                        Optional: add scoop, tablespoon, package, slice, or any amount people
                        actually use.
                    </p>
                ) : (
                    <div className="portion-editor">
                        {portions.fields.map((field, index) => (
                            <div className="portion-row" key={field.id}>
                                <Field label="Portion name">
                                    <input
                                        {...register(`portions.${index}.name`)}
                                        placeholder="scoop"
                                    />
                                </Field>
                                <Field label="Quantity">
                                    <input
                                        type="number"
                                        min="0.000001"
                                        step="any"
                                        {...register(`portions.${index}.quantity`, {
                                            valueAsNumber: true,
                                        })}
                                    />
                                </Field>
                                <Field label="Weight (g)">
                                    <input
                                        type="number"
                                        min="0"
                                        step="any"
                                        {...register(`portions.${index}.gramWeight`, {
                                            setValueAs: (value) =>
                                                value === '' ? undefined : Number(value),
                                        })}
                                    />
                                </Field>
                                <Field label="Volume (ml)">
                                    <input
                                        type="number"
                                        min="0"
                                        step="any"
                                        {...register(`portions.${index}.milliliterVolume`, {
                                            setValueAs: (value) =>
                                                value === '' ? undefined : Number(value),
                                        })}
                                    />
                                </Field>
                                <label className="check">
                                    <input
                                        type="checkbox"
                                        {...register(`portions.${index}.default`)}
                                    />
                                    Default
                                </label>
                                <Button
                                    type="button"
                                    variant="ghost"
                                    aria-label="Remove portion"
                                    onClick={() => portions.remove(index)}
                                >
                                    <Trash2 />
                                </Button>
                            </div>
                        ))}
                    </div>
                )}
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
