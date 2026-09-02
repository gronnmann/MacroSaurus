import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarCheck, Dumbbell, LogOut, Settings, Target, UserRound } from 'lucide-react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
    Button,
    Card,
    ErrorPanel,
    Field,
    PageHeader,
    SectionHeader,
    Skeleton,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { useAppAuth } from '../lib/auth'
import { formatNumber, parseDecimal } from '../lib/utils'
import { NutrientGoals } from './goals'

export function ProfilePage() {
    const profile = useQuery({
        queryKey: queryKeys.profile,
        queryFn: api.profile,
    })
    const coaching = useQuery({ queryKey: queryKeys.coachingStatus, queryFn: api.coachingStatus })
    const profileData = profile.data
    return (
        <>
            <PageHeader
                eyebrow="PROFILE"
                title={profile.data?.displayName || 'Your profile'}
                description="Your details, goals, and preferences in one place."
            />
            <nav className="profile-jump" aria-label="Profile sections">
                <a href="#account">Account</a>
                <a href="#nutrition-goals">Goals</a>
                <a href="#preferences">Preferences</a>
            </nav>
            {profile.isLoading || coaching.isLoading ? (
                <Skeleton lines={10} />
            ) : profile.error || coaching.error || !profileData ? (
                <ErrorPanel error={profile.error || coaching.error} />
            ) : (
                <div className="profile-sections">
                    <AccountForm profile={profileData} />
                    <StrategyCard status={coaching.data} />
                    <section id="micronutrient-goals">
                        <SectionHeader
                            eyebrow="OPTIONAL"
                            title="Other nutrient goals"
                            aside={<Target />}
                        />
                        <NutrientGoals />
                    </section>
                    <Preferences profile={profileData} />
                </div>
            )}
        </>
    )
}

function StrategyCard({
    status,
}: {
    status: Awaited<ReturnType<typeof api.coachingStatus>> | undefined
}) {
    const goal = status?.goal
    const program = status?.program
    return (
        <Card id="nutrition-goals" tone="green">
            <SectionHeader
                eyebrow="GOAL & PROGRAM"
                title="Your current strategy"
                aside={<Dumbbell />}
            />
            <div className="strategy-summary">
                <div>
                    <span>Goal</span>
                    <b>
                        {goal
                            ? goal.type === 'LOSS'
                                ? 'Lose weight'
                                : goal.type === 'GAIN'
                                  ? 'Gain weight'
                                  : 'Maintain weight'
                            : 'Legacy program'}
                    </b>
                    {goal?.targetWeightKg && (
                        <small>
                            {goal.targetWeightKg} kg · {goal.weeklyRatePercent}% / week
                        </small>
                    )}
                </div>
                <div>
                    <span>Program</span>
                    <b>{program?.style === 'MANUAL' ? 'Manual' : 'Coached'}</b>
                    <small>Effective {program?.effectiveFrom || 'now'}</small>
                </div>
                <div>
                    <span>Daily target</span>
                    <b>{formatNumber(program?.energyKcal, 'kcal')}</b>
                    <small>
                        {formatNumber(program?.proteinG)}P · {formatNumber(program?.carbohydrateG)}C
                        · {formatNumber(program?.fatG)}F
                    </small>
                </div>
                <div>
                    <span>Next check-in</span>
                    <b>{status?.nextCheckInDate || 'After setup'}</b>
                    <small>Every Monday</small>
                </div>
            </div>
            <div className="strategy-actions">
                {status?.checkInDue && (
                    <Link className="button button--secondary" to="/check-in">
                        <CalendarCheck /> Check in now
                    </Link>
                )}
                <Link className="button button--primary" to="/setup">
                    Edit goal or program
                </Link>
            </div>
        </Card>
    )
}

function AccountForm({ profile }: { profile: Awaited<ReturnType<typeof api.profile>> }) {
    const client = useQueryClient()
    const toast = useToast()
    const save = useMutation({
        mutationFn: api.updateProfile,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.profile })
            client.invalidateQueries({ queryKey: queryKeys.expenditure })
            client.invalidateQueries({ queryKey: ['resolved-goals'] })
            toast.push('Profile saved')
        },
        onError: (error) => toast.push('Could not save profile', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        save.mutate({
            displayName: String(data.get('displayName')),
            locale: String(data.get('locale')),
            timezone: String(data.get('timezone')),
            unitSystem: 'METRIC',
            birthDate: String(data.get('birthDate')) || undefined,
            heightCm: data.get('heightCm') ? parseDecimal(data.get('heightCm')) : undefined,
            formulaSex: data.get('formulaSex')
                ? (String(data.get('formulaSex')) as 'MALE' | 'FEMALE')
                : undefined,
            activityMultiplier: parseDecimal(data.get('activityMultiplier')),
        })
    }
    return (
        <form id="account" onSubmit={submit}>
            <Card>
                <SectionHeader eyebrow="ACCOUNT" title="About you" aside={<UserRound />} />
                <div className="form-grid">
                    <Field label="Display name" className="span-2">
                        <input name="displayName" required defaultValue={profile.displayName} />
                    </Field>
                    <Field label="Birth date">
                        <input name="birthDate" type="date" defaultValue={profile.birthDate} />
                    </Field>
                    <Field label="Height (cm)">
                        <input
                            name="heightCm"
                            type="text"
                            inputMode="decimal"
                            defaultValue={profile.heightCm}
                        />
                    </Field>
                    <Field label="Formula used for energy">
                        <select name="formulaSex" defaultValue={profile.formulaSex || ''}>
                            <option value="">Not set</option>
                            <option value="MALE">Male formula</option>
                            <option value="FEMALE">Female formula</option>
                        </select>
                    </Field>
                    <Field label="Usual activity">
                        <select
                            name="activityMultiplier"
                            defaultValue={profile.activityMultiplier || 1.2}
                        >
                            <option value="1.2">Sedentary</option>
                            <option value="1.375">Lightly active</option>
                            <option value="1.55">Moderately active</option>
                            <option value="1.725">Very active</option>
                            <option value="1.9">Extremely active</option>
                        </select>
                    </Field>
                    <input type="hidden" name="locale" value={profile.locale} />
                    <input type="hidden" name="timezone" value={profile.timezone} />
                    <Button className="span-2" type="submit" disabled={save.isPending}>
                        {save.isPending ? 'Saving…' : 'Save profile'}
                    </Button>
                </div>
            </Card>
        </form>
    )
}

function Preferences({ profile }: { profile: Awaited<ReturnType<typeof api.profile>> }) {
    const auth = useAppAuth()
    const toast = useToast()
    return (
        <Card id="preferences">
            <SectionHeader eyebrow="PREFERENCES" title="App settings" aside={<Settings />} />
            <div className="preference-list">
                <div>
                    <span>Units</span>
                    <b>Metric</b>
                </div>
                <div>
                    <span>Language</span>
                    <b>{profile.locale}</b>
                </div>
                <div>
                    <span>Timezone</span>
                    <b>{profile.timezone}</b>
                </div>
            </div>
            <Button
                variant="ghost"
                onClick={() =>
                    void auth
                        .logout()
                        .catch((error: Error) =>
                            toast.push('Could not log out', error.message, 'error'),
                        )
                }
            >
                <LogOut />
                Log out
            </Button>
        </Card>
    )
}
