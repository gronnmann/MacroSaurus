import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarCheck, Check, Gauge, Scale, Utensils } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { DecimalInput } from '../components/decimal-input'
import {
    Badge,
    Button,
    Card,
    ErrorPanel,
    Field,
    PageHeader,
    SectionHeader,
    Skeleton,
    StatePanel,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatDate, formatNumber, parseDecimal } from '../lib/utils'
import type { NutritionDayStatus } from '../types'

export function CheckInPage() {
    const client = useQueryClient()
    const navigate = useNavigate()
    const checkIn = useQuery({ queryKey: queryKeys.checkIn, queryFn: api.currentCheckIn })
    const review = useMutation({
        mutationFn: ({
            date,
            status,
            estimatedTotalKcal,
        }: {
            date: string
            status: NutritionDayStatus
            estimatedTotalKcal?: number
        }) => api.reviewNutritionDay(date, { status, estimatedTotalKcal }),
        onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.checkIn }),
    })
    const addWeight = useMutation({
        mutationFn: api.addWeight,
        onSuccess: async () => {
            await client.invalidateQueries({ queryKey: queryKeys.checkIn })
            await client.invalidateQueries({ queryKey: queryKeys.weights })
        },
    })
    const refresh = useMutation({
        mutationFn: (id: string) => api.refreshCheckIn(id),
        onSuccess: (data) => client.setQueryData(queryKeys.checkIn, data),
    })
    const finish = useMutation({
        mutationFn: ({ id, skip }: { id: string; skip: boolean }) =>
            skip ? api.skipCheckIn(id) : api.acceptCheckIn(id),
        onSuccess: async () => {
            await Promise.all([
                client.invalidateQueries({ queryKey: queryKeys.checkIn }),
                client.invalidateQueries({ queryKey: queryKeys.coachingStatus }),
                client.invalidateQueries({ queryKey: ['resolved-goals'] }),
                client.invalidateQueries({ queryKey: queryKeys.expenditure }),
            ])
            navigate('/dashboard')
        },
    })
    if (checkIn.isLoading) return <Skeleton lines={8} />
    if (checkIn.error || !checkIn.data) return <ErrorPanel error={checkIn.error} />
    const data = checkIn.data
    if (!data.due)
        return (
            <StatePanel
                kind="success"
                title="You’re checked in"
                message="Your next review will be available on Monday."
                action={<Button onClick={() => navigate('/dashboard')}>Back to Dashboard</Button>}
            />
        )
    const unresolved = data.candidates.filter((candidate) => !candidate.review)
    return (
        <>
            <PageHeader
                eyebrow="MONDAY CHECK-IN"
                title="Update your week"
                description={`${data.periodFrom} to ${data.periodTo} · review the data first, then decide whether to update.`}
            />
            <div className="checkin-stack">
                <Card>
                    <SectionHeader
                        eyebrow="1 · NUTRITION"
                        title="Fill the gaps"
                        aside={<Utensils />}
                    />
                    {data.candidates.length === 0 ? (
                        <p className="module-complete">
                            <Check /> No missing or suspicious days found.
                        </p>
                    ) : (
                        <div className="review-days">
                            {data.candidates.map((candidate) => (
                                <ReviewDay
                                    key={candidate.date}
                                    candidate={candidate}
                                    busy={review.isPending}
                                    onReview={(status, estimatedTotalKcal) =>
                                        review.mutate({
                                            date: candidate.date,
                                            status,
                                            estimatedTotalKcal,
                                        })
                                    }
                                />
                            ))}
                        </div>
                    )}
                    <p className="privacy-note">
                        Estimated totals affect coaching only. They do not create fake foods or
                        macro entries.
                    </p>
                </Card>
                <Card>
                    <SectionHeader
                        eyebrow="2 · WEIGH-IN"
                        title="Give the trend a fresh point"
                        aside={<Scale />}
                    />
                    {data.needsWeight ? (
                        <WeightPrompt
                            pending={addWeight.isPending}
                            onSubmit={(weightKg) =>
                                addWeight.mutate({ weightKg, note: 'Weekly check-in' })
                            }
                        />
                    ) : (
                        <p className="module-complete">
                            <Check /> A recent weigh-in is already included.
                        </p>
                    )}
                </Card>
                <Card tone="dark">
                    <SectionHeader
                        eyebrow="3 · PROGRAM"
                        title="Your update"
                        aside={<CalendarCheck />}
                    />
                    {!data.proposal ? (
                        <>
                            <p>
                                Once the flagged days are reviewed, Macrosaurus will recalculate
                                expenditure and prepare the coming week.
                            </p>
                            <Button
                                variant="secondary"
                                disabled={unresolved.length > 0 || refresh.isPending}
                                onClick={() => data.id && refresh.mutate(data.id)}
                            >
                                {refresh.isPending ? 'Calculating…' : 'Calculate update'}
                            </Button>
                        </>
                    ) : (
                        <Proposal proposal={data.proposal} />
                    )}
                    {(review.error || addWeight.error || refresh.error || finish.error) && (
                        <ErrorPanel
                            error={review.error || addWeight.error || refresh.error || finish.error}
                        />
                    )}
                    <div className="checkin-actions">
                        <Button
                            variant="ghost"
                            disabled={finish.isPending}
                            onClick={() => data.id && finish.mutate({ id: data.id, skip: true })}
                        >
                            Skip this week
                        </Button>
                        {data.proposal && (
                            <Button
                                disabled={finish.isPending}
                                onClick={() =>
                                    data.id && finish.mutate({ id: data.id, skip: false })
                                }
                            >
                                {finish.isPending
                                    ? 'Saving…'
                                    : data.proposal.targetUpdateAvailable
                                      ? 'Accept new targets'
                                      : 'Finish check-in'}
                            </Button>
                        )}
                    </div>
                </Card>
            </div>
        </>
    )
}

function ReviewDay({
    candidate,
    busy,
    onReview,
}: {
    candidate: Awaited<ReturnType<typeof api.currentCheckIn>>['candidates'][number]
    busy: boolean
    onReview: (status: NutritionDayStatus, estimate?: number) => void
}) {
    const [estimate, setEstimate] = useState(candidate.loggedEnergyKcal || 0)
    return (
        <article className={candidate.review ? 'review-day reviewed' : 'review-day'}>
            <div>
                <b>{formatDate(`${candidate.date}T12:00:00`)}</b>
                <span>
                    {candidate.reason === 'MISSING'
                        ? 'No nutrition logged'
                        : `Only ${formatNumber(candidate.loggedEnergyKcal, 'kcal')} logged`}
                </span>
            </div>
            {candidate.review ? (
                <Badge tone="green">{candidate.review.status.replaceAll('_', ' ')}</Badge>
            ) : (
                <div className="review-actions">
                    {candidate.entryCount > 0 && (
                        <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => onReview('CONFIRMED_COMPLETE')}
                        >
                            That is complete
                        </Button>
                    )}
                    <label htmlFor={`estimate-${candidate.date}`}>
                        <span>Estimated day total</span>
                        <DecimalInput
                            id={`estimate-${candidate.date}`}
                            value={estimate}
                            onValue={(value) => setEstimate(value ?? 0)}
                        />
                        <Button
                            variant="secondary"
                            disabled={busy}
                            onClick={() => onReview('ESTIMATED_TOTAL', estimate)}
                        >
                            Use estimate
                        </Button>
                    </label>
                    <Button variant="ghost" disabled={busy} onClick={() => onReview('EXCLUDED')}>
                        Exclude
                    </Button>
                    <Button variant="ghost" disabled={busy} onClick={() => onReview('FASTING')}>
                        Intentional fast
                    </Button>
                </div>
            )}
        </article>
    )
}

function WeightPrompt({
    pending,
    onSubmit,
}: {
    pending: boolean
    onSubmit: (weight: number) => void
}) {
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        onSubmit(parseDecimal(data.get('weight')))
    }
    return (
        <form className="weight-form" onSubmit={submit}>
            <Field label="Weight (kg)">
                <input name="weight" type="text" inputMode="decimal" required />
            </Field>
            <Button type="submit" disabled={pending}>
                {pending ? 'Saving…' : 'Add weigh-in'}
            </Button>
        </form>
    )
}

function Proposal({
    proposal,
}: {
    proposal: NonNullable<Awaited<ReturnType<typeof api.currentCheckIn>>['proposal']>
}) {
    return (
        <div className="checkin-proposal">
            <div className="estimate-strip">
                <Gauge />
                <span>Expenditure</span>
                <strong>{formatNumber(proposal.estimate.suggestedKcal, 'kcal/day')}</strong>
                <small>
                    {formatNumber(proposal.estimate.lowerKcal)}–
                    {formatNumber(proposal.estimate.upperKcal)} kcal ·{' '}
                    {proposal.estimate.confidence.toLowerCase()} confidence
                </small>
            </div>
            <div className="proposal-compare">
                <div>
                    <span>Current</span>
                    <b>{formatNumber(proposal.previousEnergyKcal, 'kcal')}</b>
                </div>
                <div>
                    <span>{proposal.targetUpdateAvailable ? 'Proposed' : 'Stays at'}</span>
                    <b>{formatNumber(proposal.proposedEnergyKcal, 'kcal')}</b>
                </div>
            </div>
            {proposal.proposedProteinG != null && (
                <div className="goal-preview">
                    <div>
                        <b>{formatNumber(proposal.proposedProteinG)} g</b>
                        <span>protein</span>
                    </div>
                    <div>
                        <b>{formatNumber(proposal.proposedCarbohydrateG)} g</b>
                        <span>carbs</span>
                    </div>
                    <div>
                        <b>{formatNumber(proposal.proposedFatG)} g</b>
                        <span>fat</span>
                    </div>
                </div>
            )}
            {proposal.warnings.map((warning) => (
                <p className="estimate-note" key={warning}>
                    {warning}
                </p>
            ))}
        </div>
    )
}
