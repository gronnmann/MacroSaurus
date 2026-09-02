import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { DecimalInput } from './decimal-input'

function Harness() {
    const [value, setValue] = useState<number>()
    return (
        <>
            <DecimalInput aria-label="Amount" value={value} onValue={setValue} />
            <output>{value}</output>
        </>
    )
}

describe('DecimalInput', () => {
    it('keeps comma input editable and exposes the parsed decimal', async () => {
        const user = userEvent.setup()
        render(<Harness />)
        const input = screen.getByLabelText('Amount')

        await user.type(input, '1,5')

        expect(input).toHaveValue('1,5')
        expect(screen.getByText('1.5')).toBeInTheDocument()
    })
})
