import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Check, Gauge, Target, UserRound, Weight } from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Brand } from '../components/layout'
import { Badge, Button, Card, ErrorPanel, Field, Skeleton } from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { formatNumber } from '../lib/utils'
import type { CoachingSetupDraft, ProgramStyle, WeightGoalType } from '../types'

const steps = ['About you', 'Starting point', 'Goal', 'Program', 'Review']

export function SetupPage() {
    const navigate = useNavigate()
    const client = useQueryClient()
    const query = useQuery({ queryKey: queryKeys.setupDraft, queryFn: api.setupDraft })
    const [draft, setDraft] = useState<CoachingSetupDraft>()
    useEffect(() => {
        if (query.data) setDraft(query.data)
    }, [query.data])
    const save = useMutation({ mutationFn: api.saveSetupDraft })
    const complete = useMutation({
        mutationFn: api.completeSetup,
        onSuccess: async () => {
            client.removeQueries({ queryKey: queryKeys.setupDraft })
            await client.invalidateQueries({ queryKey: queryKeys.coachingStatus })
            await client.invalidateQueries({ queryKey: queryKeys.profile })
            await client.invalidateQueries({ queryKey: queryKeys.weights })
            await client.invalidateQueries({ queryKey: queryKeys.goals })
            navigate('/dashboard', { replace: true })
        },
    })
    const preview = useQuery({
        queryKey: ['setup-preview', draft],
        queryFn: () => {
            if (!draft) throw new Error('Setup draft is not ready')
            return api.previewSetup(draft)
        },
        enabled: Boolean(draft && draft.currentStep === 5),
        retry: false,
    })
    if (query.isLoading || !draft)
        return (
            <SetupFrame>
                <Skeleton lines={8} />
            </SetupFrame>
        )
    if (query.error)
        return (
            <SetupFrame>
                <ErrorPanel error={query.error} />
            </SetupFrame>
        )

    const update = <K extends keyof CoachingSetupDraft>(key: K, value: CoachingSetupDraft[K]) =>
        setDraft((current) => (current ? { ...current, [key]: value } : current))
    const move = async (step: number) => {
        const next = { ...draft, currentStep: step }
        setDraft(next)
        await save.mutateAsync(next)
    }
    const submit = (event: FormEvent) => {
        event.preventDefault()
        if (draft.currentStep < 5) void move(draft.currentStep + 1)
        else complete.mutate(draft)
    }

    return (
        <SetupFrame>
            <div
                className="setup-progress"
                role="progressbar"
                aria-label="Goal setup progress"
                aria-valuemin={1}
                aria-valuemax={5}
                aria-valuenow={draft.currentStep}
            >
                {steps.map((label, index) => (
                    <span className={index + 1 <= draft.currentStep ? 'active' : ''} key={label}>
                        <i>{index + 1 < draft.currentStep ? <Check /> : index + 1}</i>
                        <b>{label}</b>
                    </span>
                ))}
            </div>
            <form onSubmit={submit}>
                <Card className="setup-card">
                    {draft.currentStep === 1 && <AboutStep draft={draft} update={update} />}
                    {draft.currentStep === 2 && <StartingStep draft={draft} update={update} />}
                    {draft.currentStep === 3 && <GoalStep draft={draft} update={update} />}
                    {draft.currentStep === 4 && <ProgramStep draft={draft} update={update} />}
                    {draft.currentStep === 5 && (
                        <ReviewStep
                            draft={draft}
                            loading={preview.isLoading}
                            error={preview.error}
                            preview={preview.data}
                        />
                    )}
                    {(save.error || complete.error) && (
                        <ErrorPanel error={save.error || complete.error} />
                    )}
                    <div className="setup-actions">
                        {draft.currentStep > 1 && (
                            <Button
                                type="button"
                                variant="ghost"
                                onClick={() => void move(draft.currentStep - 1)}
                            >
                                <ArrowLeft /> Back
                            </Button>
                        )}
                        <Button
                            type="submit"
                            disabled={
                                save.isPending ||
                                complete.isPending ||
                                (draft.currentStep === 5 && !preview.data)
                            }
                        >
                            {draft.currentStep === 5
                                ? complete.isPending
                                    ? 'Starting…'
                                    : 'Start my program'
                                : 'Continue'}
                            {draft.currentStep < 5 && <ArrowRight />}
                        </Button>
                    </div>
                </Card>
            </form>
        </SetupFrame>
    )
}

function SetupFrame({ children }: { children: React.ReactNode }) {
    return (
        <main className="setup-page">
            <header>
                <Brand />
                <Badge tone="green">GUIDED SETUP</Badge>
            </header>
            <div className="setup-inner">{children}</div>
        </main>
    )
}

type Update = <K extends keyof CoachingSetupDraft>(key: K, value: CoachingSetupDraft[K]) => void

function AboutStep({ draft, update }: { draft: CoachingSetupDraft; update: Update }) {
    return (
        <>
            <StepTitle
                icon={<UserRound />}
                eyebrow="FIRST THINGS FIRST"
                title="Tell your coach about you"
                copy="These details create a starting estimate. Your logged data will take over as the model learns."
            />
            <div className="form-grid">
                <Field label="Display name" className="span-2">
                    <input
                        required
                        value={draft.displayName || ''}
                        onChange={(e) => update('displayName', e.target.value)}
                    />
                </Field>
                <Field label="Birth date">
                    <input
                        required
                        type="date"
                        value={draft.birthDate || ''}
                        onChange={(e) => update('birthDate', e.target.value)}
                    />
                </Field>
                <Field label="Height (cm)">
                    <input
                        required
                        type="number"
                        min="30"
                        max="300"
                        step="0.1"
                        value={draft.heightCm ?? ''}
                        onChange={(e) => update('heightCm', number(e.target.value))}
                    />
                </Field>
                <Field label="Formula used for energy">
                    <select
                        required
                        value={draft.formulaSex || ''}
                        onChange={(e) => update('formulaSex', e.target.value as 'MALE' | 'FEMALE')}
                    >
                        <option value="">Choose one</option>
                        <option value="MALE">Male formula</option>
                        <option value="FEMALE">Female formula</option>
                    </select>
                </Field>
                <Field label="Timezone">
                    <input
                        required
                        value={draft.timezone}
                        onChange={(e) => update('timezone', e.target.value)}
                    />
                </Field>
            </div>
            <p className="privacy-note">
                Macrosaurus coaching is for adults and provides wellness guidance, not medical
                advice.
            </p>
        </>
    )
}

function StartingStep({ draft, update }: { draft: CoachingSetupDraft; update: Update }) {
    return (
        <>
            <StepTitle
                icon={<Weight />}
                eyebrow="STARTING POINT"
                title="Where are you today?"
                copy="Use a current, ordinary weigh-in. Daily fluctuations are expected and will be smoothed."
            />
            <div className="form-grid">
                <Field label="Current weight (kg)">
                    <input
                        required
                        type="number"
                        min="10"
                        max="700"
                        step="0.1"
                        value={draft.weightKg ?? ''}
                        onChange={(e) => update('weightKg', number(e.target.value))}
                    />
                </Field>
                <Field label="Usual activity">
                    <select
                        value={draft.activityMultiplier}
                        onChange={(e) => update('activityMultiplier', Number(e.target.value))}
                    >
                        <option value="1.2">Sedentary</option>
                        <option value="1.375">Lightly active</option>
                        <option value="1.55">Moderately active</option>
                        <option value="1.725">Very active</option>
                        <option value="1.9">Extremely active</option>
                    </select>
                </Field>
            </div>
        </>
    )
}

function GoalStep({ draft, update }: { draft: CoachingSetupDraft; update: Update }) {
    const choose = (goal: WeightGoalType) => {
        update('goalType', goal)
        update('weeklyRatePercent', goal === 'LOSS' ? 0.5 : goal === 'GAIN' ? 0.25 : 0)
        if (goal === 'MAINTAIN') update('targetWeightKg', undefined)
    }
    return (
        <>
            <StepTitle
                icon={<Target />}
                eyebrow="YOUR DIRECTION"
                title="What should the program support?"
                copy="Each week stands on its own. The coach will never punish you for missing an old target."
            />
            <div className="choice-grid">
                {(['LOSS', 'MAINTAIN', 'GAIN'] as const).map((goal) => (
                    <button
                        type="button"
                        className={draft.goalType === goal ? 'selected' : ''}
                        onClick={() => choose(goal)}
                        key={goal}
                    >
                        <b>
                            {goal === 'LOSS'
                                ? 'Lose weight'
                                : goal === 'GAIN'
                                  ? 'Gain weight'
                                  : 'Maintain weight'}
                        </b>
                        <span>
                            {goal === 'MAINTAIN'
                                ? 'Stay near your current trend'
                                : 'Set a target and a steady pace'}
                        </span>
                    </button>
                ))}
            </div>
            {draft.goalType && draft.goalType !== 'MAINTAIN' && (
                <div className="form-grid setup-subform">
                    <Field label="Target weight (kg)">
                        <input
                            required
                            type="number"
                            min="10"
                            max="700"
                            step="0.1"
                            value={draft.targetWeightKg ?? ''}
                            onChange={(e) => update('targetWeightKg', number(e.target.value))}
                        />
                    </Field>
                    <Field
                        label={`Weekly rate (${draft.goalType === 'LOSS' ? '0.25–1.0' : '0.10–0.50'}%)`}
                    >
                        <input
                            required
                            type="number"
                            min={draft.goalType === 'LOSS' ? 0.25 : 0.1}
                            max={draft.goalType === 'LOSS' ? 1 : 0.5}
                            step="0.05"
                            value={draft.weeklyRatePercent ?? ''}
                            onChange={(e) => update('weeklyRatePercent', number(e.target.value))}
                        />
                    </Field>
                </div>
            )}
        </>
    )
}

function ProgramStep({ draft, update }: { draft: CoachingSetupDraft; update: Update }) {
    return (
        <>
            <StepTitle
                icon={<Gauge />}
                eyebrow="PROGRAM STYLE"
                title="How much should the coach manage?"
                copy="Coached targets adjust after accepted Monday check-ins. Manual targets stay exactly where you set them."
            />
            <div className="choice-grid choice-grid--two">
                {(['COACHED', 'MANUAL'] as const).map((style: ProgramStyle) => (
                    <button
                        type="button"
                        className={draft.programStyle === style ? 'selected' : ''}
                        onClick={() => update('programStyle', style)}
                        key={style}
                    >
                        <b>{style === 'COACHED' ? 'Coached' : 'Manual'}</b>
                        <span>
                            {style === 'COACHED'
                                ? 'Adaptive calories and guided macros'
                                : 'Fixed calories and grams, insights only'}
                        </span>
                    </button>
                ))}
            </div>
            {draft.programStyle === 'COACHED' ? (
                <div className="form-grid setup-subform">
                    <Field label={`Protein · ${draft.proteinGPerKg.toFixed(1)} g/kg`}>
                        <input
                            type="range"
                            min="1.2"
                            max="2.2"
                            step="0.1"
                            value={draft.proteinGPerKg}
                            onChange={(e) => update('proteinGPerKg', Number(e.target.value))}
                        />
                    </Field>
                    <Field label={`Fat · ${draft.fatEnergyPercent}% of calories`}>
                        <input
                            type="range"
                            min="20"
                            max="40"
                            step="1"
                            value={draft.fatEnergyPercent}
                            onChange={(e) => update('fatEnergyPercent', Number(e.target.value))}
                        />
                    </Field>
                </div>
            ) : draft.programStyle === 'MANUAL' ? (
                <div className="form-grid setup-subform">
                    <Field label="Calories">
                        <input
                            required
                            type="number"
                            min="1"
                            value={draft.manualEnergyKcal ?? ''}
                            onChange={(e) => update('manualEnergyKcal', number(e.target.value))}
                        />
                    </Field>
                    <Field label="Protein (g)">
                        <input
                            required
                            type="number"
                            min="0"
                            value={draft.manualProteinG ?? ''}
                            onChange={(e) => update('manualProteinG', number(e.target.value))}
                        />
                    </Field>
                    <Field label="Carbohydrate (g)">
                        <input
                            required
                            type="number"
                            min="0"
                            value={draft.manualCarbohydrateG ?? ''}
                            onChange={(e) => update('manualCarbohydrateG', number(e.target.value))}
                        />
                    </Field>
                    <Field label="Fat (g)">
                        <input
                            required
                            type="number"
                            min="0"
                            value={draft.manualFatG ?? ''}
                            onChange={(e) => update('manualFatG', number(e.target.value))}
                        />
                    </Field>
                </div>
            ) : null}
        </>
    )
}

function ReviewStep({
    loading,
    error,
    preview,
}: {
    draft: CoachingSetupDraft
    loading: boolean
    error: unknown
    preview?: Awaited<ReturnType<typeof api.previewSetup>>
}) {
    return (
        <>
            <StepTitle
                icon={<Check />}
                eyebrow="READY TO START"
                title="Your first program"
                copy="This is a starting point. Weight and reviewed nutrition will make it personal over the next few weeks."
            />
            {loading ? (
                <Skeleton lines={6} />
            ) : error ? (
                <ErrorPanel error={error} />
            ) : (
                preview && (
                    <>
                        <div className="setup-estimate">
                            <span>Estimated expenditure</span>
                            <strong>
                                {formatNumber(preview.expenditure.suggestedKcal, 'kcal/day')}
                            </strong>
                            <small>
                                {formatNumber(preview.expenditure.lowerKcal)}–
                                {formatNumber(preview.expenditure.upperKcal)} kcal uncertainty range
                            </small>
                        </div>
                        <div className="goal-preview">
                            <div>
                                <b>{formatNumber(preview.energyKcal)}</b>
                                <span>kcal</span>
                            </div>
                            <div>
                                <b>{formatNumber(preview.proteinG)} g</b>
                                <span>protein</span>
                            </div>
                            <div>
                                <b>{formatNumber(preview.carbohydrateG)} g</b>
                                <span>carbs</span>
                            </div>
                            <div>
                                <b>{formatNumber(preview.fatG)} g</b>
                                <span>fat</span>
                            </div>
                        </div>
                        {preview.estimatedCompletionDate && (
                            <p className="estimate-note">
                                Estimated goal date: <b>{preview.estimatedCompletionDate}</b>. This
                                moves with your trend; it is not a deadline.
                            </p>
                        )}
                        {preview.warnings.map((warning) => (
                            <p className="goal-warning" key={warning}>
                                {warning}
                            </p>
                        ))}
                    </>
                )
            )}
        </>
    )
}

function StepTitle({
    icon,
    eyebrow,
    title,
    copy,
}: {
    icon: React.ReactNode
    eyebrow: string
    title: string
    copy: string
}) {
    return (
        <header className="setup-title">
            <span>{icon}</span>
            <div>
                <p className="eyebrow">{eyebrow}</p>
                <h1>{title}</h1>
                <p>{copy}</p>
            </div>
        </header>
    )
}

function number(value: string) {
    return value === '' ? undefined : Number(value)
}
