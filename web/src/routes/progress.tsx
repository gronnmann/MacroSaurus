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
import { formatDate, formatNumber, parseDecimal, today } from '../lib/utils'
import type { ProgressSeriesPoint, Weight } from '../types'

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
    const range = progressRange()
    const series = useQuery({
        queryKey: queryKeys.progressSeries(range.from, range.to),
        queryFn: () => api.progressSeries(range.from, range.to),
    })
    const add = useMutation({
        mutationFn: api.addWeight,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.weights })
            client.invalidateQueries({ queryKey: queryKeys.expenditure })
            client.invalidateQueries({ queryKey: ['progress-series'] })
            toast.push('Weigh-in added')
        },
        onError: (error) => toast.push('Could not add weigh-in', error.message, 'error'),
    })
    const remove = useMutation({
        mutationFn: api.deleteWeight,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.weights })
            client.invalidateQueries({ queryKey: queryKeys.expenditure })
            client.invalidateQueries({ queryKey: ['progress-series'] })
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
            weightKg: parseDecimal(data.get('weight')),
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
                            {series.data && <WeightTrendChart points={series.data} />}
                            <form className="weight-form" onSubmit={submit}>
                                <Field label="Weight (kg)">
                                    <input name="weight" type="text" inputMode="decimal" required />
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
                            {series.data && <ExpenditureChart points={series.data} />}
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

function WeightTrendChart({ points }: { points: ProgressSeriesPoint[] }) {
    const usable = points.filter((point) => point.expenditure.trendWeightKg != null)
    if (usable.length < 2) return null
    const values = usable
        .flatMap((point) => [
            point.expenditure.trendWeightLowerKg,
            point.expenditure.trendWeightUpperKg,
        ])
        .filter((value): value is number => value != null)
    const min = Math.min(...values) - 0.2
    const max = Math.max(...values) + 0.2
    const y = (value: number) => 100 - ((value - min) / (max - min || 1)) * 100
    const x = (index: number) => (index / (usable.length - 1)) * 100
    const line = usable
        .map(
            (point, index) =>
                `${index ? 'L' : 'M'} ${x(index)} ${y(point.expenditure.trendWeightKg ?? 0)}`,
        )
        .join(' ')
    const band = [
        ...usable.map(
            (point, index) =>
                `${x(index)},${y(point.expenditure.trendWeightUpperKg ?? point.expenditure.trendWeightKg ?? 0)}`,
        ),
        ...[...usable]
            .reverse()
            .map(
                (point, reverseIndex) =>
                    `${x(usable.length - 1 - reverseIndex)},${y(point.expenditure.trendWeightLowerKg ?? point.expenditure.trendWeightKg ?? 0)}`,
            ),
    ].join(' ')
    const latest = usable.at(-1)
    if (!latest) return null
    return (
        <div className="uncertainty-chart">
            <div className="chart-summary">
                <span>21-day trend and uncertainty</span>
                <b>{formatNumber(latest.expenditure.trendWeightKg, 'kg')}</b>
            </div>
            <svg
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                role="img"
                aria-label={`Trend weight ${formatNumber(latest.expenditure.trendWeightKg, 'kilograms')}, likely range ${formatNumber(latest.expenditure.trendWeightLowerKg)} to ${formatNumber(latest.expenditure.trendWeightUpperKg)}`}
            >
                <polygon className="chart-band" points={band} />
                <path className="chart-line" d={line} />
                {usable.map(
                    (point, index) =>
                        point.measuredWeightKg != null && (
                            <circle
                                className="chart-measurement"
                                key={point.date}
                                cx={x(index)}
                                cy={y(point.measuredWeightKg)}
                                r="1.4"
                            >
                                <title>
                                    {point.measuredWeightKg} kg on {point.date}
                                </title>
                            </circle>
                        ),
                )}
            </svg>
        </div>
    )
}

function ExpenditureChart({ points }: { points: ProgressSeriesPoint[] }) {
    const usable = points.filter((point) => point.expenditure.suggestedKcal != null)
    if (usable.length < 2) return null
    const values = usable
        .flatMap((point) => [point.expenditure.lowerKcal, point.expenditure.upperKcal])
        .filter((value): value is number => value != null)
    const min = Math.min(...values) - 50
    const max = Math.max(...values) + 50
    const y = (value: number) => 100 - ((value - min) / (max - min || 1)) * 100
    const x = (index: number) => (index / (usable.length - 1)) * 100
    const line = usable
        .map(
            (point, index) =>
                `${index ? 'L' : 'M'} ${x(index)} ${y(point.expenditure.suggestedKcal ?? 0)}`,
        )
        .join(' ')
    const band = [
        ...usable.map(
            (point, index) =>
                `${x(index)},${y(point.expenditure.upperKcal ?? point.expenditure.suggestedKcal ?? 0)}`,
        ),
        ...[...usable]
            .reverse()
            .map(
                (point, reverseIndex) =>
                    `${x(usable.length - 1 - reverseIndex)},${y(point.expenditure.lowerKcal ?? point.expenditure.suggestedKcal ?? 0)}`,
            ),
    ].join(' ')
    const latest = usable.at(-1)
    if (!latest) return null
    return (
        <div className="uncertainty-chart uncertainty-chart--energy">
            <div className="chart-summary">
                <span>90-day expenditure model</span>
                <b>{formatNumber(latest.expenditure.suggestedKcal, 'kcal')}</b>
            </div>
            <svg
                viewBox="0 0 100 100"
                preserveAspectRatio="none"
                role="img"
                aria-label={`Estimated expenditure ${formatNumber(latest.expenditure.suggestedKcal, 'calories')}, likely range ${formatNumber(latest.expenditure.lowerKcal)} to ${formatNumber(latest.expenditure.upperKcal)}`}
            >
                <polygon className="chart-band" points={band} />
                <path className="chart-line" d={line} />
            </svg>
            <small>
                {latest.expenditure.modelState.toLowerCase()} ·{' '}
                {latest.expenditure.confidence.toLowerCase()} confidence
            </small>
        </div>
    )
}

function progressRange() {
    const end = new Date(`${today()}T12:00:00`)
    const start = new Date(end)
    start.setDate(start.getDate() - 89)
    return { from: start.toISOString().slice(0, 10), to: today() }
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
