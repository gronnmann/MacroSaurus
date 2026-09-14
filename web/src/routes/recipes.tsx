import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Plus, Trash2 } from 'lucide-react'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DecimalInput } from '../components/decimal-input'
import { NutrientFacts } from '../components/nutrition'
import { ShareButton } from '../components/share'
import {
    initialTrackingWhen,
    TrackingWhenFields,
    trackingTimestamp,
} from '../components/tracking-when'
import {
    Button,
    Card,
    ErrorPanel,
    Field,
    PageHeader,
    SectionHeader,
    Skeleton,
    StatePanel,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber, kcal, parseDecimal } from '../lib/utils'
import type { Food, Recipe, RecipeInput } from '../types'
import { foodUnits, type IngredientSelection, TrackSheet } from './track'

export function RecipeDetailPage() {
    const { id = '' } = useParams()
    const recipe = useQuery({
        queryKey: queryKeys.recipe(id),
        queryFn: () => api.recipe(id),
    })
    const definitions = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
    })
    if (recipe.isLoading) return <Skeleton lines={8} />
    if (recipe.error || !recipe.data) return <ErrorPanel error={recipe.error} />
    const item = recipe.data
    return (
        <>
            <Link className="back-link" to="/track">
                <ArrowLeft />
                Back to Track
            </Link>
            <PageHeader
                eyebrow="RECIPE"
                title={item.name}
                description={`${formatNumber(item.servings)} servings`}
                actions={
                    <div className="page-actions">
                        <Link className="button button--secondary" to={`/recipes/${item.id}/edit`}>
                            <Pencil />
                            Edit
                        </Link>
                        <ShareButton type="RECIPE" revisionId={item.revisionId} label={item.name} />
                    </div>
                }
            />
            <div className="detail-grid">
                <Card>
                    <SectionHeader
                        eyebrow="PER SERVING"
                        title={`${kcal(item.nutrientsPerServing)} kcal`}
                    />
                    <NutrientFacts
                        nutrients={item.nutrientsPerServing}
                        definitions={definitions.data}
                    />
                </Card>
                <Card tone="green">
                    <SectionHeader eyebrow="ADD TO FOOD LOG" title="Choose servings" />
                    <RecipeLogger recipe={item} />
                    <div className="yield-stats">
                        <div>
                            <b>{formatNumber(item.explicitYieldG || item.estimatedYieldG, 'g')}</b>
                            <span>
                                {item.explicitYieldG ? 'finished weight' : 'estimated weight'}
                            </span>
                        </div>
                        <div>
                            <b>{formatNumber(item.servings)}</b>
                            <span>servings</span>
                        </div>
                    </div>
                </Card>
            </div>
            <Card>
                <SectionHeader
                    eyebrow="INGREDIENTS"
                    title="What goes in"
                    aside={`${item.ingredients.length} foods`}
                />
                <div className="ingredient-table">
                    {item.ingredients.map((ingredient) => (
                        <div key={ingredient.id}>
                            <b>{ingredient.name}</b>
                            <span>{formatNumber(ingredient.quantity, ingredient.unit)}</span>
                            <span>
                                {ingredient.resolvedGrams
                                    ? `${formatNumber(ingredient.resolvedGrams)} g`
                                    : 'by volume'}
                            </span>
                            <strong>{kcal(ingredient.nutrients)} kcal</strong>
                        </div>
                    ))}
                </div>
            </Card>
        </>
    )
}

function RecipeLogger({ recipe }: { recipe: Recipe }) {
    const [servings, setServings] = useState(1)
    const [when, setWhen] = useState(initialTrackingWhen)
    const toast = useToast()
    const client = useQueryClient()
    const log = useMutation({
        mutationFn: () =>
            api.addRecipeEntry({
                recipeRevisionId: recipe.revisionId,
                servings,
                ...trackingTimestamp(when),
            }),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Recipe added to Food Log', recipe.name)
        },
        onError: (error) => toast.push('Could not log recipe', error.message, 'error'),
    })
    return (
        <form
            className="logger"
            onSubmit={(event) => {
                event.preventDefault()
                log.mutate()
            }}
        >
            <Field label="Servings">
                <DecimalInput value={servings} onValue={(value) => setServings(value ?? 0)} />
            </Field>
            <div className="preview-macros">
                <strong>{Math.round(kcal(recipe.nutrientsPerServing) * servings)} kcal</strong>
                <span>
                    P {formatNumber(recipe.nutrientsPerServing.protein_g * servings)} · C{' '}
                    {formatNumber(recipe.nutrientsPerServing.carbohydrate_g * servings)} · F{' '}
                    {formatNumber(recipe.nutrientsPerServing.fat_g * servings)}
                </span>
            </div>
            <TrackingWhenFields value={when} onChange={setWhen} />
            <Button
                type="submit"
                disabled={log.isPending || !Number.isFinite(servings) || servings <= 0}
            >
                Add to Food Log
            </Button>
        </form>
    )
}

type DraftIngredient = {
    key: string
    food: Food
    quantity: number
    unit: string
    portionId?: string
}
export function RecipeEditorPage() {
    const { id } = useParams()
    const navigate = useNavigate()
    const toast = useToast()
    const client = useQueryClient()
    const recipe = useQuery({
        queryKey: queryKeys.recipe(id || ''),
        queryFn: () => api.recipe(id || ''),
        enabled: !!id,
    })
    const [ingredients, setIngredients] = useState<DraftIngredient[]>([])
    const [addingIngredient, setAddingIngredient] = useState(false)
    const initializedRecipe = useRef<string | undefined>(undefined)
    const [loadingIngredients, setLoadingIngredients] = useState(Boolean(id))
    const [ingredientError, setIngredientError] = useState<Error>()
    useEffect(() => {
        if (!recipe.data || initializedRecipe.current === recipe.data.revisionId) return
        let cancelled = false
        setLoadingIngredients(true)
        const currentRecipe = recipe.data
        Promise.all(
            currentRecipe.ingredients.map(async (item) => ({
                key: item.id,
                food: await api.foodRevision(item.foodRevisionId),
                quantity: item.quantity,
                unit: item.unit,
                portionId: item.portionId,
            })),
        )
            .then((items) => {
                if (!cancelled) {
                    initializedRecipe.current = currentRecipe.revisionId
                    setIngredients(items)
                    setLoadingIngredients(false)
                }
            })
            .catch((error) => {
                if (!cancelled) {
                    setIngredientError(error)
                    setLoadingIngredients(false)
                }
            })
        return () => {
            cancelled = true
        }
    }, [recipe.data])
    const addIngredients = (items: IngredientSelection[]) =>
        setIngredients((current) => [
            ...current,
            ...items.map((item) => ({ ...item, key: crypto.randomUUID() })),
        ])
    const save = useMutation({
        mutationFn: (input: RecipeInput) =>
            id ? api.updateRecipe(id, input) : api.createRecipe(input),
        onSuccess: (result) => {
            client.invalidateQueries({ queryKey: queryKeys.recipes })
            toast.push(id ? 'Changes saved' : 'Recipe created')
            navigate(`/recipes/${result.id}`)
        },
        onError: (error) => toast.push('Could not save recipe', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        save.mutate({
            name: String(data.get('name')),
            servings: parseDecimal(data.get('servings')),
            finishedWeightG: data.get('yield') ? parseDecimal(data.get('yield')) : null,
            ingredients: ingredients.map((item) => ({
                foodRevisionId: item.food.revisionId,
                quantity: item.quantity,
                unit: item.unit,
                portionId: item.unit === 'portion' ? item.portionId : null,
            })),
        })
    }
    if (id && recipe.isLoading) return <Skeleton lines={8} />
    if (id && (recipe.error || ingredientError))
        return <ErrorPanel error={recipe.error || ingredientError} />
    return (
        <>
            <Link className="back-link" to={id ? `/recipes/${id}` : '/track'}>
                <ArrowLeft />
                Back
            </Link>
            <PageHeader
                eyebrow="RECIPE BUILDER"
                title={id ? `Edit ${recipe.data?.name || 'recipe'}` : 'Create a recipe'}
                description="Add ingredients, then choose how many servings the finished batch makes."
            />
            <form className="editor-form" onSubmit={submit}>
                <Card>
                    <SectionHeader eyebrow="RECIPE" title="Details" />
                    <div className="form-grid">
                        <Field label="Recipe name" className="span-2">
                            <input
                                name="name"
                                required
                                defaultValue={recipe.data?.name}
                                placeholder="Sunday chilli"
                            />
                        </Field>
                        <Field label="Servings">
                            <input
                                name="servings"
                                type="text"
                                inputMode="decimal"
                                required
                                defaultValue={recipe.data?.servings || 4}
                            />
                        </Field>
                        <Field
                            label="Finished weight (g)"
                            hint="Optional; useful when tracking the recipe by grams"
                        >
                            <input
                                name="yield"
                                type="text"
                                inputMode="decimal"
                                defaultValue={recipe.data?.explicitYieldG}
                            />
                        </Field>
                    </div>
                </Card>
                <Card>
                    <SectionHeader
                        eyebrow="BATCH"
                        title="Ingredients"
                        aside={`${ingredients.length} added`}
                    />
                    <Button
                        type="button"
                        variant="secondary"
                        disabled={loadingIngredients}
                        onClick={() => setAddingIngredient(true)}
                    >
                        <Plus />
                        Add ingredient
                    </Button>
                    {loadingIngredients && <Skeleton lines={3} />}
                    {ingredients.length ? (
                        <div className="recipe-editor-list">
                            {ingredients.map((item, index) => (
                                <div key={item.key}>
                                    <b>{item.food.name}</b>
                                    <DecimalInput
                                        aria-label={`${item.food.name} quantity`}
                                        value={item.quantity}
                                        onValue={(value) =>
                                            setIngredients((current) =>
                                                current.map((entry, i) =>
                                                    i === index
                                                        ? {
                                                              ...entry,
                                                              quantity: value ?? 0,
                                                          }
                                                        : entry,
                                                ),
                                            )
                                        }
                                    />
                                    <select
                                        aria-label={`${item.food.name} unit`}
                                        value={
                                            item.portionId ? `portion:${item.portionId}` : item.unit
                                        }
                                        onChange={(e) =>
                                            setIngredients((current) =>
                                                current.map((entry, i) =>
                                                    i === index
                                                        ? {
                                                              ...entry,
                                                              unit: e.target.value.startsWith(
                                                                  'portion:',
                                                              )
                                                                  ? 'portion'
                                                                  : e.target.value,
                                                              portionId: e.target.value.startsWith(
                                                                  'portion:',
                                                              )
                                                                  ? e.target.value.slice(8)
                                                                  : undefined,
                                                          }
                                                        : entry,
                                                ),
                                            )
                                        }
                                    >
                                        {foodUnits(item.food).map((unit) => (
                                            <option value={unit.value} key={unit.value}>
                                                {unit.label}
                                            </option>
                                        ))}
                                    </select>
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        aria-label={`Remove ${item.food.name}`}
                                        onClick={() =>
                                            setIngredients((current) =>
                                                current.filter((entry) => entry.key !== item.key),
                                            )
                                        }
                                    >
                                        <Trash2 />
                                    </Button>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <StatePanel
                            compact
                            title="No ingredients yet"
                            message="Search and add at least one food."
                        />
                    )}
                </Card>
                <div className="sticky-actions">
                    <span>Past Food Log entries stay as recorded.</span>
                    <Button
                        type="submit"
                        disabled={
                            loadingIngredients ||
                            !ingredients.length ||
                            ingredients.some(
                                (item) => !Number.isFinite(item.quantity) || item.quantity <= 0,
                            ) ||
                            save.isPending
                        }
                    >
                        {save.isPending ? 'Saving…' : id ? 'Save changes' : 'Create recipe'}
                    </Button>
                </div>
            </form>
            {addingIngredient && (
                <TrackSheet
                    onClose={() => setAddingIngredient(false)}
                    onIngredients={addIngredients}
                />
            )}
        </>
    )
}
