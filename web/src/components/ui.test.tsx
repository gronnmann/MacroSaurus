import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button, ConfirmDialog, StatePanel } from './ui'

describe('design system primitives', () => {
    it('exposes disabled buttons accessibly', () => {
        render(<Button disabled>Save</Button>)
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
    })

    it('opens and confirms a destructive dialog', async () => {
        const user = userEvent.setup()
        const confirm = vi.fn()
        render(
            <ConfirmDialog
                title="Remove entry?"
                description="This cannot be undone."
                confirmLabel="Remove"
                danger
                onConfirm={confirm}
                trigger={<Button>Open</Button>}
            />,
        )
        await user.click(screen.getByRole('button', { name: 'Open' }))
        expect(screen.getByRole('dialog')).toHaveTextContent('Remove entry?')
        await user.click(screen.getByRole('button', { name: 'Remove' }))
        expect(confirm).toHaveBeenCalledOnce()
    })

    it('renders readable empty-state copy', () => {
        render(<StatePanel title="No foods found" message="Try a different search." />)
        expect(screen.getByRole('heading', { name: 'No foods found' })).toBeVisible()
    })
})
