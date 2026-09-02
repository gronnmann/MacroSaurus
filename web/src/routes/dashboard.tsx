import { useQuery } from '@tanstack/react-query'
import {
    ArrowRight,
    CalendarCheck,
    ChevronLeft,
    ChevronRight,
    Flame,
    NotebookTabs,
    Scale,
    Utensils,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { NutrientFacts, Progress } from '../components/nutrition'
import { Button, Card, ErrorPanel, SectionHeader, Skeleton, StatePanel } from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber, kcal, today, localDate as toLocalDate } from '../lib/utils'
import type { DiaryDay, ResolvedGoal, Weight } from '../types'

const nutrients = [
    { code: 'protein_g', label: 'Protein', unit: 'g', color: 'orange' },
    { code: 'fat_g', label: 'Fat', unit: 'g', color: 'teal' },
    { code: 'carbohydrate_g', label: 'Carbs', unit: 'g', color: 'green' },
] as const

export function DashboardPage() {
    const [params, setParams] = useSearchParams()
    const selected = params.get('date') || today()
    const [mode, setMode] = useState<'consumed' | 'remaining'>('consumed')
    const activityFrom = useMemo(() => addDays(selected, -29), [selected])
    const days = useQuery({
        queryKey: queryKeys.diaryRange(activityFrom, selected),
        queryFn: () => api.diaryRange(activityFrom, selected),
    })
    const goals = useQuery({
        queryKey: queryKeys.resolvedGoals(selected),
        queryFn: () => api.resolvedGoals(selected),
    })
    const weights = useQuery({
        queryKey: queryKeys.weights,
        queryFn: () => api.weights(500),
    })
    const targets = useQuery({ queryKey: queryKeys.targets, queryFn: api.targets })
    const definitions = useQuery({ queryKey: queryKeys.nutrients, queryFn: api.nutrients })
    const coaching = useQuery({ queryKey: queryKeys.coachingStatus, queryFn: api.coachingStatus })
    const day = days.data?.find((item) => item.date === selected)
    const goal = goals.data?.find((item) => item.date === selected)
    const targetMap = resolvedTargetMap(goal, targets.data)
    const moveDay = (amount: number) => setParams({ date: addDays(selected, amount) })

    return (
        <>
            <header className="dashboard-header">
                <div>
                    <p className="eyebrow">{formatDay(selected)}</p>
                    <h1>Dashboard</h1>
                    <p>A clear nutrition view, backed by the habits that move you forward.</p>
                </div>
                <DateNavigator
                    selected={selected}
                    onPrevious={() => moveDay(-1)}
                    onNext={() => moveDay(1)}
                    onSelect={(date) => setParams({ date })}
                />
            </header>

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
                <Skeleton lines={7} />
            ) : days.error || goals.error ? (
                <ErrorPanel error={days.error || goals.error} />
            ) : (
                <>
                    <DailyNutrition day={day} targets={targetMap} mode={mode} onMode={setMode} />
                    {goal?.warnings.map((warning) => (
                        <p className="goal-warning" key={warning}>
                            {warning} <Link to="/profile#nutrition-goals">Review goals</Link>
                        </p>
                    ))}

                    <section className="dashboard-habits" aria-labelledby="dashboard-habits-title">
                        <header className="section-header">
                            <div>
                                <p className="eyebrow">CONSISTENCY</p>
                                <h2 id="dashboard-habits-title">Habits</h2>
                            </div>
                        </header>
                        {weights.isLoading ? (
                            <Skeleton lines={4} />
                        ) : weights.error ? (
                            <ErrorPanel error={weights.error} />
                        ) : (
                            <div className="habit-grid">
                                <HabitCard
                                    title="Weigh-in"
                                    icon={Scale}
                                    color="green"
                                    selected={selected}
                                    activeDates={weightDates(weights.data || [])}
                                    to="/progress"
                                />
                                <HabitCard
                                    title="Food logging"
                                    icon={NotebookTabs}
                                    color="orange"
                                    selected={selected}
                                    activeDates={foodLogDates(days.data || [])}
                                    to={`/food-log?date=${selected}`}
                                />
                            </div>
                        )}
                    </section>

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

function DateNavigator({
    selected,
    onPrevious,
    onNext,
    onSelect,
}: {
    selected: string
    onPrevious: () => void
    onNext: () => void
    onSelect: (date: string) => void
}) {
    return (
        <div className="dashboard-date-navigation">
            <Button variant="ghost" aria-label="Previous day" onClick={onPrevious}>
                <ChevronLeft />
            </Button>
            <label>
                <span className="sr-only">Dashboard date</span>
                <input
                    type="date"
                    value={selected}
                    onChange={(event) => onSelect(event.target.value)}
                    aria-label="Dashboard date"
                />
            </label>
            <Button variant="ghost" aria-label="Next day" onClick={onNext}>
                <ChevronRight />
            </Button>
            {selected !== today() && (
                <button className="dashboard-today" type="button" onClick={() => onSelect(today())}>
                    Today
                </button>
            )}
        </div>
    )
}

function DailyNutrition({
    day,
    targets,
    mode,
    onMode,
}: {
    day?: DiaryDay
    targets: Record<string, number>
    mode: 'consumed' | 'remaining'
    onMode: (mode: 'consumed' | 'remaining') => void
}) {
    const consumedEnergy = day?.totals.energy_kcal || 0
    const energyTarget = targets.energy_kcal
    const energyDifference = energyTarget == null ? undefined : energyTarget - consumedEnergy
    const remainingEnergy = energyDifference == null ? undefined : Math.max(0, energyDifference)
    const shownEnergy = mode === 'consumed' ? consumedEnergy : remainingEnergy
    const progress = energyTarget ? Math.min(1, consumedEnergy / energyTarget) : 0
    const circumference = 2 * Math.PI * 44

    return (
        <Card className="daily-nutrition-card">
            <SectionHeader eyebrow="DAILY TOTAL" title="Daily nutrition" />
            <div className="energy-summary">
                <div className="energy-side-stat">
                    <strong>
                        {formatNumber(mode === 'consumed' ? remainingEnergy : consumedEnergy)}
                    </strong>
                    <span>{mode === 'consumed' ? 'Remaining' : 'Consumed'}</span>
                </div>
                <div className="energy-ring">
                    <svg
                        viewBox="0 0 100 100"
                        role="img"
                        aria-label={
                            energyTarget
                                ? `${formatNumber(consumedEnergy)} of ${formatNumber(energyTarget)} calories consumed`
                                : `${formatNumber(consumedEnergy)} calories consumed; no target set`
                        }
                    >
                        <circle className="energy-ring-track" cx="50" cy="50" r="44" />
                        <circle
                            className="energy-ring-value"
                            cx="50"
                            cy="50"
                            r="44"
                            style={{
                                strokeDasharray: circumference,
                                strokeDashoffset: circumference * (1 - progress),
                            }}
                        />
                    </svg>
                    <div>
                        <strong>{shownEnergy == null ? '—' : formatNumber(shownEnergy)}</strong>
                        <span>{mode === 'consumed' ? 'Consumed' : 'Remaining'}</span>
                        {energyDifference != null && energyDifference < 0 && (
                            <em>{formatNumber(Math.abs(energyDifference))} over</em>
                        )}
                    </div>
                </div>
                <div className="energy-side-stat">
                    <strong>{formatNumber(energyTarget)}</strong>
                    <span>Target</span>
                    {energyTarget == null && <Link to="/profile#nutrition-goals">Set goal</Link>}
                </div>
            </div>
            <div className="daily-nutrient-bars">
                {nutrients.map((item) => {
                    const consumed = day?.totals[item.code] || 0
                    const target = targets[item.code]
                    const difference = target == null ? undefined : target - consumed
                    const shown =
                        mode === 'consumed'
                            ? consumed
                            : difference == null
                              ? undefined
                              : Math.max(0, difference)
                    return (
                        <div
                            className={`daily-nutrient daily-nutrient--${item.color}`}
                            key={item.code}
                        >
                            <span>{item.label}</span>
                            {target == null ? (
                                <Link to="/profile#nutrition-goals">Set goal</Link>
                            ) : (
                                <>
                                    <Progress value={consumed} max={target} color={item.color} />
                                    <b>
                                        {formatNumber(shown)} / {formatNumber(target)} {item.unit}
                                    </b>
                                    {difference != null && difference < 0 && (
                                        <em>
                                            {formatNumber(Math.abs(difference), item.unit)} over
                                        </em>
                                    )}
                                </>
                            )}
                        </div>
                    )
                })}
            </div>
            <fieldset className="macro-mode daily-nutrition-mode">
                <legend className="sr-only">Nutrient display</legend>
                <button
                    type="button"
                    className={mode === 'consumed' ? 'active' : ''}
                    onClick={() => onMode('consumed')}
                >
                    Consumed
                </button>
                <button
                    type="button"
                    className={mode === 'remaining' ? 'active' : ''}
                    onClick={() => onMode('remaining')}
                >
                    Remaining
                </button>
            </fieldset>
        </Card>
    )
}

function HabitCard({
    title,
    icon: Icon,
    color,
    selected,
    activeDates,
    to,
}: {
    title: string
    icon: typeof Scale
    color: 'green' | 'orange'
    selected: string
    activeDates: Set<string>
    to: string
}) {
    const dates = Array.from({ length: 30 }, (_, index) => addDays(selected, index - 29))
    const weekStart = startOfWeek(selected)
    const completedThisWeek = dates.filter(
        (date) => date >= weekStart && date <= selected && activeDates.has(date),
    ).length
    return (
        <Link
            className={`card habit-card habit-card--${color}`}
            to={to}
            aria-label={`${title}: ${completedThisWeek} of 7 days completed this week`}
        >
            <header>
                <span>
                    <Icon />
                </span>
                <div>
                    <h3>{title}</h3>
                    <p>Last 30 days</p>
                </div>
            </header>
            <div className="habit-calendar" aria-hidden="true">
                {dates.map((date) => (
                    <i
                        className={`${activeDates.has(date) ? 'complete' : ''} ${date === selected ? 'selected' : ''}`}
                        key={date}
                    />
                ))}
            </div>
            <footer>
                <span>
                    <b>{completedThisWeek}/7</b> this week
                </span>
                <ChevronRight />
            </footer>
        </Link>
    )
}

function foodLogDates(days: DiaryDay[]) {
    return new Set(days.filter((day) => day.entries.length > 0).map((day) => day.date))
}

function weightDates(weights: Weight[]) {
    return new Set(weights.map((weight) => toLocalDate(new Date(weight.measuredAt))))
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

const dateAtNoon = (value: string) => new Date(`${value}T12:00:00`)
const addDays = (value: string, amount: number) => {
    const date = dateAtNoon(value)
    date.setDate(date.getDate() + amount)
    return toLocalDate(date)
}
const startOfWeek = (value: string) => {
    const date = dateAtNoon(value)
    return addDays(value, -((date.getDay() + 6) % 7))
}
const formatDay = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
    }).format(dateAtNoon(value))
const formatTime = (value: string) =>
    new Intl.DateTimeFormat(undefined, {
        hour: '2-digit',
        minute: '2-digit',
    }).format(new Date(value))
