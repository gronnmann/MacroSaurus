/** Lets browsers that reject locale commas in number controls treat comma as a decimal point. */
export function installDecimalCommaSupport() {
    const decimalInput = (target: EventTarget | null): target is HTMLInputElement =>
        target instanceof HTMLInputElement &&
        (target.type === 'number' || target.inputMode === 'decimal')
    const insert = (input: HTMLInputElement, value: string) => {
        const start = input.selectionStart ?? input.value.length
        const end = input.selectionEnd ?? start
        const next = `${input.value.slice(0, start)}${value}${input.value.slice(end)}`
        const nativeSetter = Object.getOwnPropertyDescriptor(
            HTMLInputElement.prototype,
            'value',
        )?.set
        if (nativeSetter) nativeSetter.call(input, next)
        else input.value = next
        input.dispatchEvent(
            new InputEvent('input', { bubbles: true, inputType: 'insertText', data: value }),
        )
    }
    document.addEventListener(
        'keydown',
        (event) => {
            if (event.key !== ',') return
            const input = event.target
            if (!decimalInput(input)) return
            event.preventDefault()
            insert(input, '.')
        },
        true,
    )
    document.addEventListener(
        'beforeinput',
        (event) => {
            if (!(event instanceof InputEvent) || event.data !== ',' || !decimalInput(event.target))
                return
            event.preventDefault()
            insert(event.target, '.')
        },
        true,
    )
    document.addEventListener(
        'paste',
        (event) => {
            if (!decimalInput(event.target)) return
            const pasted = event.clipboardData?.getData('text')?.trim()
            if (!pasted?.includes(',') || pasted.includes('.')) return
            const normalized = pasted.replace(',', '.')
            if (!/^[+-]?\d*(?:\.\d*)?$/.test(normalized)) return
            event.preventDefault()
            insert(event.target, normalized)
        },
        true,
    )
}
