import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
    Activity,
    ArrowDown,
    ArrowUp,
    CheckCircle2,
    Trash2,
    Weight as WeightIcon,
} from 'lucide-react'
import type { FormEvent } from 'react'
import {
    Badge,
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
import { formatDate, formatNumber } from '../lib/utils'
import type { Weight } from '../types'

export function ProgressPage() {
    const client = useQueryClient()
    const toast = useToast()
    const weights = useQuery({
        queryKey: queryKeys.weights,
        queryFn: () => api.weights(),
    })
    const estimate = useQuery({
        queryKey: queryKeys.expenditure,
        queryFn: () => api.expenditure(),
    })
    const add = useMutation({
        mutationFn: api.addWeight,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.weights })
            client.invalidateQueries({ queryKey: queryKeys.expenditure })
            toast.push('Weigh-in added')
        },
        onError: (error) => toast.push('Could not add weigh-in', error.message, 'error'),
    })
    const remove = useMutation({
        mutationFn: api.deleteWeight,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.weights })
            client.invalidateQueries({ queryKey: queryKeys.expenditure })
            toast.push('Weigh-in removed')
        },
    })
    const persist = useMutation({
        mutationFn: () => api.expenditure(true),
        onSuccess: () => toast.push('Daily calorie estimate saved'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        add.mutate({
            weightKg: Number(data.get('weight')),
            measuredAt: data.get('measuredAt')
                ? new Date(String(data.get('measuredAt'))).toISOString()
                : undefined,
            note: String(data.get('note') || '') || null,
        })
        event.currentTarget.reset()
    }
    return (
        <>
            <PageHeader
                eyebrow="PROGRESS"
                title="See your trend"
                description="Follow your weight and daily calorie needs over time."
            />
            <div className="progress-layout">
                <Card>
                    <SectionHeader
                        eyebrow="WEIGH-INS"
                        title="Weight trend"
                        aside={`${weights.data?.length || 0} entries`}
                    />
                    {weights.isLoading ? (
                        <Skeleton lines={5} />
                    ) : weights.error ? (
                        <ErrorPanel error={weights.error} />
                    ) : (
                        <>
                            <WeightChart weights={weights.data || []} />
                            <form className="weight-form" onSubmit={submit}>
                                <Field label="Weight (kg)">
                                    <input
                                        name="weight"
                                        type="number"
                                        min="10"
                                        max="700"
                                        step="0.1"
                                        required
                                    />
                                </Field>
                                <Field label="Measured at">
                                    <input name="measuredAt" type="datetime-local" />
                                </Field>
                                <Field label="Note">
                                    <input
                                        name="note"
                                        maxLength={500}
                                        placeholder="Morning, after training…"
                                    />
                                </Field>
                                <Button type="submit" disabled={add.isPending}>
                                    Add weigh-in
                                </Button>
                            </form>
                            <div className="weight-history">
                                {weights.data?.slice(0, 10).map((item) => (
                                    <div key={item.id}>
                                        <span className="weight-dot">
                                            <WeightIcon />
                                        </span>
                                        <div>
                                            <b>{formatNumber(item.weightKg, 'kg')}</b>
                                            <span>
                                                {formatDate(item.measuredAt)}
                                                {item.note ? ` · ${item.note}` : ''}
                                            </span>
                                        </div>
                                        <ConfirmDialog
                                            title="Remove this weigh-in?"
                                            description="Your calorie estimate will update without it."
                                            danger
                                            confirmLabel="Remove"
                                            onConfirm={() => remove.mutate(item.id)}
                                            trigger={
                                                <Button
                                                    variant="ghost"
                                                    aria-label="Remove weigh-in"
                                                >
                                                    <Trash2 />
                                                </Button>
                                            }
                                        />
                                    </div>
                                ))}
                            </div>
                        </>
                    )}
                </Card>
                <Card tone="dark">
                    <SectionHeader
                        eyebrow="DAILY CALORIES"
                        title="Estimated needs"
                        aside={
                            <Badge tone="orange">{estimate.data?.confidence || 'loading'}</Badge>
                        }
                    />
                    {estimate.isLoading ? (
                        <Skeleton lines={4} />
                    ) : estimate.error ? (
                        <ErrorPanel error={estimate.error} />
                    ) : (
                        <>
                            <div className="estimate-value">
                                <strong>
                                    {estimate.data?.suggestedKcal
                                        ? Math.round(estimate.data.suggestedKcal).toLocaleString()
                                        : '—'}
                                </strong>
                                <span>kcal / day</span>
                            </div>
                            <div className="estimate-compare">
                                <div>
                                    <span>Starting estimate</span>
                                    <b>{formatNumber(estimate.data?.baselineKcal, 'kcal')}</b>
                                </div>
                                <div>
                                    <span>Based on your progress</span>
                                    <b>{formatNumber(estimate.data?.adaptiveKcal, 'kcal')}</b>
                                </div>
                            </div>
                            <div className="requirement-list">
                                <Requirement
                                    label="Logged days"
                                    current={estimate.data?.requirements.loggedDays || 0}
                                    target={14}
                                />
                                <Requirement
                                    label="Weigh-ins"
                                    current={estimate.data?.requirements.weighIns || 0}
                                    target={4}
                                />
                                <Requirement
                                    label="Weight span"
                                    current={estimate.data?.requirements.weightSpanDays || 0}
                                    target={14}
                                />
                            </div>
                            {estimate.data?.explanation.map((line) => (
                                <p className="estimate-note" key={line}>
                                    {line}
                                </p>
                            ))}
                            <Button
                                variant="secondary"
                                disabled={persist.isPending || !estimate.data?.suggestedKcal}
                                onClick={() => persist.mutate()}
                            >
                                Use this calorie estimate
                            </Button>
                        </>
                    )}
                </Card>
            </div>
        </>
    )
}

function Requirement({
    label,
    current,
    target,
}: {
    label: string
    current: number
    target: number
}) {
    const done = current >= target
    return (
        <div>
            <span>
                {done ? <CheckCircle2 /> : <Activity />}
                {label}
            </span>
            <b>
                {current}/{target}
            </b>
        </div>
    )
}

function WeightChart({ weights }: { weights: Weight[] }) {
    if (weights.length < 2)
        return (
            <StatePanel
                compact
                title="A trend needs two points"
                message="Add another weigh-in to draw the first line."
            />
        )
    const points = [...weights].reverse().slice(-30)
    const values = points.map((p) => p.weightKg)
    const min = Math.min(...values) - 0.5
    const max = Math.max(...values) + 0.5
    const range = max - min || 1
    const path = points
        .map(
            (p, i) =>
                `${i ? 'L' : 'M'} ${(i / (points.length - 1)) * 100} ${100 - ((p.weightKg - min) / range) * 100}`,
        )
        .join(' ')
    const delta = (values.at(-1) ?? values[0]) - values[0]
    return (
        <div className="weight-chart">
            <div className="chart-summary">
                <span>Last {points.length} entries</span>
                <b>
                    {delta <= 0 ? <ArrowDown /> : <ArrowUp />}
                    {formatNumber(Math.abs(delta), 'kg')}
                </b>
            </div>
            <svg
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                role="img"
                aria-label={`Weight changed ${formatNumber(delta, 'kilograms')} across ${points.length} measurements`}
            >
                <path d={path} />
                {points.map((p, i) => (
                    <circle
                        key={p.id}
                        cx={(i / (points.length - 1)) * 100}
                        cy={100 - ((p.weightKg - min) / range) * 100}
                        r="1.8"
                    >
                        <title>
                            {p.weightKg} kg on {formatDate(p.measuredAt)}
                        </title>
                    </circle>
                ))}
            </svg>
        </div>
    )
}
