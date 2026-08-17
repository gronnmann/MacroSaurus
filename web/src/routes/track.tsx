import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Beef, BookOpen, Camera, ChevronRight, Scale, Search, Utensils, X, Zap } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Button, Field, Skeleton, StatePanel, useToast } from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber, kcal, localDate } from '../lib/utils'
import type { Trackable } from '../types'
import { ScanExperience } from './scan'

type TrackMode = 'home' | 'search' | 'quick' | 'scan' | 'weight'

export function TrackPage() {
    const location = useLocation()
    const navigate = useNavigate()
    const [mode, setMode] = useState<TrackMode>('home')
    const from = (location.state as { from?: string } | null)?.from || '/dashboard'
    const close = () => navigate(from, { replace: true })
    return (
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
                <header>
                    <div>
                        <p className="eyebrow">QUICK ACTION</p>
                        <h1 id="track-title">{trackTitle(mode)}</h1>
                    </div>
                    <button
                        type="button"
                        className="sheet-close"
                        onClick={close}
                        aria-label="Close Track"
                    >
                        <X />
                    </button>
                </header>
                {mode !== 'home' && (
                    <button type="button" className="sheet-back" onClick={() => setMode('home')}>
                        ← All tracking options
                    </button>
                )}
                {mode === 'home' && <TrackHome onMode={setMode} />}{' '}
                {mode === 'search' && <TrackSearch onDone={close} />}{' '}
                {mode === 'quick' && <QuickEntry onDone={close} />}{' '}
                {mode === 'scan' && <ScanExperience />}
                {mode === 'weight' && <WeightEntry onDone={close} />}
            </section>
        </div>
    )
}

function TrackHome({ onMode }: { onMode: (mode: TrackMode) => void }) {
    return (
        <div className="track-home">
            <button type="button" className="track-home-primary" onClick={() => onMode('search')}>
                <span>
                    <Search />
                </span>
                <div>
                    <b>Search foods & recipes</b>
                    <small>Choose an amount or portion</small>
                </div>
                <ChevronRight />
            </button>
            <div className="track-home-grid">
                <button type="button" onClick={() => onMode('scan')}>
                    <Camera />
                    <b>Scan barcode</b>
                    <small>Use your camera</small>
                </button>
                <button type="button" onClick={() => onMode('quick')}>
                    <Zap />
                    <b>Quick track</b>
                    <small>Macros only</small>
                </button>
                <button type="button" onClick={() => onMode('weight')}>
                    <Scale />
                    <b>Log weight</b>
                    <small>Add a weigh-in now</small>
                </button>
                <Link to="/foods/new">
                    <Beef />
                    <b>Create food</b>
                    <small>Add your own</small>
                </Link>
                <Link to="/recipes/new">
                    <BookOpen />
                    <b>Create recipe</b>
                    <small>Build a batch</small>
                </Link>
            </div>
        </div>
    )
}

function TrackSearch({ onDone }: { onDone: () => void }) {
    const [query, setQuery] = useState('')
    const [type, setType] = useState('ALL')
    const [selected, setSelected] = useState<Trackable>()
    const results = useQuery({
        queryKey: queryKeys.trackables(query, type),
        queryFn: () => api.trackables(query, type),
        staleTime: 30_000,
    })
    if (selected)
        return <AmountForm item={selected} onBack={() => setSelected(undefined)} onDone={onDone} />
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
            {results.isLoading ? (
                <Skeleton lines={5} />
            ) : results.data?.length ? (
                <div className="track-results">
                    {results.data.map((item) => (
                        <button
                            type="button"
                            key={`${item.type}-${item.id}`}
                            onClick={() => setSelected(item)}
                        >
                            <span
                                className={`track-result-icon track-result-icon--${item.type.toLowerCase()}`}
                            >
                                {item.type === 'FOOD' ? <Utensils /> : <BookOpen />}
                            </span>
                            <div>
                                <b>{item.name}</b>
                                <small>
                                    {[item.brand, item.servingLabel].filter(Boolean).join(' · ')}
                                </small>
                            </div>
                            <strong>{kcal(item.nutrients)} kcal</strong>
                            <ChevronRight />
                        </button>
                    ))}
                </div>
            ) : (
                <StatePanel
                    compact
                    title="No matches"
                    message="Try another name or create your own food or recipe."
                    action={
                        <div className="inline-actions">
                            <Link className="button button--secondary" to="/foods/new">
                                Create food
                            </Link>
                            <Link className="button button--secondary" to="/recipes/new">
                                Create recipe
                            </Link>
                        </div>
                    }
                />
            )}
        </>
    )
}

function AmountForm({
    item,
    onBack,
    onDone,
}: {
    item: Trackable
    onBack: () => void
    onDone: () => void
}) {
    const client = useQueryClient()
    const toast = useToast()
    const [unit, setUnit] = useState(item.type === 'FOOD' ? 'g' : 'serving')
    const food = useQuery({
        queryKey: queryKeys.food(item.id),
        queryFn: () => api.food(item.id),
        enabled: item.type === 'FOOD',
    })
    const add = useMutation({
        mutationFn: (payload: unknown) =>
            item.type === 'FOOD' ? api.addFoodEntry(payload) : api.addRecipeEntry(payload),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Added to Food Log', item.name)
            onDone()
        },
        onError: (error) => toast.push('Could not track item', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const trackedAt = new Date()
        const common = {
            localDate: localDate(trackedAt),
            consumedAt: trackedAt.toISOString(),
            meal: 'OTHER',
        }
        if (item.type === 'FOOD')
            add.mutate({
                ...common,
                foodRevisionId: item.revisionId,
                quantity: Number(data.get('quantity')),
                unit,
                portionId: unit === 'portion' ? data.get('portionId') : null,
            })
        else
            add.mutate({
                ...common,
                recipeRevisionId: item.revisionId,
                servings: Number(data.get('quantity')),
            })
    }
    return (
        <form className="track-amount" onSubmit={submit}>
            <button type="button" className="sheet-back" onClick={onBack}>
                ← Search results
            </button>
            <div className="selected-trackable">
                <span className={`track-result-icon track-result-icon--${item.type.toLowerCase()}`}>
                    {item.type === 'FOOD' ? <Utensils /> : <BookOpen />}
                </span>
                <div>
                    <h2>{item.name}</h2>
                    <p>
                        {item.brand || item.servingLabel} · {kcal(item.nutrients)} kcal
                    </p>
                </div>
            </div>
            <div className="form-grid">
                <Field label={item.type === 'FOOD' ? 'Amount' : 'Servings'}>
                    <input
                        name="quantity"
                        type="number"
                        min="0.000001"
                        step="any"
                        required
                        defaultValue={item.type === 'FOOD' ? 100 : 1}
                    />
                </Field>
                {item.type === 'FOOD' && (
                    <Field label="Unit">
                        <select value={unit} onChange={(event) => setUnit(event.target.value)}>
                            <option value="g">grams</option>
                            <option value="ml">millilitres</option>
                            {food.data?.basisType === 'PER_SERVING' && (
                                <option value="serving">servings</option>
                            )}
                            {food.data?.portions.length ? (
                                <option value="portion">named portion</option>
                            ) : null}
                        </select>
                    </Field>
                )}
                {unit === 'portion' && (
                    <Field className="span-2" label="Portion">
                        <select name="portionId" required>
                            <option value="">Choose portion…</option>
                            {food.data?.portions.map((portion) => (
                                <option value={portion.id} key={portion.id}>
                                    {portion.name}
                                    {portion.gramWeight
                                        ? ` · ${formatNumber(portion.gramWeight)} g`
                                        : ''}
                                </option>
                            ))}
                        </select>
                    </Field>
                )}
                <p className="track-now span-2">Logged at the current date and time</p>
                <Button className="span-2" type="submit" disabled={add.isPending}>
                    {add.isPending ? 'Adding…' : 'Add to Food Log'}
                </Button>
            </div>
        </form>
    )
}

function QuickEntry({ onDone }: { onDone: () => void }) {
    const client = useQueryClient()
    const toast = useToast()
    const add = useMutation({
        mutationFn: api.quickTrack,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Added to Food Log')
            onDone()
        },
        onError: (error) => toast.push('Could not track entry', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const trackedAt = new Date()
        add.mutate({
            name: data.get('name'),
            localDate: localDate(trackedAt),
            consumedAt: trackedAt.toISOString(),
            meal: 'OTHER',
            calories: numberOrNull(data.get('calories')),
            proteinG: number(data.get('protein')),
            carbohydrateG: number(data.get('carbs')),
            fatG: number(data.get('fat')),
            fiberG: numberOrNull(data.get('fiber')),
            saveAsFood: data.get('save') === 'on',
        })
    }
    return (
        <form className="form-grid quick-track-sheet" onSubmit={submit}>
            <Field label="Name" className="span-2">
                <input name="name" required placeholder="Post-workout shake" />
            </Field>
            <Field label="Calories">
                <input
                    name="calories"
                    type="number"
                    min="0"
                    step="any"
                    placeholder="Calculated if empty"
                />
            </Field>
            <Field label="Protein (g)">
                <input name="protein" type="number" min="0" step="any" defaultValue="0" />
            </Field>
            <Field label="Carbs (g)">
                <input name="carbs" type="number" min="0" step="any" defaultValue="0" />
            </Field>
            <Field label="Fat (g)">
                <input name="fat" type="number" min="0" step="any" defaultValue="0" />
            </Field>
            <Field label="Fiber (g)">
                <input name="fiber" type="number" min="0" step="any" />
            </Field>
            <p className="track-now span-2">Logged at the current date and time</p>
            <label className="check span-2">
                <input name="save" type="checkbox" />
                Save for next time
            </label>
            <Button className="span-2" type="submit" disabled={add.isPending}>
                {add.isPending ? 'Adding…' : 'Add to Food Log'}
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
            weightKg: Number(data.get('weight')),
            measuredAt: new Date().toISOString(),
            note: String(data.get('note') || '') || null,
        })
    }
    return (
        <form className="form-grid quick-track-sheet" onSubmit={submit}>
            <Field label="Weight (kg)" className="span-2">
                <input name="weight" type="number" min="10" max="700" step="0.1" required />
            </Field>
            <Field label="Note" className="span-2">
                <input name="note" maxLength={500} placeholder="Optional" />
            </Field>
            <p className="track-now span-2">Measured now</p>
            <Button className="span-2" type="submit" disabled={add.isPending}>
                {add.isPending ? 'Adding…' : 'Add weigh-in'}
            </Button>
        </form>
    )
}

function trackTitle(mode: TrackMode) {
    if (mode === 'home') return 'What are you tracking?'
    if (mode === 'search') return 'Find food or recipes'
    if (mode === 'quick') return 'Quick track'
    if (mode === 'weight') return 'Log weight'
    return 'Scan a product'
}

const number = (value: FormDataEntryValue | null) => Number(value || 0)
const numberOrNull = (value: FormDataEntryValue | null) =>
    value === '' || value == null ? null : Number(value)
