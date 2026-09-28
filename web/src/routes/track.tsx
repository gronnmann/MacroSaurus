import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
    Beef,
    BookOpen,
    Camera,
    ChevronRight,
    MoreHorizontal,
    Pencil,
    Scale,
    Search,
    Sparkles,
    Utensils,
    X,
    Zap,
} from 'lucide-react'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { FoodForm } from '../components/food-form'
import { Brand } from '../components/layout'
import { QuickNutrientFields } from '../components/quick-nutrient-fields'
import {
    initialTrackingWhen,
    type TrackingWhen,
    TrackingWhenFields,
    trackingTimestamp,
} from '../components/tracking-when'
import { Button, Field, Skeleton, StatePanel, useToast } from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { readQuickNutrients } from '../lib/nutrient-fields'
import { createQuickFood, type QuickFoodInput } from '../lib/quick-food'
import { recipeBatchFraction, recipeNutrients, recipeUnits } from '../lib/recipe-amount'
import { formatNumber, kcal, parseDecimal } from '../lib/utils'
import type { AddRecipeEntryInput, Food, FoodInput, Nutrients, Trackable } from '../types'
import { MealEstimateEntry } from './meal-estimate'
import { ScanExperience } from './scan'

type TrackMode = 'search' | 'scan' | 'quick' | 'more' | 'weight' | 'estimate' | 'create'
type TopTrackMode = Exclude<TrackMode, 'weight' | 'estimate' | 'create'>

export function TrackPage() {
    const location = useLocation()
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const requestedFoodId = searchParams.get('food') || ''
    const from = (location.state as { from?: string } | null)?.from || '/dashboard'
    const requestedDate =
        searchParams.get('date') ||
        new URL(from, 'https://local').searchParams.get('date') ||
        undefined
    return (
        <TrackSheet
            initialFoodId={requestedFoodId}
            initialDate={requestedDate}
            onClose={() => navigate(from, { replace: true })}
        />
    )
}

export type IngredientSelection = { food: Food; quantity: number; unit: string; portionId?: string }

export function TrackSheet({
    initialFoodId = '',
    initialDate,
    onClose: close,
    onIngredients,
}: {
    initialFoodId?: string
    initialDate?: string
    onClose: () => void
    onIngredients?: (ingredients: IngredientSelection[]) => void
}) {
    const client = useQueryClient()
    const [mode, setMode] = useState<TrackMode>('search')
    const [when, setWhen] = useState(() => initialTrackingWhen(initialDate))
    const [selected, setSelected] = useState<{ item: Trackable; origin: 'search' | 'scan' }>()
    const requestedFood = useQuery({
        queryKey: queryKeys.food(initialFoodId),
        queryFn: () => api.food(initialFoodId),
        enabled: Boolean(initialFoodId),
    })
    useEffect(() => {
        if (requestedFood.data)
            setSelected({ item: trackableFood(requestedFood.data), origin: 'search' })
    }, [requestedFood.data])
    const selectFood = (food: Food) => {
        client.setQueryData(['food-revision', food.revisionId], food)
        client.invalidateQueries({ queryKey: ['trackables'] })
        client.invalidateQueries({ queryKey: ['foods'] })
        setSelected({ item: trackableFood(food), origin: 'scan' })
    }
    useEffect(() => {
        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        return () => {
            document.body.style.overflow = previousOverflow
        }
    }, [])
    return createPortal(
        <div className="track-overlay">
            <button
                type="button"
                className="track-backdrop"
                aria-label="Close Track"
                onClick={close}
            />
            <section
                className="track-sheet"
                role="dialog"
                aria-modal="true"
                aria-labelledby="track-title"
            >
                <header className="track-sheet-header">
                    <div className="track-sheet-brand-row">
                        <Brand />
                        <button
                            type="button"
                            className="sheet-close"
                            onClick={close}
                            aria-label="Close Track"
                        >
                            <X />
                        </button>
                    </div>
                    <div className="track-sheet-title">
                        <p className="eyebrow">QUICK ACTION</p>
                        <h1 id="track-title">
                            {selected
                                ? 'Choose amount'
                                : onIngredients && mode === 'search'
                                  ? 'Add ingredient'
                                  : trackTitle(mode)}
                        </h1>
                    </div>
                </header>
                {selected ? (
                    <AmountForm
                        key={selected.item.revisionId}
                        item={selected.item}
                        when={when}
                        onWhenChange={setWhen}
                        onIngredients={onIngredients}
                        backLabel={selected.origin === 'scan' ? 'Scan barcode' : 'Search results'}
                        onBack={() => setSelected(undefined)}
                        onDone={close}
                    />
                ) : (
                    <>
                        {mode !== 'weight' && mode !== 'estimate' && mode !== 'create' && (
                            <TrackTabs mode={mode} onMode={(next) => setMode(next)} />
                        )}
                        {mode === 'search' && (
                            <TrackSearch
                                onCreate={() => setMode('create')}
                                onSelect={(item) => setSelected({ item, origin: 'search' })}
                            />
                        )}
                        {mode === 'quick' && (
                            <QuickEntry
                                onDone={close}
                                when={when}
                                onWhenChange={setWhen}
                                onFoodReady={onIngredients ? selectFood : undefined}
                            />
                        )}
                        {mode === 'scan' && <ScanExperience embedded onFoodReady={selectFood} />}
                        {mode === 'more' && (
                            <TrackMore
                                onWeight={onIngredients ? undefined : () => setMode('weight')}
                                allowRecipeCreation={!onIngredients}
                                onCreate={() => setMode('create')}
                                onEstimate={() => setMode('estimate')}
                            />
                        )}
                        {mode === 'estimate' && (
                            <>
                                <button
                                    type="button"
                                    className="sheet-back"
                                    onClick={() => setMode('more')}
                                >
                                    ← More tracking options
                                </button>
                                <MealEstimateEntry
                                    onDone={close}
                                    when={when}
                                    onWhenChange={setWhen}
                                    onFoodReady={onIngredients ? selectFood : undefined}
                                />
                            </>
                        )}
                        {mode === 'create' && (
                            <CreateFoodEntry
                                onFoodReady={selectFood}
                                onBack={() => setMode('more')}
                            />
                        )}
                        {mode === 'weight' && (
                            <>
                                <button
                                    type="button"
                                    className="sheet-back"
                                    onClick={() => setMode('more')}
                                >
                                    ← More tracking options
                                </button>
                                <WeightEntry onDone={close} />
                            </>
                        )}
                    </>
                )}
            </section>
        </div>,
        document.body,
    )
}

function TrackTabs({ mode, onMode }: { mode: TopTrackMode; onMode: (mode: TopTrackMode) => void }) {
    const tabs: Array<{ mode: TopTrackMode; label: string; icon: typeof Search }> = [
        { mode: 'search', label: 'Search', icon: Search },
        { mode: 'scan', label: 'Scan', icon: Camera },
        { mode: 'quick', label: 'Quick Add', icon: Zap },
        { mode: 'more', label: 'More', icon: MoreHorizontal },
    ]
    return (
        <div className="track-tabs" role="tablist" aria-label="Tracking options">
            {tabs.map((tab) => {
                const Icon = tab.icon
                return (
                    <button
                        key={tab.mode}
                        type="button"
                        role="tab"
                        aria-selected={mode === tab.mode}
                        className={mode === tab.mode ? 'active' : ''}
                        onClick={() => onMode(tab.mode)}
                    >
                        <Icon />
                        <span>{tab.label}</span>
                    </button>
                )
            })}
        </div>
    )
}

function TrackMore({
    allowRecipeCreation,
    onWeight,
    onEstimate,
    onCreate,
}: {
    allowRecipeCreation?: boolean
    onWeight?: () => void
    onEstimate: () => void
    onCreate?: () => void
}) {
    return (
        <div className="track-home">
            <div className="track-home-grid">
                <button type="button" onClick={onEstimate}>
                    <Sparkles />
                    <b>AI meal estimate</b>
                    <small>Photos and description</small>
                </button>
                {onWeight && (
                    <button type="button" onClick={onWeight}>
                        <Scale />
                        <b>Log weight</b>
                        <small>Add a weigh-in now</small>
                    </button>
                )}
                {onCreate ? (
                    <button type="button" onClick={onCreate}>
                        <Beef />
                        <b>Create food</b>
                        <small>Add your own</small>
                    </button>
                ) : (
                    <Link to="/foods/new">
                        <Beef />
                        <b>Create food</b>
                        <small>Add your own</small>
                    </Link>
                )}
                {allowRecipeCreation && (
                    <Link to="/recipes/new">
                        <BookOpen />
                        <b>Create recipe</b>
                        <small>Build a batch</small>
                    </Link>
                )}
            </div>
        </div>
    )
}

function TrackSearch({
    onSelect,
    onCreate,
}: {
    onSelect: (item: Trackable) => void
    onCreate?: () => void
}) {
    const [query, setQuery] = useState('')
    const [type, setType] = useState('ALL')
    const results = useQuery({
        queryKey: queryKeys.trackables(query, type),
        queryFn: () => api.trackables(query, type),
        staleTime: 30_000,
    })
    const goTos = useQuery({
        queryKey: queryKeys.timeOfDaySuggestions(type),
        queryFn: () => api.timeOfDaySuggestions(type),
        enabled: query.trim() === '',
        staleTime: 60_000,
    })
    const suggestions = query.trim() === '' ? goTos.data?.items || [] : []
    const suggestedKeys = new Set(suggestions.map((item) => `${item.type}-${item.id}`))
    const resultItems = (results.data || []).filter(
        (item) => !suggestedKeys.has(`${item.type}-${item.id}`),
    )
    return (
        <>
            <label className="search-field track-search">
                <Search />
                <input
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="Search foods and recipes…"
                />
                <kbd>{results.data?.length || 0}</kbd>
            </label>
            <div className="segmented compact">
                <button
                    type="button"
                    className={type === 'ALL' ? 'active' : ''}
                    onClick={() => setType('ALL')}
                >
                    All
                </button>
                <button
                    type="button"
                    className={type === 'FOOD' ? 'active' : ''}
                    onClick={() => setType('FOOD')}
                >
                    Foods
                </button>
                <button
                    type="button"
                    className={type === 'RECIPE' ? 'active' : ''}
                    onClick={() => setType('RECIPE')}
                >
                    Recipes
                </button>
            </div>
            {suggestions.length > 0 && goTos.data && (
                <section className="track-gotos" aria-labelledby="track-gotos-title">
                    <h2 id="track-gotos-title">Around {formatAnchorHour(goTos.data.anchorHour)}</h2>
                    <div>
                        {suggestions.map((item) => (
                            <button
                                type="button"
                                key={`${item.type}-${item.id}`}
                                onClick={() => onSelect(item)}
                            >
                                <span
                                    className={`track-result-icon track-result-icon--${item.type.toLowerCase()}`}
                                >
                                    {item.type === 'FOOD' ? <Utensils /> : <BookOpen />}
                                </span>
                                <b>{item.name}</b>
                                <small>{item.brand || item.servingLabel}</small>
                            </button>
                        ))}
                    </div>
                </section>
            )}
            {results.isLoading ? (
                <Skeleton lines={5} />
            ) : resultItems.length ? (
                <div className="track-results">
                    {resultItems.map((item) => (
                        <TrackableResult
                            item={item}
                            onSelect={onSelect}
                            key={`${item.type}-${item.id}`}
                        />
                    ))}
                </div>
            ) : suggestions.length === 0 ? (
                <StatePanel
                    compact
                    title="No matches"
                    message="Try another name or create your own food or recipe."
                    action={
                        <div className="inline-actions">
                            {onCreate ? (
                                <Button onClick={onCreate}>Create food</Button>
                            ) : (
                                <>
                                    <Link className="button button--secondary" to="/foods/new">
                                        Create food
                                    </Link>
                                    <Link className="button button--secondary" to="/recipes/new">
                                        Create recipe
                                    </Link>
                                </>
                            )}
                        </div>
                    }
                />
            ) : null}
        </>
    )
}

function TrackableResult({
    item,
    onSelect,
}: {
    item: Trackable
    onSelect: (item: Trackable) => void
}) {
    return (
        <button type="button" onClick={() => onSelect(item)}>
            <span className={`track-result-icon track-result-icon--${item.type.toLowerCase()}`}>
                {item.type === 'FOOD' ? <Utensils /> : <BookOpen />}
            </span>
            <div>
                <b>{item.name}</b>
                <small>{[item.brand, item.servingLabel].filter(Boolean).join(' · ')}</small>
            </div>
            <strong>{kcal(item.nutrients)} kcal</strong>
            <ChevronRight />
        </button>
    )
}

function AmountForm({
    item,
    backLabel,
    onBack,
    onDone,
    when,
    onWhenChange,
    onIngredients,
}: {
    when: TrackingWhen
    onWhenChange: (when: TrackingWhen) => void
    onIngredients?: (ingredients: IngredientSelection[]) => void
    item: Trackable
    backLabel: string
    onBack: () => void
    onDone: () => void
}) {
    const client = useQueryClient()
    const toast = useToast()
    const initialized = useRef(false)
    const [amountReady, setAmountReady] = useState(false)
    const [quantity, setQuantity] = useState('')
    const [unitChoice, setUnitChoice] = useState(item.type === 'FOOD' ? 'g' : 'serving')
    const food = useQuery({
        queryKey: ['food-revision', item.revisionId],
        queryFn: () => api.foodRevision(item.revisionId),
        enabled: item.type === 'FOOD',
    })
    const recipe = useQuery({
        queryKey: ['recipe-revision', item.revisionId],
        queryFn: () => api.recipeRevision(item.revisionId),
        enabled: item.type === 'RECIPE',
    })
    const lastAmount = useQuery({
        queryKey: queryKeys.lastTrackedAmount(item.type, item.revisionId),
        queryFn: () => api.lastTrackedAmount(item.type, item.revisionId),
        staleTime: 30_000,
        enabled: !onIngredients,
    })
    useEffect(() => {
        if (
            initialized.current ||
            (!onIngredients && lastAmount.isPending) ||
            (item.type === 'FOOD' && !food.data) ||
            (item.type === 'RECIPE' && !recipe.data)
        )
            return
        initialized.current = true
        if (!onIngredients && lastAmount.data) {
            setQuantity(String(lastAmount.data.quantity))
            setUnitChoice(
                lastAmount.data.portionId
                    ? `portion:${lastAmount.data.portionId}`
                    : lastAmount.data.unit,
            )
        } else if (food.data) {
            const defaultPortion = food.data.portions.find((portion) => portion.default)
            setQuantity(String(defaultPortion ? 1 : food.data.basisAmount))
            setUnitChoice(
                defaultPortion ? `portion:${defaultPortion.id}` : defaultFoodUnit(food.data),
            )
        } else {
            setQuantity('1')
            setUnitChoice('serving')
        }
        setAmountReady(true)
    }, [food.data, item.type, lastAmount.data, lastAmount.isPending, onIngredients, recipe.data])
    const numericQuantity = parseDecimal(quantity)
    const portionId = unitChoice.startsWith('portion:') ? unitChoice.slice(8) : ''
    const unit = portionId ? 'portion' : unitChoice
    const resolved = useQuery({
        queryKey: ['resolved-food', item.revisionId, numericQuantity, unit, portionId],
        queryFn: () =>
            api.resolveFood(item.revisionId, {
                quantity: numericQuantity,
                unit,
                portionId: portionId || null,
            }),
        enabled:
            item.type === 'FOOD' &&
            amountReady &&
            Number.isFinite(numericQuantity) &&
            numericQuantity > 0 &&
            Boolean(unit),
    })
    const preview = !amountReady
        ? undefined
        : item.type === 'RECIPE'
          ? recipe.data
              ? recipeNutrients(recipe.data, numericQuantity, unit)
              : undefined
          : resolved.data?.nutrients
    const add = useMutation({
        mutationFn: async (payload: unknown) => {
            if (onIngredients) {
                if (food.data)
                    onIngredients([
                        {
                            food: food.data,
                            quantity: numericQuantity,
                            unit,
                            portionId: portionId || undefined,
                        },
                    ])
                else {
                    if (!recipe.data) throw new Error('Recipe details are still loading')
                    const factor = recipeBatchFraction(recipe.data, numericQuantity, unit)
                    if (factor === undefined) throw new Error('This recipe cannot use that unit')
                    const ingredients = await Promise.all(
                        recipe.data.ingredients.map(async (ingredient) => ({
                            food: await api.foodRevision(ingredient.foodRevisionId),
                            quantity: ingredient.quantity * factor,
                            unit: ingredient.unit,
                            portionId: ingredient.portionId,
                        })),
                    )
                    onIngredients(ingredients)
                }
                return
            }
            return item.type === 'FOOD'
                ? api.addFoodEntry(payload)
                : api.addRecipeEntry(payload as AddRecipeEntryInput)
        },
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push(onIngredients ? 'Ingredient added' : 'Added to Food Log', item.name)
            onDone()
        },
        onError: (error) => toast.push('Could not track item', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        if (!Number.isFinite(numericQuantity) || numericQuantity <= 0) return
        const common = onIngredients ? {} : trackingTimestamp(when)
        if (item.type === 'FOOD')
            add.mutate({
                ...common,
                foodRevisionId: item.revisionId,
                quantity: numericQuantity,
                unit,
                portionId: portionId || null,
            })
        else
            add.mutate({
                ...common,
                recipeRevisionId: item.revisionId,
                quantity: numericQuantity,
                unit,
            })
    }
    const units = food.data ? foodUnits(food.data) : recipe.data ? recipeUnits(recipe.data) : []
    return (
        <form className="track-amount" onSubmit={submit}>
            <button type="button" className="sheet-back" onClick={onBack}>
                ← {backLabel}
            </button>
            <div className="selected-trackable">
                <span className={`track-result-icon track-result-icon--${item.type.toLowerCase()}`}>
                    {item.type === 'FOOD' ? <Utensils /> : <BookOpen />}
                </span>
                <div>
                    <h2>{item.name}</h2>
                    <p>{item.brand || (item.type === 'RECIPE' ? 'Recipe' : 'Food')}</p>
                </div>
                {item.type === 'RECIPE' && !onIngredients && (
                    <Link
                        className="button button--secondary selected-trackable-edit"
                        to={`/recipes/${item.id}/edit`}
                    >
                        <Pencil />
                        Edit
                    </Link>
                )}
            </div>
            {(food.isError || recipe.isError) && (
                <p role="alert" className="field-error">
                    Could not load this {item.type === 'FOOD' ? 'food' : 'recipe'}.{' '}
                    <Button
                        variant="secondary"
                        onClick={() => (item.type === 'FOOD' ? food.refetch() : recipe.refetch())}
                    >
                        Try again
                    </Button>
                </p>
            )}
            <LiveNutrition nutrients={preview} pending={!amountReady || resolved.isFetching} />
            {!onIngredients && <TrackingWhenFields value={when} onChange={onWhenChange} />}
            <div className="track-amount-entry">
                <Field label="Amount">
                    <input
                        name="quantity"
                        type="text"
                        inputMode="decimal"
                        required
                        value={quantity}
                        onChange={(event) => setQuantity(event.target.value)}
                    />
                </Field>
                {units.length > 0 && (
                    <fieldset className="track-unit-picker">
                        <legend>Unit</legend>
                        <div>
                            {units.map((option) => (
                                <button
                                    type="button"
                                    className={unitChoice === option.value ? 'active' : ''}
                                    key={option.value}
                                    onClick={() => {
                                        setUnitChoice(option.value)
                                        if (option.value.startsWith('portion:')) setQuantity('1')
                                    }}
                                >
                                    {option.label}
                                </button>
                            ))}
                        </div>
                    </fieldset>
                )}
                {resolved.isError && (
                    <p className="field-error">This amount cannot be converted using that unit.</p>
                )}
                <Button
                    className="track-add-button"
                    type="submit"
                    disabled={
                        !amountReady ||
                        add.isPending ||
                        !Number.isFinite(numericQuantity) ||
                        numericQuantity <= 0 ||
                        !preview
                    }
                >
                    {add.isPending
                        ? 'Adding…'
                        : onIngredients
                          ? 'Add ingredient'
                          : 'Add to Food Log'}
                </Button>
            </div>
        </form>
    )
}

function LiveNutrition({ nutrients, pending }: { nutrients?: Nutrients; pending: boolean }) {
    const values = [
        { code: 'energy_kcal', label: 'Calories', unit: '' },
        { code: 'protein_g', label: 'Protein', unit: 'g' },
        { code: 'fat_g', label: 'Fat', unit: 'g' },
        { code: 'carbohydrate_g', label: 'Carbs', unit: 'g' },
    ] as const
    return (
        <div className="track-live-nutrition" aria-live="polite" aria-busy={pending}>
            {values.map((value) => (
                <div key={value.code}>
                    <strong>
                        {nutrients
                            ? formatNumber(nutrients[value.code], value.unit)
                            : pending
                              ? '…'
                              : '—'}
                    </strong>
                    <span>{value.label}</span>
                </div>
            ))}
        </div>
    )
}

function defaultFoodUnit(food: Food) {
    if (food.basisType === 'PER_100_ML') return 'ml'
    if (food.basisType === 'PER_SERVING') return 'serving'
    return 'g'
}

export function foodUnits(food: Food) {
    const units: Array<{ value: string; label: string }> = []
    if (food.basisType === 'PER_100_G' || food.densityGPerMl) units.push({ value: 'g', label: 'g' })
    if (food.basisType === 'PER_100_ML' || food.densityGPerMl)
        units.push({ value: 'ml', label: 'ml' })
    if (food.basisType === 'PER_SERVING') units.push({ value: 'serving', label: 'serving' })
    food.portions.forEach((portion) => {
        units.push({ value: `portion:${portion.id}`, label: portion.name })
    })
    return units
}

function trackableFood(food: Food): Trackable {
    return {
        type: 'FOOD',
        id: food.id,
        revisionId: food.revisionId,
        name: food.name,
        brand: food.brand,
        servingLabel: `${formatNumber(food.basisAmount)} ${food.basisUnit}`,
        nutrients: food.nutrients,
    }
}

function QuickEntry({
    onDone,
    when,
    onWhenChange,
    onFoodReady,
}: {
    onDone: () => void
    when: TrackingWhen
    onWhenChange: (when: TrackingWhen) => void
    onFoodReady?: (food: Food) => void
}) {
    const client = useQueryClient()
    const toast = useToast()
    const add = useMutation({
        mutationFn: (input: QuickFoodInput) =>
            onFoodReady ? createQuickFood(input) : api.quickTrack(input),
        onSuccess: (result) => {
            if (onFoodReady) {
                onFoodReady(result as Food)
                return
            }
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Added to Food Log')
            onDone()
        },
        onError: (error) => toast.push('Could not track entry', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        add.mutate({
            name: String(data.get('name')),
            ...(onFoodReady ? {} : trackingTimestamp(when)),
            ...readQuickNutrients(data),
            saveAsFood: data.get('save') === 'on',
        })
    }
    return (
        <form className="form-grid quick-track-sheet" onSubmit={submit}>
            <Field label="Name" className="span-2">
                <input name="name" required placeholder="Post-workout shake" />
            </Field>
            <QuickNutrientFields />
            {!onFoodReady && <TrackingWhenFields value={when} onChange={onWhenChange} />}
            {!onFoodReady && (
                <label className="check span-2">
                    <input name="save" type="checkbox" />
                    Save for next time
                </label>
            )}
            <Button className="span-2 track-primary-action" type="submit" disabled={add.isPending}>
                {add.isPending ? 'Adding…' : onFoodReady ? 'Choose amount' : 'Add to Food Log'}
            </Button>
        </form>
    )
}

function WeightEntry({ onDone }: { onDone: () => void }) {
    const client = useQueryClient()
    const toast = useToast()
    const add = useMutation({
        mutationFn: api.addWeight,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.weights })
            client.invalidateQueries({ queryKey: queryKeys.expenditure })
            toast.push('Weigh-in added')
            onDone()
        },
        onError: (error) => toast.push('Could not add weigh-in', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        add.mutate({
            weightKg: parseDecimal(data.get('weight')),
            measuredAt: new Date().toISOString(),
            note: String(data.get('note') || '') || null,
        })
    }
    return (
        <form className="form-grid quick-track-sheet" onSubmit={submit}>
            <Field label="Weight (kg)" className="span-2">
                <input name="weight" type="text" inputMode="decimal" required />
            </Field>
            <Field label="Note" className="span-2">
                <input name="note" maxLength={500} placeholder="Optional" />
            </Field>
            <p className="track-now span-2">Measured now</p>
            <Button className="span-2 track-primary-action" type="submit" disabled={add.isPending}>
                {add.isPending ? 'Adding…' : 'Add weigh-in'}
            </Button>
        </form>
    )
}

function trackTitle(mode: TrackMode) {
    if (mode === 'search') return 'Find food or recipes'
    if (mode === 'quick') return 'Quick track'
    if (mode === 'weight') return 'Log weight'
    if (mode === 'estimate') return 'Estimate a meal'
    if (mode === 'more') return 'More tracking options'
    return 'Scan a product'
}

function formatAnchorHour(hour: number) {
    const value = new Date(2020, 0, 1, hour)
    return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(value)
}

function CreateFoodEntry({
    onFoodReady,
    onBack,
}: {
    onFoodReady: (food: Food) => void
    onBack: () => void
}) {
    const toast = useToast()
    const definitions = useQuery({ queryKey: queryKeys.nutrients, queryFn: api.nutrients })
    const save = useMutation({
        mutationFn: (input: FoodInput) => api.createFood(input),
        onSuccess: onFoodReady,
        onError: (error) => toast.push('Could not create food', error.message, 'error'),
    })
    return (
        <>
            <button type="button" className="sheet-back" onClick={onBack}>
                ← More options
            </button>
            <FoodForm
                definitions={definitions.data}
                pending={save.isPending}
                submitLabel="Choose amount"
                onSubmit={(input) => save.mutate(input)}
            />
        </>
    )
}
