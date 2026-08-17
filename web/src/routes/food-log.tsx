import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
    CalendarPlus,
    ChevronLeft,
    ChevronRight,
    Copy,
    Ellipsis,
    Pencil,
    Plus,
    Trash2,
} from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
    AppDialog,
    Button,
    Card,
    ConfirmDialog,
    ErrorPanel,
    Field,
    PageHeader,
    SectionHeader,
    Skeleton,
    StatePanel,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber, kcal, round, today } from '../lib/utils'
import type { DiaryEntry, Food, Recipe, UpdateDiaryEntryInput } from '../types'

export function FoodLogPage() {
    const [params, setParams] = useSearchParams()
    const date = params.get('date') || today()
    const [editing, setEditing] = useState<DiaryEntry>()
    const [copying, setCopying] = useState<DiaryEntry>()
    const client = useQueryClient()
    const toast = useToast()
    const day = useQuery({
        queryKey: queryKeys.diary(date),
        queryFn: () => api.diary(date),
    })
    const invalidate = () => client.invalidateQueries({ queryKey: ['diary'] })
    const remove = useMutation({
        mutationFn: api.deleteDiaryEntry,
        onSuccess: () => {
            invalidate()
            toast.push('Entry deleted')
        },
        onError: (error) => toast.push('Could not delete entry', error.message, 'error'),
    })
    const copy = useMutation({
        mutationFn: ({
            id,
            destinationDate,
            destinationTime,
        }: {
            id: string
            destinationDate: string
            destinationTime?: string
        }) => api.copyDiaryEntry(id, { destinationDate, destinationTime }),
        onSuccess: (entry) => {
            invalidate()
            setCopying(undefined)
            toast.push('Entry copied', formatDay(entry.localDate))
        },
        onError: (error) => toast.push('Could not copy entry', error.message, 'error'),
    })
    const move = (amount: number) => setParams({ date: addDays(date, amount) })
    const quickCopy = (entry: DiaryEntry, destinationDate: string) =>
        copy.mutate({ id: entry.id, destinationDate })
    const diaryEntries = day.data?.entries ?? []

    return (
        <>
            <PageHeader
                eyebrow="FOOD LOG"
                title={date === today() ? 'Today’s timeline' : formatDay(date)}
                description="Review, adjust, or reuse anything you tracked."
                actions={
                    <div className="date-switcher">
                        <Button variant="ghost" aria-label="Previous day" onClick={() => move(-1)}>
                            <ChevronLeft />
                        </Button>
                        <input
                            aria-label="Food log date"
                            type="date"
                            value={date}
                            onChange={(event) => setParams({ date: event.target.value })}
                        />
                        <Button variant="ghost" aria-label="Next day" onClick={() => move(1)}>
                            <ChevronRight />
                        </Button>
                    </div>
                }
            />
            <div className="food-log-summary">
                <div>
                    <strong>{kcal(day.data?.totals)}</strong>
                    <span>kcal</span>
                </div>
                <div>
                    <strong>{round(day.data?.totals.protein_g)}</strong>
                    <span>protein</span>
                </div>
                <div>
                    <strong>{round(day.data?.totals.carbohydrate_g)}</strong>
                    <span>carbs</span>
                </div>
                <div>
                    <strong>{round(day.data?.totals.fat_g)}</strong>
                    <span>fat</span>
                </div>
                <Link
                    className="button button--primary"
                    to="/track"
                    state={{ from: `/food-log?date=${date}` }}
                >
                    <Plus />
                    Track
                </Link>
            </div>
            {day.isLoading ? (
                <Skeleton lines={8} />
            ) : day.error ? (
                <ErrorPanel error={day.error} />
            ) : day.data?.entries.length ? (
                <Card className="timeline-card">
                    <SectionHeader eyebrow="ENTRIES" title={`${day.data.entries.length} tracked`} />
                    <div className="food-timeline">
                        {[...diaryEntries]
                            .sort((a, b) => a.consumedAt.localeCompare(b.consumedAt))
                            .map((entry) => (
                                <article className="timeline-entry" key={entry.id}>
                                    <time>{formatTime(entry.consumedAt)}</time>
                                    <span
                                        className={`timeline-dot timeline-dot--${entry.entryType.toLowerCase()}`}
                                    />
                                    <div className="timeline-entry-copy">
                                        <b>{entry.displayName}</b>
                                        <span>
                                            {entry.quantity
                                                ? `${formatNumber(entry.quantity)} ${entry.unit}`
                                                : 'Quick entry'}{' '}
                                            · P {round(entry.nutrients.protein_g)} · C{' '}
                                            {round(entry.nutrients.carbohydrate_g)} · F{' '}
                                            {round(entry.nutrients.fat_g)}
                                        </span>
                                    </div>
                                    <strong>{kcal(entry.nutrients)} kcal</strong>
                                    <details className="entry-menu">
                                        <summary aria-label={`Actions for ${entry.displayName}`}>
                                            <Ellipsis />
                                        </summary>
                                        <div>
                                            <button type="button" onClick={() => setEditing(entry)}>
                                                <Pencil />
                                                Edit
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => quickCopy(entry, today())}
                                            >
                                                <Copy />
                                                Copy to today
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() =>
                                                    quickCopy(entry, addDays(today(), -1))
                                                }
                                            >
                                                <Copy />
                                                Copy to yesterday
                                            </button>
                                            <button type="button" onClick={() => setCopying(entry)}>
                                                <CalendarPlus />
                                                Choose date & time
                                            </button>
                                            <ConfirmDialog
                                                title={`Delete ${entry.displayName}?`}
                                                description="This removes the entry from your log."
                                                danger
                                                confirmLabel="Delete"
                                                onConfirm={() => remove.mutate(entry.id)}
                                                trigger={
                                                    <Button variant="ghost" className="danger">
                                                        <Trash2 />
                                                        Delete
                                                    </Button>
                                                }
                                            />
                                        </div>
                                    </details>
                                </article>
                            ))}
                    </div>
                </Card>
            ) : (
                <StatePanel
                    title="Nothing tracked yet"
                    message="Your entries will appear here in time order."
                    action={
                        <Link className="button button--primary" to="/track">
                            <Plus />
                            Track something
                        </Link>
                    }
                />
            )}
            <EntryEditor
                entry={editing}
                onClose={() => setEditing(undefined)}
                onSaved={() => {
                    invalidate()
                    setEditing(undefined)
                }}
            />
            <CopyDialog
                entry={copying}
                pending={copy.isPending}
                onClose={() => setCopying(undefined)}
                onCopy={(destinationDate, destinationTime) =>
                    copying && copy.mutate({ id: copying.id, destinationDate, destinationTime })
                }
            />
        </>
    )
}

function EntryEditor({
    entry,
    onClose,
    onSaved,
}: {
    entry?: DiaryEntry
    onClose: () => void
    onSaved: () => void
}) {
    const toast = useToast()
    const entryId = entry?.id ?? ''
    const sourceRevisionId = entry?.sourceRevisionId ?? ''
    const food = useQuery({
        queryKey: ['food-revision', sourceRevisionId],
        queryFn: () => api.foodRevision(sourceRevisionId),
        enabled: entry?.entryType === 'FOOD' && !!sourceRevisionId,
    })
    const recipe = useQuery({
        queryKey: ['recipe-revision', sourceRevisionId],
        queryFn: () => api.recipeRevision(sourceRevisionId),
        enabled: entry?.entryType === 'RECIPE' && !!sourceRevisionId,
    })
    const save = useMutation({
        mutationFn: (input: UpdateDiaryEntryInput) => api.updateDiaryEntry(entryId, input),
        onSuccess: () => {
            toast.push('Entry updated')
            onSaved()
        },
        onError: (error) => toast.push('Could not update entry', error.message, 'error'),
    })
    if (!entry) return null
    const local = new Date(entry.consumedAt)
    const initialTime = `${String(local.getHours()).padStart(2, '0')}:${String(local.getMinutes()).padStart(2, '0')}`
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const localDate = String(data.get('date'))
        const time = String(data.get('time'))
        const base: UpdateDiaryEntryInput = {
            localDate,
            consumedAt: new Date(`${localDate}T${time}:00`).toISOString(),
            meal: entry.meal || 'OTHER',
        }
        if (entry.entryType === 'FOOD')
            Object.assign(base, {
                quantity: Number(data.get('quantity')),
                unit: String(data.get('unit')),
                portionId: data.get('portionId') || null,
            })
        if (entry.entryType === 'RECIPE')
            Object.assign(base, { quantity: Number(data.get('quantity')) })
        if (entry.entryType === 'QUICK')
            Object.assign(base, {
                name: data.get('name'),
                calories: number(data.get('calories')),
                proteinG: number(data.get('protein')),
                carbohydrateG: number(data.get('carbs')),
                fatG: number(data.get('fat')),
                fiberG: numberOrNull(data.get('fiber')),
            })
        save.mutate(base)
    }
    return (
        <AppDialog
            open
            onOpenChange={(open) => !open && onClose()}
            title={`Edit ${entry.displayName}`}
            wide
        >
            <form className="form-grid entry-editor" onSubmit={submit}>
                <Field label="Date">
                    <input name="date" type="date" required defaultValue={entry.localDate} />
                </Field>
                <Field label="Time">
                    <input name="time" type="time" required defaultValue={initialTime} />
                </Field>
                {entry.entryType === 'FOOD' && <FoodFields entry={entry} food={food.data} />}{' '}
                {entry.entryType === 'RECIPE' && (
                    <RecipeFields entry={entry} recipe={recipe.data} />
                )}{' '}
                {entry.entryType === 'QUICK' && <QuickFields entry={entry} />}
                <div className="dialog-actions span-2">
                    <Button type="button" variant="ghost" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" disabled={save.isPending}>
                        {save.isPending ? 'Saving…' : 'Save changes'}
                    </Button>
                </div>
            </form>
        </AppDialog>
    )
}

function FoodFields({ entry, food }: { entry: DiaryEntry; food?: Food }) {
    const initialUnit = entry.portionId ? 'portion' : entry.unit || 'g'
    const [unit, setUnit] = useState(initialUnit)
    return (
        <>
            <Field label="Amount">
                <input
                    name="quantity"
                    type="number"
                    min="0.000001"
                    step="any"
                    required
                    defaultValue={entry.quantity}
                />
            </Field>
            <Field label="Unit">
                <select name="unit" value={unit} onChange={(event) => setUnit(event.target.value)}>
                    <option value="g">grams</option>
                    <option value="ml">millilitres</option>
                    {food?.basisType === 'PER_SERVING' && <option value="serving">servings</option>}
                    {food?.portions.length ? <option value="portion">named portion</option> : null}
                </select>
            </Field>
            {unit === 'portion' && (
                <Field className="span-2" label="Portion">
                    <select name="portionId" required defaultValue={entry.portionId || ''}>
                        <option value="">Choose portion…</option>
                        {food?.portions.map((portion) => (
                            <option key={portion.id} value={portion.id}>
                                {portion.name}
                            </option>
                        ))}
                    </select>
                </Field>
            )}
        </>
    )
}
function RecipeFields({ entry, recipe }: { entry: DiaryEntry; recipe?: Recipe }) {
    return (
        <Field className="span-2" label={`Servings${recipe ? ` of ${recipe.name}` : ''}`}>
            <input
                name="quantity"
                type="number"
                min="0.000001"
                step="any"
                required
                defaultValue={entry.quantity}
            />
        </Field>
    )
}
function QuickFields({ entry }: { entry: DiaryEntry }) {
    return (
        <>
            <Field className="span-2" label="Name">
                <input name="name" required defaultValue={entry.displayName} />
            </Field>
            <Field label="Calories">
                <input
                    name="calories"
                    type="number"
                    min="0"
                    step="any"
                    defaultValue={entry.nutrients.energy_kcal}
                />
            </Field>
            <Field label="Protein (g)">
                <input
                    name="protein"
                    type="number"
                    min="0"
                    step="any"
                    defaultValue={entry.nutrients.protein_g || 0}
                />
            </Field>
            <Field label="Carbs (g)">
                <input
                    name="carbs"
                    type="number"
                    min="0"
                    step="any"
                    defaultValue={entry.nutrients.carbohydrate_g || 0}
                />
            </Field>
            <Field label="Fat (g)">
                <input
                    name="fat"
                    type="number"
                    min="0"
                    step="any"
                    defaultValue={entry.nutrients.fat_g || 0}
                />
            </Field>
            <Field className="span-2" label="Fiber (g)">
                <input
                    name="fiber"
                    type="number"
                    min="0"
                    step="any"
                    defaultValue={entry.nutrients.fiber_g}
                />
            </Field>
        </>
    )
}

function CopyDialog({
    entry,
    pending,
    onClose,
    onCopy,
}: {
    entry?: DiaryEntry
    pending: boolean
    onClose: () => void
    onCopy: (date: string, time?: string) => void
}) {
    if (!entry) return null
    const local = new Date(entry.consumedAt)
    const time = `${String(local.getHours()).padStart(2, '0')}:${String(local.getMinutes()).padStart(2, '0')}`
    return (
        <AppDialog
            open
            onOpenChange={(open) => !open && onClose()}
            title={`Copy ${entry.displayName}`}
        >
            <form
                onSubmit={(event) => {
                    event.preventDefault()
                    const data = new FormData(event.currentTarget)
                    onCopy(String(data.get('date')), String(data.get('time')))
                }}
                className="form-grid"
            >
                <Field label="Date">
                    <input name="date" type="date" required defaultValue={today()} />
                </Field>
                <Field label="Time">
                    <input name="time" type="time" required defaultValue={time} />
                </Field>
                <div className="dialog-actions span-2">
                    <Button type="button" variant="ghost" onClick={onClose}>
                        Cancel
                    </Button>
                    <Button type="submit" disabled={pending}>
                        {pending ? 'Copying…' : 'Copy entry'}
                    </Button>
                </div>
            </form>
        </AppDialog>
    )
}

const addDays = (value: string, amount: number) => {
    const date = new Date(`${value}T12:00:00`)
    date.setDate(date.getDate() + amount)
    return date.toISOString().slice(0, 10)
}
const formatDay = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
    }).format(new Date(`${value}T12:00:00`))
const formatTime = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
        hour: '2-digit',
        minute: '2-digit',
    }).format(new Date(value))
const number = (value: FormDataEntryValue | null) => Number(value || 0)
const numberOrNull = (value: FormDataEntryValue | null) =>
    value === '' || value == null ? null : Number(value)
