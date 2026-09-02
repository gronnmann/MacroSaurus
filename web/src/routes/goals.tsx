import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, Target, Trash2 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import {
    Badge,
    Button,
    Card,
    ConfirmDialog,
    ErrorPanel,
    Field,
    PageHeader,
    Skeleton,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { parseDecimal } from '../lib/utils'
import type { NutrientTarget } from '../types'

export function GoalsPage() {
    return (
        <>
            <PageHeader
                eyebrow="YOUR TARGETS"
                title="Nutrition goals"
                description="Choose the numbers that help you plan your day."
            />
            <NutrientGoals />
        </>
    )
}

export function NutrientGoals() {
    const [search, setSearch] = useState('')
    const targets = useQuery({
        queryKey: queryKeys.targets,
        queryFn: api.targets,
    })
    const filtered =
        targets.data?.filter(
            (item) =>
                item.displayName.toLowerCase().includes(search.toLowerCase()) ||
                item.nutrientCode.includes(search.toLowerCase()),
        ) || []
    return (
        <Card>
            <label className="search-field">
                <Search />
                <input
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Find iron, vitamin D, sodium…"
                />
                <kbd>{filtered.length}</kbd>
            </label>
            {targets.isLoading ? (
                <Skeleton lines={8} />
            ) : targets.error ? (
                <ErrorPanel error={targets.error} />
            ) : (
                <div className="target-list">
                    {filtered
                        .filter(
                            (target) =>
                                !['energy_kcal', 'protein_g', 'carbohydrate_g', 'fat_g'].includes(
                                    target.nutrientCode,
                                ),
                        )
                        .map((target) => (
                            <TargetRow key={target.nutrientCode} target={target} />
                        ))}
                </div>
            )}
        </Card>
    )
}

function TargetRow({ target }: { target: NutrientTarget }) {
    const client = useQueryClient()
    const toast = useToast()
    const [editing, setEditing] = useState(false)
    const save = useMutation({
        mutationFn: (values: {
            targetAmount?: number
            minimumAmount?: number
            maximumAmount?: number
        }) => api.setTarget(target.nutrientCode, values),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.targets })
            setEditing(false)
            toast.push(`${target.displayName} target saved`)
        },
        onError: (error) => toast.push('Could not save target', error.message, 'error'),
    })
    const clear = useMutation({
        mutationFn: () => api.clearTarget(target.nutrientCode),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.targets })
            toast.push('Custom target cleared')
        },
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const value = (key: string) =>
            data.get(key) === '' ? undefined : parseDecimal(data.get(key))
        save.mutate({
            targetAmount: value('target'),
            minimumAmount: value('minimum'),
            maximumAmount: value('maximum'),
        })
    }
    const active =
        target.targetAmount != null || target.minimumAmount != null || target.maximumAmount != null
    return (
        <article className={`target-row ${editing ? 'editing' : ''}`}>
            <span className="target-icon">
                <Target />
            </span>
            <div className="target-copy">
                <b>{target.displayName}</b>
                <span>{target.unit}</span>
            </div>
            {!editing ? (
                <>
                    <div className="target-values">
                        {active ? (
                            <>
                                <Badge tone="green">
                                    Target {target.targetAmount ?? '—'} {target.unit}
                                </Badge>
                                <span>
                                    min {target.minimumAmount ?? '—'} · max{' '}
                                    {target.maximumAmount ?? '—'}
                                </span>
                            </>
                        ) : (
                            <span>No custom target</span>
                        )}
                    </div>
                    <Button variant="secondary" onClick={() => setEditing(true)}>
                        {active ? 'Edit' : 'Set target'}
                    </Button>
                    {active && (
                        <ConfirmDialog
                            title="Clear custom target?"
                            description="Daily totals remain; only this guide is removed."
                            danger
                            confirmLabel="Clear"
                            onConfirm={() => clear.mutate()}
                            trigger={
                                <Button variant="ghost" aria-label="Clear target">
                                    <Trash2 />
                                </Button>
                            }
                        />
                    )}
                </>
            ) : (
                <form onSubmit={submit}>
                    <Field label={`Target (${target.unit})`}>
                        <input
                            name="target"
                            type="text"
                            inputMode="decimal"
                            defaultValue={target.targetAmount}
                        />
                    </Field>
                    <Field label="Minimum">
                        <input
                            name="minimum"
                            type="text"
                            inputMode="decimal"
                            defaultValue={target.minimumAmount}
                        />
                    </Field>
                    <Field label="Maximum">
                        <input
                            name="maximum"
                            type="text"
                            inputMode="decimal"
                            defaultValue={target.maximumAmount}
                        />
                    </Field>
                    <Button type="button" variant="ghost" onClick={() => setEditing(false)}>
                        Cancel
                    </Button>
                    <Button type="submit" disabled={save.isPending}>
                        Save
                    </Button>
                </form>
            )}
        </article>
    )
}
