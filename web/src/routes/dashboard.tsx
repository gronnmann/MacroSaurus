import { useQuery } from '@tanstack/react-query'
import { ArrowRight, CalendarCheck, ChevronLeft, ChevronRight, Flame, Utensils } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { NutrientFacts, Progress } from '../components/nutrition'
import {
    Button,
    Card,
    ErrorPanel,
    PageHeader,
    SectionHeader,
    Skeleton,
    StatePanel,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber, kcal, round, today } from '../lib/utils'
import type { DiaryDay, ResolvedGoal } from '../types'

const primary = [
    { code: 'energy_kcal', label: 'Calories', unit: 'kcal', color: 'orange' },
    { code: 'protein_g', label: 'Protein', unit: 'g', color: 'green' },
    { code: 'carbohydrate_g', label: 'Carbs', unit: 'g', color: 'orange' },
    { code: 'fat_g', label: 'Fat', unit: 'g', color: 'teal' },
] as const

export function DashboardPage() {
    const [params, setParams] = useSearchParams()
    const selected = params.get('date') || today()
    const [mode, setMode] = useState<'consumed' | 'remaining'>('consumed')
    const week = useMemo(() => weekFor(selected), [selected])
    const days = useQuery({
        queryKey: ['diary-week', week.from, week.to],
        queryFn: () => api.diaryRange(week.from, week.to),
    })
    const goals = useQuery({
        queryKey: queryKeys.resolvedGoals(week.from, week.to),
        queryFn: () => api.resolvedGoals(week.from, week.to),
    })
    const targets = useQuery({
        queryKey: queryKeys.targets,
        queryFn: api.targets,
    })
    const definitions = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
    })
    const coaching = useQuery({ queryKey: queryKeys.coachingStatus, queryFn: api.coachingStatus })
    const day = days.data?.find((item) => item.date === selected)
    const goal = goals.data?.find((item) => item.date === selected)
    const targetMap = resolvedTargetMap(goal, targets.data)
    const moveWeek = (amount: number) => setParams({ date: addDays(selected, amount * 7) })

    return (
        <>
            <PageHeader
                eyebrow="YOUR WEEK"
                title={selected === today() ? 'Today' : formatDay(selected)}
                description="One day at a time. The whole week stays in view."
                actions={
                    <div className="date-switcher">
                        <Button
                            variant="ghost"
                            aria-label="Previous week"
                            onClick={() => moveWeek(-1)}
                        >
                            <ChevronLeft />
                        </Button>
                        <span>{formatRange(week.from, week.to)}</span>
                        <Button variant="ghost" aria-label="Next week" onClick={() => moveWeek(1)}>
                            <ChevronRight />
                        </Button>
                    </div>
                }
            />
            {coaching.data?.checkInDue && (
                <Card className="checkin-callout" tone="orange">
                    <CalendarCheck />
                    <div>
                        <b>Your Monday check-in is ready</b>
                        <span>
                            Review last week’s data and decide whether to update your targets.
                        </span>
                    </div>
                    <Link className="button button--primary" to="/check-in">
                        Check in <ArrowRight />
                    </Link>
                </Card>
            )}
            {days.isLoading || goals.isLoading ? (
                <Skeleton lines={6} />
            ) : days.error || goals.error ? (
                <ErrorPanel error={days.error || goals.error} />
            ) : (
                <>
                    <WeekOverview
                        days={days.data || []}
                        goals={goals.data || []}
                        nutrientTargets={targets.data}
                        selected={selected}
                        onSelect={(date) => setParams({ date })}
                    />
                    <fieldset className="macro-mode">
                        <legend className="sr-only">Nutrient display</legend>
                        <button
                            type="button"
                            className={mode === 'consumed' ? 'active' : ''}
                            onClick={() => setMode('consumed')}
                        >
                            Consumed
                        </button>
                        <button
                            type="button"
                            className={mode === 'remaining' ? 'active' : ''}
                            onClick={() => setMode('remaining')}
                        >
                            Remaining
                        </button>
                    </fieldset>
                    <div className="daily-macros">
                        {primary.map((item) => (
                            <MacroCard
                                key={item.code}
                                item={item}
                                consumed={day?.totals[item.code] || 0}
                                target={targetMap[item.code]}
                                mode={mode}
                            />
                        ))}
                    </div>
                    {goal?.warnings.map((warning) => (
                        <p className="goal-warning" key={warning}>
                            {warning} <Link to="/profile#nutrition-goals">Review goals</Link>
                        </p>
                    ))}
                    <div className="dashboard-app-grid">
                        <Card>
                            <SectionHeader
                                eyebrow="LATEST"
                                title="Recent entries"
                                aside={
                                    <Link className="text-link" to={`/food-log?date=${selected}`}>
                                        View food log <ArrowRight />
                                    </Link>
                                }
                            />
                            {day?.entries.length ? (
                                <div className="recent-entries">
                                    {[...day.entries]
                                        .sort((a, b) => b.consumedAt.localeCompare(a.consumedAt))
                                        .slice(0, 4)
                                        .map((entry) => (
                                            <article key={entry.id}>
                                                <span>
                                                    <Utensils />
                                                </span>
                                                <div>
                                                    <b>{entry.displayName}</b>
                                                    <small>
                                                        {formatTime(entry.consumedAt)} ·{' '}
                                                        {entry.quantity
                                                            ? `${formatNumber(entry.quantity)} ${entry.unit}`
                                                            : 'Quick entry'}
                                                    </small>
                                                </div>
                                                <strong>{kcal(entry.nutrients)} kcal</strong>
                                            </article>
                                        ))}
                                </div>
                            ) : (
                                <StatePanel
                                    compact
                                    title="Nothing logged yet"
                                    message="Use Track when you are ready."
                                />
                            )}
                        </Card>
                        <Card tone="dark">
                            <SectionHeader
                                eyebrow="ENERGY"
                                title={
                                    goal?.energyKcal
                                        ? `${formatNumber(goal.energyKcal)} kcal target`
                                        : 'Set your calorie goal'
                                }
                                aside={<Flame />}
                            />
                            <p>
                                {goal?.expenditureKcal
                                    ? `Current expenditure estimate: ${formatNumber(goal.expenditureKcal)} kcal.`
                                    : 'Complete your profile and add a weigh-in to calculate an estimate.'}
                            </p>
                            <Link
                                className="button button--secondary"
                                to="/profile#nutrition-goals"
                            >
                                {goal?.energyKcal ? 'Adjust goals' : 'Set goals'}
                            </Link>
                        </Card>
                    </div>
                    <Card className="micronutrient-card">
                        <SectionHeader
                            eyebrow="THE FULL PICTURE"
                            title="Vitamins, minerals & more"
                            aside="Scroll for details"
                        />
                        <NutrientFacts
                            nutrients={day?.totals || {}}
                            definitions={definitions.data}
                            targets={targetMap}
                        />
                    </Card>
                </>
            )}
        </>
    )
}

function WeekOverview({
    days,
    goals,
    nutrientTargets,
    selected,
    onSelect,
}: {
    days: DiaryDay[]
    goals: ResolvedGoal[]
    nutrientTargets?: Array<{ nutrientCode: string; targetAmount?: number }>
    selected: string
    onSelect: (date: string) => void
}) {
    const selectedDay = days.find((day) => day.date === selected)
    const selectedGoal = goals.find((goal) => goal.date === selected)
    const selectedTargets = resolvedTargetMap(selectedGoal, nutrientTargets)
    return (
        <Card className="week-overview">
            <fieldset className="week-chart">
                <legend className="sr-only">Weekly nutrition overview</legend>
                {days.map((day) => {
                    const goal = goals.find((item) => item.date === day.date)
                    const dayTargets = resolvedTargetMap(goal, nutrientTargets)
                    const isToday = day.date === today()
                    return (
                        <button
                            type="button"
                            key={day.date}
                            className={`${day.date === selected ? 'selected' : ''} ${isToday ? 'today' : ''}`}
                            onClick={() => onSelect(day.date)}
                            aria-pressed={day.date === selected}
                            aria-label={`${formatDay(day.date)}, ${kcal(day.totals)} calories`}
                        >
                            <div className="week-bars" aria-hidden="true">
                                {primary.map((item) => {
                                    const consumed = day.totals[item.code] || 0
                                    const target = dayTargets[item.code]
                                    const percent = target
                                        ? Math.min(100, (consumed / target) * 100)
                                        : 0
                                    return (
                                        <span
                                            className={`week-bar week-bar--${item.color}`}
                                            key={item.code}
                                        >
                                            <i style={{ height: `${percent}%` }} />
                                        </span>
                                    )
                                })}
                            </div>
                            <small>
                                {new Intl.DateTimeFormat(undefined, { weekday: 'narrow' }).format(
                                    localDate(day.date),
                                )}
                            </small>
                            <b>{Number(day.date.slice(-2))}</b>
                        </button>
                    )
                })}
            </fieldset>
            <div className="week-targets">
                <p>{selected === today() ? 'Today' : formatDay(selected)}</p>
                {primary.map((item) => {
                    const consumed = selectedDay?.totals[item.code] || 0
                    const target = selectedTargets[item.code]
                    return (
                        <div key={item.code}>
                            <span className={`target-swatch target-swatch--${item.color}`} />
                            <span>{item.label}</span>
                            <strong>{formatNumber(consumed)}</strong>
                            {target == null ? (
                                <Link to="/profile#nutrition-goals">Set target</Link>
                            ) : (
                                <b>
                                    / {formatNumber(target)} {item.unit}
                                </b>
                            )}
                        </div>
                    )
                })}
            </div>
        </Card>
    )
}

function MacroCard({
    item,
    consumed,
    target,
    mode,
}: {
    item: (typeof primary)[number]
    consumed: number
    target?: number
    mode: 'consumed' | 'remaining'
}) {
    const difference = target == null ? undefined : target - consumed
    const shown =
        mode === 'consumed' ? consumed : difference == null ? undefined : Math.max(0, difference)
    return (
        <Card className={`dashboard-macro dashboard-macro--${item.color}`}>
            <span>{item.label}</span>
            <strong>{shown == null ? '—' : formatNumber(shown)}</strong>
            <small>
                {shown == null ? <Link to="/profile#nutrition-goals">Set goal</Link> : item.unit}
            </small>
            {target != null && (
                <>
                    <Progress value={consumed} max={target} color={item.color} />
                    <p className="macro-target-reading">
                        <span>{round(consumed)} of</span>
                        <strong>
                            {round(target)} {item.unit}
                        </strong>
                        <small>target</small>
                    </p>
                    {difference != null && difference < 0 && (
                        <em>
                            {round(Math.abs(difference))} {item.unit} over
                        </em>
                    )}
                </>
            )}
        </Card>
    )
}

function resolvedTargetMap(
    goal?: ResolvedGoal,
    nutrientTargets?: Array<{ nutrientCode: string; targetAmount?: number }>,
) {
    const values: Record<string, number> = Object.fromEntries(
        (nutrientTargets || []).flatMap((item) =>
            item.targetAmount == null ? [] : [[item.nutrientCode, item.targetAmount]],
        ),
    )
    if (goal?.energyKcal != null) values.energy_kcal = goal.energyKcal
    if (goal?.proteinG != null) values.protein_g = goal.proteinG
    if (goal?.carbohydrateG != null) values.carbohydrate_g = goal.carbohydrateG
    if (goal?.fatG != null) values.fat_g = goal.fatG
    return values
}

const localDate = (value: string) => new Date(`${value}T12:00:00`)
const addDays = (value: string, amount: number) => {
    const date = localDate(value)
    date.setDate(date.getDate() + amount)
    return date.toISOString().slice(0, 10)
}
const weekFor = (value: string) => {
    const date = localDate(value)
    const offset = (date.getDay() + 6) % 7
    const from = addDays(value, -offset)
    return { from, to: addDays(from, 6) }
}
const formatDay = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
    }).format(localDate(value))
const formatRange = (from: string, to: string) =>
    `${new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(localDate(from))} – ${new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(localDate(to))}`
const formatTime = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
        hour: '2-digit',
        minute: '2-digit',
    }).format(new Date(value))
