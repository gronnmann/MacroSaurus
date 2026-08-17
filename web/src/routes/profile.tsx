import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Dumbbell, Gauge, LogOut, Settings, Target, UserRound } from 'lucide-react'
import { type FormEvent, useState } from 'react'
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
import { formatNumber } from '../lib/utils'
import type { EnergyGoalMode, GoalSettings, GoalWeightBasis, MacroGoalMode } from '../types'
import { NutrientGoals } from './goals'

export function ProfilePage() {
    const profile = useQuery({
        queryKey: queryKeys.profile,
        queryFn: api.profile,
    })
    const goals = useQuery({ queryKey: queryKeys.goals, queryFn: api.goals })
    const profileData = profile.data
    const goalSettings = goals.data
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
            {profile.isLoading || goals.isLoading ? (
                <Skeleton lines={10} />
            ) : profile.error || goals.error || !profileData || !goalSettings ? (
                <ErrorPanel error={profile.error || goals.error} />
            ) : (
                <div className="profile-sections">
                    <AccountForm profile={profileData} />
                    <GoalForm key={String(goalSettings.configured)} settings={goalSettings} />
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
            heightCm: data.get('heightCm') ? Number(data.get('heightCm')) : undefined,
            formulaSex: data.get('formulaSex')
                ? (String(data.get('formulaSex')) as 'MALE' | 'FEMALE')
                : undefined,
            activityMultiplier: Number(data.get('activityMultiplier')),
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
                            type="number"
                            min="30"
                            max="300"
                            step="0.1"
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

function GoalForm({ settings }: { settings: GoalSettings }) {
    const client = useQueryClient()
    const toast = useToast()
    const estimate = useQuery({
        queryKey: queryKeys.expenditure,
        queryFn: () => api.expenditure(),
    })
    const weights = useQuery({
        queryKey: queryKeys.weights,
        queryFn: () => api.weights(),
    })
    const [energyMode, setEnergyMode] = useState<EnergyGoalMode>(
        settings.energyMode || 'MAINTENANCE',
    )
    const [energyValue, setEnergyValue] = useState(settings.energyValue ?? 0)
    const [macroMode, setMacroMode] = useState<MacroGoalMode>(settings.macroMode || 'GUIDED')
    const [weightBasis, setWeightBasis] = useState<GoalWeightBasis>(
        settings.weightBasis || 'LATEST_WEIGHT',
    )
    const [manualWeight, setManualWeight] = useState(
        settings.manualWeightKg || weights.data?.[0]?.weightKg || 75,
    )
    const [proteinPerKg, setProteinPerKg] = useState(settings.proteinGPerKg || 1.6)
    const [fatPercent, setFatPercent] = useState(settings.fatEnergyPercent || 25)
    const [proteinPercent, setProteinPercent] = useState(settings.proteinEnergyPercent || 30)
    const [carbPercent, setCarbPercent] = useState(settings.carbohydrateEnergyPercent || 45)
    const maintenance = estimate.data?.suggestedKcal
    const targetEnergy =
        energyMode === 'FIXED'
            ? energyValue
            : maintenance == null
              ? undefined
              : energyMode === 'MAINTENANCE'
                ? maintenance
                : energyMode === 'KCAL_DELTA'
                  ? maintenance + energyValue
                  : maintenance * (1 + energyValue / 100)
    const weight = weightBasis === 'MANUAL_WEIGHT' ? manualWeight : weights.data?.[0]?.weightKg
    const preview =
        macroMode === 'GUIDED' && targetEnergy && weight
            ? guided(targetEnergy, weight, proteinPerKg, fatPercent)
            : undefined
    const save = useMutation({
        mutationFn: api.updateGoals,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: queryKeys.goals })
            client.invalidateQueries({ queryKey: ['resolved-goals'] })
            toast.push('Goals saved')
        },
        onError: (error) => toast.push('Could not save goals', error.message, 'error'),
    })
    const submit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        save.mutate({
            energyMode,
            energyValue: energyMode === 'MAINTENANCE' ? undefined : energyValue,
            macroMode,
            proteinGPerKg: macroMode === 'GUIDED' ? proteinPerKg : undefined,
            fatEnergyPercent: macroMode === 'GUIDED' ? fatPercent : undefined,
            weightBasis: macroMode === 'GUIDED' ? weightBasis : undefined,
            manualWeightKg:
                macroMode === 'GUIDED' && weightBasis === 'MANUAL_WEIGHT'
                    ? manualWeight
                    : undefined,
            proteinTargetG:
                macroMode === 'CUSTOM_GRAMS' ? Number(data.get('proteinTargetG')) : undefined,
            carbohydrateTargetG:
                macroMode === 'CUSTOM_GRAMS' ? Number(data.get('carbohydrateTargetG')) : undefined,
            fatTargetG: macroMode === 'CUSTOM_GRAMS' ? Number(data.get('fatTargetG')) : undefined,
            proteinEnergyPercent: macroMode === 'PERCENT_SPLIT' ? proteinPercent : undefined,
            carbohydrateEnergyPercent: macroMode === 'PERCENT_SPLIT' ? carbPercent : undefined,
        })
    }
    return (
        <form id="nutrition-goals" onSubmit={submit}>
            <Card tone="green">
                <SectionHeader
                    eyebrow="NUTRITION GOALS"
                    title="Build your daily targets"
                    aside={<Dumbbell />}
                />
                <div className="goal-builder">
                    <div>
                        <Field label="Calorie goal">
                            <select
                                value={energyMode}
                                onChange={(event) =>
                                    setEnergyMode(event.target.value as EnergyGoalMode)
                                }
                            >
                                <option value="MAINTENANCE">Maintain around expenditure</option>
                                <option value="KCAL_DELTA">Kcal surplus or deficit</option>
                                <option value="PERCENT_DELTA">Percentage surplus or deficit</option>
                                <option value="FIXED">Fixed calorie target</option>
                            </select>
                        </Field>
                        {energyMode !== 'MAINTENANCE' && (
                            <Field
                                label={
                                    energyMode === 'FIXED'
                                        ? 'Daily calories'
                                        : energyMode === 'KCAL_DELTA'
                                          ? 'Kcal change'
                                          : 'Percentage change'
                                }
                                hint={
                                    energyMode !== 'FIXED'
                                        ? 'Use a negative number for a deficit.'
                                        : undefined
                                }
                            >
                                <input
                                    type="number"
                                    step="any"
                                    value={energyValue}
                                    onChange={(event) => setEnergyValue(Number(event.target.value))}
                                />
                            </Field>
                        )}
                        <div className="goal-result">
                            <Gauge />
                            <div>
                                <span>Daily target</span>
                                <strong>
                                    {targetEnergy
                                        ? `${formatNumber(targetEnergy)} kcal`
                                        : 'Needs energy estimate'}
                                </strong>
                                {maintenance && energyMode !== 'FIXED' && (
                                    <small>
                                        Based on {formatNumber(maintenance)} kcal estimated
                                        expenditure
                                    </small>
                                )}
                            </div>
                        </div>
                    </div>
                    <div>
                        <Field label="Macro setup">
                            <select
                                value={macroMode}
                                onChange={(event) =>
                                    setMacroMode(event.target.value as MacroGoalMode)
                                }
                            >
                                <option value="GUIDED">Guided sliders</option>
                                <option value="CUSTOM_GRAMS">Custom grams</option>
                                <option value="PERCENT_SPLIT">Calorie percentage split</option>
                            </select>
                        </Field>
                        {macroMode === 'GUIDED' && (
                            <div className="goal-sliders">
                                <Field
                                    label={`Protein · ${proteinPerKg.toFixed(1)} g/kg`}
                                    hint="0.83 g/kg is the general adult reference; exercising people often choose more."
                                >
                                    <input
                                        type="range"
                                        min="0.8"
                                        max="2.2"
                                        step="0.1"
                                        value={proteinPerKg}
                                        onChange={(event) =>
                                            setProteinPerKg(Number(event.target.value))
                                        }
                                    />
                                </Field>
                                <Field label="Weight used">
                                    <select
                                        value={weightBasis}
                                        onChange={(event) =>
                                            setWeightBasis(event.target.value as GoalWeightBasis)
                                        }
                                    >
                                        <option value="LATEST_WEIGHT">Latest weigh-in</option>
                                        <option value="MANUAL_WEIGHT">
                                            Manual reference weight
                                        </option>
                                    </select>
                                </Field>
                                {weightBasis === 'MANUAL_WEIGHT' && (
                                    <Field label="Reference weight (kg)">
                                        <input
                                            type="number"
                                            min="10"
                                            step="0.1"
                                            value={manualWeight}
                                            onChange={(event) =>
                                                setManualWeight(Number(event.target.value))
                                            }
                                        />
                                    </Field>
                                )}
                                <Field label={`Fat · ${fatPercent}% of calories`}>
                                    <input
                                        type="range"
                                        min="10"
                                        max="45"
                                        step="1"
                                        value={fatPercent}
                                        onChange={(event) =>
                                            setFatPercent(Number(event.target.value))
                                        }
                                    />
                                </Field>
                            </div>
                        )}
                        {macroMode === 'CUSTOM_GRAMS' && (
                            <div className="form-grid">
                                <Field label="Protein (g)">
                                    <input
                                        name="proteinTargetG"
                                        type="number"
                                        min="0"
                                        step="any"
                                        defaultValue={settings.proteinTargetG || 160}
                                    />
                                </Field>
                                <Field label="Carbs (g)">
                                    <input
                                        name="carbohydrateTargetG"
                                        type="number"
                                        min="0"
                                        step="any"
                                        defaultValue={settings.carbohydrateTargetG || 220}
                                    />
                                </Field>
                                <Field label="Fat (g)">
                                    <input
                                        name="fatTargetG"
                                        type="number"
                                        min="0"
                                        step="any"
                                        defaultValue={settings.fatTargetG || 70}
                                    />
                                </Field>
                            </div>
                        )}
                        {macroMode === 'PERCENT_SPLIT' && (
                            <div className="goal-sliders">
                                <Field label={`Protein · ${proteinPercent}%`}>
                                    <input
                                        type="range"
                                        min="10"
                                        max="70"
                                        value={proteinPercent}
                                        onChange={(event) =>
                                            setProteinPercent(Number(event.target.value))
                                        }
                                    />
                                </Field>
                                <Field label={`Carbs · ${carbPercent}%`}>
                                    <input
                                        type="range"
                                        min="10"
                                        max={90 - proteinPercent}
                                        value={carbPercent}
                                        onChange={(event) =>
                                            setCarbPercent(Number(event.target.value))
                                        }
                                    />
                                </Field>
                                <p>
                                    Fat receives the remaining{' '}
                                    {Math.max(0, 100 - proteinPercent - carbPercent)}%.
                                </p>
                            </div>
                        )}
                    </div>
                </div>
                {preview && (
                    <div className="goal-preview">
                        <div>
                            <b>{formatNumber(preview.protein)} g</b>
                            <span>protein</span>
                        </div>
                        <div>
                            <b>{formatNumber(preview.carbs)} g</b>
                            <span>carbs</span>
                        </div>
                        <div>
                            <b>{formatNumber(preview.fat)} g</b>
                            <span>fat</span>
                        </div>
                    </div>
                )}
                <p className="privacy-note">
                    Suggestions are adjustable wellness references, not medical advice.
                </p>
                <Button type="submit" disabled={save.isPending}>
                    {save.isPending ? 'Saving…' : 'Save nutrition goals'}
                </Button>
            </Card>
        </form>
    )
}

function Preferences({ profile }: { profile: Awaited<ReturnType<typeof api.profile>> }) {
    const auth = useAppAuth()
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
            <Button variant="ghost" onClick={() => auth.logout()}>
                <LogOut />
                Log out
            </Button>
        </Card>
    )
}

function guided(calories: number, weight: number, proteinPerKg: number, fatPercent: number) {
    const protein = weight * proteinPerKg
    const fat = (calories * fatPercent) / 100 / 9
    return {
        protein,
        fat,
        carbs: Math.max(0, (calories - protein * 4 - fat * 9) / 4),
    }
}
