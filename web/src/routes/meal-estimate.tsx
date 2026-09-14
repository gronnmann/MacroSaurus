import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { PhotoInput } from '../components/photo-input'
import { Button, Field, Skeleton, useToast } from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { prepareLabelImage } from '../lib/image'
import { localDate, parseDecimal } from '../lib/utils'

export function MealEstimateEntry({ onDone }: { onDone: () => void }) {
    const client = useQueryClient()
    const toast = useToast()
    const features = useQuery({ queryKey: queryKeys.features, queryFn: api.features })
    const [text, setText] = useState('')
    const [images, setImages] = useState<string[]>([])
    const [preparing, setPreparing] = useState(false)
    const [photoError, setPhotoError] = useState('')
    const estimate = useMutation({ mutationFn: api.estimateMeal })
    const add = useMutation({
        mutationFn: api.quickTrack,
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['diary'] })
            toast.push('Added to Food Log')
            onDone()
        },
        onError: (error) => toast.push('Could not track meal', error.message, 'error'),
    })
    const prepare = async (files: File[]) => {
        setPhotoError('')
        if (images.length + files.length > 3) {
            setPhotoError('Choose up to 3 photos per meal.')
            return
        }
        setPreparing(true)
        try {
            const photos = await Promise.all(files.map(prepareLabelImage))
            setImages((current) => [...new Set([...current, ...photos])])
        } catch (error) {
            setPhotoError(error instanceof Error ? error.message : 'Could not open photo.')
        } finally {
            setPreparing(false)
        }
    }
    const save = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        const data = new FormData(event.currentTarget)
        const value = (key: string) => parseDecimal(data.get(key))
        const trackedAt = new Date()
        add.mutate({
            name: String(data.get('name')).trim(),
            calories: value('calories'),
            proteinG: value('proteinG'),
            carbohydrateG: value('carbohydrateG'),
            fatG: value('fatG'),
            fiberG: data.get('fiberG') ? value('fiberG') : null,
            localDate: localDate(trackedAt),
            consumedAt: trackedAt.toISOString(),
            saveAsFood: data.get('save') === 'on',
        })
    }
    if (features.isPending) return <Skeleton lines={3} />
    if (features.isError)
        return (
            <p role="alert">
                Could not check AI access.{' '}
                <Button variant="secondary" onClick={() => features.refetch()}>
                    Try again
                </Button>
            </p>
        )
    if (!features.data?.aiLabelScan.granted)
        return (
            <p className="muted">
                AI meal estimates require AI access. Ask your administrator to enable it.
            </p>
        )
    if (!features.data.aiLabelScan.available)
        return <p className="muted">AI is temporarily unavailable. Please try again later.</p>
    const result = estimate.data
    return (
        <div className="meal-estimate">
            {result ? (
                <form className="form-grid quick-track-sheet" onSubmit={save}>
                    <p className="span-2">
                        Estimated totals for the whole meal. Review and adjust before logging.
                    </p>
                    {result.assumptions.length > 0 && (
                        <ul className="span-2">
                            {[...new Set(result.assumptions)].map((item) => (
                                <li key={item}>{item}</li>
                            ))}
                        </ul>
                    )}
                    <Field label="Meal name" className="span-2">
                        <input name="name" required maxLength={200} defaultValue={result.name} />
                    </Field>
                    {(
                        [
                            ['calories', 'Calories'],
                            ['proteinG', 'Protein (g)'],
                            ['carbohydrateG', 'Carbs (g)'],
                            ['fatG', 'Fat (g)'],
                            ['fiberG', 'Fiber (g)'],
                        ] as const
                    ).map(([key, label]) => (
                        <Field key={key} label={label}>
                            <input
                                name={key}
                                type="number"
                                inputMode="decimal"
                                min="0"
                                step="any"
                                required={key !== 'fiberG'}
                                defaultValue={result[key] ?? ''}
                            />
                        </Field>
                    ))}
                    <label className="check span-2">
                        <input name="save" type="checkbox" />
                        Save for next time
                    </label>
                    <Button type="submit" className="span-2" disabled={add.isPending}>
                        {add.isPending ? 'Adding…' : 'Add to Food Log'}
                    </Button>
                    <Button
                        variant="secondary"
                        className="span-2"
                        disabled={add.isPending}
                        onClick={() => estimate.reset()}
                    >
                        Edit photos or description
                    </Button>
                </form>
            ) : (
                <form
                    className="quick-track-sheet"
                    onSubmit={(event) => {
                        event.preventDefault()
                        estimate.mutate({
                            text: text.trim(),
                            images,
                            localeHint: navigator.language,
                        })
                    }}
                >
                    <p>Add photos, describe your meal, or combine both for a better estimate.</p>
                    <PhotoInput
                        multiple
                        subject="meal photo"
                        disabled={preparing || estimate.isPending || images.length >= 3}
                        onFiles={prepare}
                    />
                    {preparing && <p role="status">Preparing photos…</p>}
                    {photoError && (
                        <p role="alert" className="field-error">
                            {photoError}
                        </p>
                    )}
                    <div className="meal-photo-previews">
                        {images.map((image, index) => (
                            <div key={image}>
                                <img src={image} alt={`Meal view ${index + 1}`} />
                                <Button
                                    variant="secondary"
                                    disabled={estimate.isPending}
                                    onClick={() =>
                                        setImages((current) =>
                                            current.filter((_, i) => i !== index),
                                        )
                                    }
                                >
                                    Remove photo {index + 1}
                                </Button>
                            </div>
                        ))}
                    </div>
                    <Field label="Meal description">
                        <textarea
                            rows={4}
                            maxLength={4000}
                            value={text}
                            disabled={estimate.isPending}
                            onChange={(event) => setText(event.target.value)}
                            placeholder="For example: chicken with rice, about 200 g of chicken, cooked with 1 tbsp olive oil. I ate the whole plate."
                        />
                    </Field>
                    <p className="muted">
                        Up to 3 photos. Photos and text are sent to AI for estimation. Photos are
                        not stored by Macrosaurus.
                    </p>
                    {estimate.error && (
                        <p role="alert" className="field-error">
                            {estimate.error.message}
                        </p>
                    )}
                    <Button
                        type="submit"
                        disabled={
                            preparing || estimate.isPending || (!text.trim() && !images.length)
                        }
                    >
                        {estimate.isPending ? 'Estimating meal…' : 'Estimate calories'}
                    </Button>
                </form>
            )}
        </div>
    )
}
