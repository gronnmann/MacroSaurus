import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FoodForm } from '../components/food-form'
import { NutrientFacts } from '../components/nutrition'
import { ShareButton } from '../components/share'
import {
    Badge,
    Button,
    Card,
    ErrorPanel,
    Field,
    PageHeader,
    SectionHeader,
    Skeleton,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber, kcal, today } from '../lib/utils'
import type { Food, FoodInput } from '../types'

export function FoodDetailPage() {
    const { id = '' } = useParams()
    const food = useQuery({
        queryKey: queryKeys.food(id),
        queryFn: () => api.food(id),
    })
    const definitions = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
    })
    if (food.isLoading) return <Skeleton lines={8} />
    if (food.error || !food.data) return <ErrorPanel error={food.error} />
    const item = food.data
    return (
        <>
            <Link className="back-link" to="/track">
                <ArrowLeft />
                Back to Track
            </Link>
            <PageHeader
                eyebrow="FOOD"
                title={item.name}
                description={item.brand || 'No brand'}
                actions={
                    <div className="page-actions">
                        {item.source === 'USER' && (
                            <Link
                                className="button button--secondary"
                                to={`/foods/${item.id}/edit`}
                            >
                                <Pencil />
                                Edit
                            </Link>
                        )}
                        <ShareButton type="FOOD" revisionId={item.revisionId} label={item.name} />
                    </div>
                }
            />
            <div className="detail-grid">
                <Card>
                    <div className="detail-badges">
                        {item.barcode && <Badge>{item.barcode}</Badge>}
                        <Badge>per {formatNumber(item.basisAmount, item.basisUnit)}</Badge>
                    </div>
                    <NutrientFacts
                        nutrients={item.nutrients}
                        definitions={definitions.data}
                        title={`Per ${formatNumber(item.basisAmount, item.basisUnit)}`}
                    />
                </Card>
                <Card tone="green">
                    <SectionHeader eyebrow="ADD TO FOOD LOG" title="Choose an amount" />
                    <FoodLogger food={item} />
                    {item.portions.length > 0 && (
                        <div className="portion-list">
                            <h3>Portions</h3>
                            {item.portions.map((portion) => (
                                <div key={portion.id}>
                                    <b>{portion.name}</b>
                                    <span>
                                        {portion.gramWeight
                                            ? `${portion.gramWeight} g`
                                            : `${portion.milliliterVolume} ml`}
                                    </span>
                                    {portion.default && <Badge tone="green">default</Badge>}
                                </div>
                            ))}
                        </div>
                    )}
                </Card>
            </div>
        </>
    )
}

function FoodLogger({ food }: { food: Food }) {
    const [quantity, setQuantity] = useState(100)
    const [unit, setUnit] = useState('g')
    const [portionId, setPortionId] = useState<string>()
    const [meal, setMeal] = useState('OTHER')
    const toast = useToast()
    const client = useQueryClient()
    const request = {
        quantity,
        unit,
        portionId: unit === 'portion' ? portionId : null,
    }
    const preview = useQuery({
        queryKey: ['resolve', food.revisionId, request],
        queryFn: () => api.resolveFood(food.revisionId, request),
        enabled: quantity > 0 && (unit !== 'portion' || !!portionId),
    })
    const log = useMutation({
        mutationFn: () =>
            api.addFoodEntry({
                foodRevisionId: food.revisionId,
                ...request,
                localDate: today(),
                meal,
            }),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Food added to today', food.name)
        },
        onError: (error) => toast.push('Could not log food', error.message, 'error'),
    })
    return (
        <div className="logger">
            <div className="quantity-row">
                <input
                    aria-label="Quantity"
                    type="number"
                    min="0.000001"
                    step="any"
                    value={quantity}
                    onChange={(e) => setQuantity(Number(e.target.value))}
                />
                <select
                    aria-label="Unit"
                    value={unit}
                    onChange={(e) => {
                        setUnit(e.target.value)
                        setQuantity(e.target.value === 'portion' ? 1 : 100)
                    }}
                >
                    <option value="g">grams</option>
                    <option value="ml">millilitres</option>
                    {food.portions.length > 0 && <option value="portion">named portion</option>}
                </select>
            </div>
            {unit === 'portion' && (
                <Field label="Portion">
                    <select value={portionId || ''} onChange={(e) => setPortionId(e.target.value)}>
                        <option value="">Choose portion…</option>
                        {food.portions.map((p) => (
                            <option value={p.id} key={p.id}>
                                {p.name}
                            </option>
                        ))}
                    </select>
                </Field>
            )}
            <Field label="Meal">
                <select value={meal} onChange={(e) => setMeal(e.target.value)}>
                    <option>BREAKFAST</option>
                    <option>LUNCH</option>
                    <option>DINNER</option>
                    <option>SNACK</option>
                    <option>OTHER</option>
                </select>
            </Field>
            {preview.data && (
                <div className="preview-macros">
                    <strong>{kcal(preview.data.nutrients)} kcal</strong>
                    <span>
                        P {formatNumber(preview.data.nutrients.protein_g)} · C{' '}
                        {formatNumber(preview.data.nutrients.carbohydrate_g)} · F{' '}
                        {formatNumber(preview.data.nutrients.fat_g)}
                    </span>
                </div>
            )}
            {preview.error && <p className="field-error">{preview.error.message}</p>}
            <Button disabled={!preview.data || log.isPending} onClick={() => log.mutate()}>
                {log.isPending ? 'Adding…' : 'Add to today'}
            </Button>
        </div>
    )
}

export function FoodEditorPage() {
    const { id } = useParams()
    const navigate = useNavigate()
    const client = useQueryClient()
    const toast = useToast()
    const food = useQuery({
        queryKey: queryKeys.food(id || ''),
        queryFn: () => api.food(id || ''),
        enabled: !!id,
    })
    const definitions = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
    })
    const save = useMutation({
        mutationFn: (input: FoodInput) => (id ? api.updateFood(id, input) : api.createFood(input)),
        onSuccess: (result) => {
            client.invalidateQueries({ queryKey: ['foods'] })
            toast.push(id ? 'Changes saved' : 'Food created')
            navigate(`/foods/${result.id}`)
        },
        onError: (error) => toast.push('Could not save food', error.message, 'error'),
    })
    if (id && food.isLoading) return <Skeleton lines={8} />
    if (id && (food.error || food.data?.source !== 'USER'))
        return (
            <ErrorPanel error={food.error || new Error('Only foods you created can be edited.')} />
        )
    return (
        <>
            <Link className="back-link" to={id ? `/foods/${id}` : '/track'}>
                <ArrowLeft />
                Back
            </Link>
            <PageHeader
                eyebrow={id ? 'EDIT FOOD' : 'NEW FOOD'}
                title={id ? `Edit ${food.data?.name}` : 'Create a food'}
                description={
                    id
                        ? 'Past Food Log entries stay as they were recorded.'
                        : 'Add the values you know; the rest can stay empty.'
                }
            />
            <FoodForm
                key={food.data?.revisionId || 'new'}
                food={food.data}
                definitions={definitions.data}
                pending={save.isPending}
                submitLabel={id ? 'Save changes' : 'Create food'}
                onSubmit={(input) => save.mutate(input)}
            />
        </>
    )
}
