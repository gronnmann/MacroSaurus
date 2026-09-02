import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Pencil, Plus, Trash2 } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { DecimalInput } from '../components/decimal-input'
import { NutrientFacts } from '../components/nutrition'
import { ShareButton } from '../components/share'
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
import { formatNumber, kcal, parseDecimal, today } from '../lib/utils'
import type { Food, Recipe, RecipeInput } from '../types'

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
    const toast = useToast()
    const client = useQueryClient()
    const log = useMutation({
        mutationFn: () =>
            api.addRecipeEntry({
                recipeRevisionId: recipe.revisionId,
                servings,
                localDate: today(),
            }),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Recipe added to today', recipe.name)
        },
        onError: (error) => toast.push('Could not log recipe', error.message, 'error'),
    })
    return (
        <div className="logger">
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
            <Button onClick={() => log.mutate()} disabled={log.isPending}>
                Add to today
            </Button>
        </div>
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
    const [search, setSearch] = useState('')
    const foods = useQuery({
        queryKey: queryKeys.foods(search),
        queryFn: () => api.foods(search),
    })
    useEffect(() => {
        if (!recipe.data || ingredients.length) return
        const currentRecipe = recipe.data
        Promise.all(
            currentRecipe.ingredients.map(async (item) => {
                const found = (await api.foods(item.name, 100)).find(
                    (food) => food.revisionId === item.foodRevisionId,
                )
                const fallback: Food = {
                    id: item.foodRevisionId,
                    revisionId: item.foodRevisionId,
                    revision: 1,
                    name: item.name,
                    source: 'USER',
                    basisType: 'PER_100_G',
                    basisAmount: 100,
                    basisUnit: 'g',
                    nutrients: item.nutrients,
                    portions: [],
                    createdAt: currentRecipe.createdAt,
                }
                return {
                    key: item.id,
                    food: found || fallback,
                    quantity: item.quantity,
                    unit: item.unit,
                    portionId: item.portionId,
                }
            }),
        )
            .then(setIngredients)
            .catch(() => undefined)
    }, [recipe.data, ingredients.length])
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
                    <label className="search-field">
                        <Plus />
                        <input
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Find an ingredient…"
                        />
                    </label>
                    <div className="ingredient-results">
                        {foods.data?.slice(0, 6).map((food) => (
                            <button
                                type="button"
                                key={food.id}
                                onClick={() =>
                                    setIngredients((current) => [
                                        ...current,
                                        {
                                            key: crypto.randomUUID(),
                                            food,
                                            quantity: 100,
                                            unit: 'g',
                                        },
                                    ])
                                }
                            >
                                <span>
                                    <b>{food.name}</b>
                                    <small>{food.brand || 'No brand'}</small>
                                </span>
                                <Plus />
                            </button>
                        ))}
                    </div>
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
                                        <option value="g">g</option>
                                        <option value="ml">ml</option>
                                        {item.food.portions.map((portion) => (
                                            <option
                                                value={`portion:${portion.id}`}
                                                key={portion.id}
                                            >
                                                {portion.name}
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
                    <Button type="submit" disabled={!ingredients.length || save.isPending}>
                        {save.isPending ? 'Saving…' : id ? 'Save changes' : 'Create recipe'}
                    </Button>
                </div>
            </form>
        </>
    )
}
