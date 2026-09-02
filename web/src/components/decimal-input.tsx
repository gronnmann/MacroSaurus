import { type InputHTMLAttributes, useEffect, useRef, useState } from 'react'
import { parseDecimal } from '../lib/utils'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> & {
    value: number | '' | undefined
    onValue: (value: number | undefined) => void
}

/** Keeps incomplete locale input such as `1,` editable while exposing parsed numbers. */
export function DecimalInput({ value, onValue, onBlur, ...props }: Props) {
    const [text, setText] = useState(value == null ? '' : String(value))
    const focused = useRef(false)
    useEffect(() => {
        if (!focused.current) setText(value == null ? '' : String(value))
    }, [value])
    return (
        <input
            {...props}
            type="text"
            inputMode="decimal"
            value={text}
            onFocus={() => {
                focused.current = true
            }}
            onChange={(event) => {
                const raw = event.target.value
                setText(raw)
                if (raw.trim() === '') onValue(undefined)
                else {
                    const parsed = parseDecimal(raw)
                    if (Number.isFinite(parsed)) onValue(parsed)
                }
            }}
            onBlur={(event) => {
                focused.current = false
                const parsed = parseDecimal(text)
                setText(
                    Number.isFinite(parsed) ? String(parsed) : value == null ? '' : String(value),
                )
                onBlur?.(event)
            }}
        />
    )
}
