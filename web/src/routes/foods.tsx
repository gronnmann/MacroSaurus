import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { FoodForm } from '../components/food-form'
import { NutrientFacts } from '../components/nutrition'
import { ShareButton } from '../components/share'
import {
    Badge,
    Button,
    Card,
    ErrorPanel,
    PageHeader,
    SectionHeader,
    Skeleton,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { prepareLabelImage } from '../lib/image'
import { formatNumber, kcal, parseDecimal, today } from '../lib/utils'
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
                        <Badge>{sourceLabel(item.source)}</Badge>
                        {item.sourceRelease && <Badge>{item.sourceRelease}</Badge>}
                        {item.externalId && <Badge>source ID {item.externalId}</Badge>}
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
    const defaultPortion = food.portions.find((portion) => portion.default) || food.portions[0]
    const [quantity, setQuantity] = useState(String(defaultPortion ? 1 : food.basisAmount))
    const [unitChoice, setUnitChoice] = useState(
        defaultPortion ? `portion:${defaultPortion.id}` : defaultFoodUnit(food),
    )
    const toast = useToast()
    const client = useQueryClient()
    const numericQuantity = parseDecimal(quantity)
    const portionId = unitChoice.startsWith('portion:') ? unitChoice.slice(8) : null
    const request = {
        quantity: numericQuantity,
        unit: portionId ? 'portion' : unitChoice,
        portionId,
    }
    const preview = useQuery({
        queryKey: ['resolve', food.revisionId, request],
        queryFn: () => api.resolveFood(food.revisionId, request),
        enabled: numericQuantity > 0,
    })
    const log = useMutation({
        mutationFn: () =>
            api.addFoodEntry({
                foodRevisionId: food.revisionId,
                ...request,
                localDate: today(),
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
                    type="text"
                    inputMode="decimal"
                    value={quantity}
                    onChange={(e) => setQuantity(e.target.value)}
                />
                <select
                    aria-label="Unit"
                    value={unitChoice}
                    onChange={(e) => {
                        setUnitChoice(e.target.value)
                        setQuantity(
                            e.target.value.startsWith('portion:') ? '1' : String(food.basisAmount),
                        )
                    }}
                >
                    {food.basisType === 'PER_100_G' || food.densityGPerMl ? (
                        <option value="g">grams</option>
                    ) : null}
                    {food.basisType === 'PER_100_ML' || food.densityGPerMl ? (
                        <option value="ml">millilitres</option>
                    ) : null}
                    {food.basisType === 'PER_SERVING' ? (
                        <option value="serving">servings</option>
                    ) : null}
                    {food.portions.map((portion) => (
                        <option value={`portion:${portion.id}`} key={portion.id}>
                            {portion.name}
                        </option>
                    ))}
                </select>
            </div>
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
    const [searchParams] = useSearchParams()
    const barcode = id ? '' : searchParams.get('barcode') || ''
    const food = useQuery({
        queryKey: queryKeys.food(id || ''),
        queryFn: () => api.food(id || ''),
        enabled: !!id,
    })
    const definitions = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
    })
    const features = useQuery({ queryKey: queryKeys.features, queryFn: api.features })
    const scan = useMutation({
        mutationFn: async (file: File) =>
            api.startScan({
                image: await prepareLabelImage(file),
                barcode: barcode || null,
                localeHint: navigator.language,
            }),
        onSuccess: (job) => navigate(`/scan/${job.id}`),
        onError: (error) => toast.push('Could not read label', error.message, 'error'),
    })
    const save = useMutation({
        mutationFn: (input: FoodInput) => (id ? api.updateFood(id, input) : api.createFood(input)),
        onSuccess: (result) => {
            client.invalidateQueries({ queryKey: ['foods'] })
            toast.push(id ? 'Changes saved' : 'Food created')
            navigate(id ? `/foods/${result.id}` : `/track?food=${result.id}`)
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
                key={food.data?.revisionId || barcode || 'new'}
                food={
                    food.data ||
                    (barcode
                        ? {
                              id: '',
                              revisionId: '',
                              revision: 0,
                              name: '',
                              barcode,
                              source: 'USER',
                              basisType: 'PER_100_G',
                              basisAmount: 100,
                              basisUnit: 'g',
                              nutrients: {},
                              portions: [],
                              createdAt: '',
                          }
                        : undefined)
                }
                definitions={definitions.data}
                pending={save.isPending}
                aiLabelEnabled={Boolean(
                    !id &&
                        features.data?.aiLabelScan?.granted &&
                        features.data.aiLabelScan.available,
                )}
                onLabelPhoto={id ? undefined : (file) => scan.mutate(file)}
                submitLabel={id ? 'Save changes' : 'Create food'}
                onSubmit={(input) => save.mutate(input)}
            />
        </>
    )
}

function defaultFoodUnit(food: Food) {
    if (food.basisType === 'PER_100_ML') return 'ml'
    if (food.basisType === 'PER_SERVING') return 'serving'
    return 'g'
}

function sourceLabel(source: Food['source']) {
    if (source === 'MATVARETABELLEN') return 'Matvaretabellen'
    if (source === 'USDA_FOUNDATION') return 'USDA Foundation'
    if (source === 'USDA_SR_LEGACY' || source === 'USDA') return 'USDA SR Legacy'
    if (source === 'OPEN_FOOD_FACTS') return 'Open Food Facts'
    return 'Custom food'
}
