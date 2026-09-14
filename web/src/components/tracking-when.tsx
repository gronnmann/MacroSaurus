import { localDate } from '../lib/utils'
import { Field } from './ui'

export type TrackingWhen = { date: string; time: string }

export function initialTrackingWhen(date?: string): TrackingWhen {
    const now = new Date()
    return {
        date: date || localDate(now),
        time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
    }
}

export function trackingTimestamp(when: TrackingWhen) {
    return {
        localDate: when.date,
        consumedAt: new Date(`${when.date}T${when.time}:00`).toISOString(),
    }
}

export function TrackingWhenFields({
    value,
    onChange,
}: {
    value: TrackingWhen
    onChange: (value: TrackingWhen) => void
}) {
    return (
        <div className="form-grid span-2 tracking-when">
            <Field label="Date">
                <input
                    type="date"
                    required
                    value={value.date}
                    onChange={(event) => onChange({ ...value, date: event.target.value })}
                />
            </Field>
            <Field label="Time">
                <input
                    type="time"
                    required
                    value={value.time}
                    onChange={(event) => onChange({ ...value, time: event.target.value })}
                />
            </Field>
        </div>
    )
}
