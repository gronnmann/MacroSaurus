import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { LoginScreen } from './layout'

const auth = vi.hoisted(() => ({
    mode: 'supabase' as const,
    isLoading: false,
    isAuthenticated: false,
    getToken: vi.fn(),
    sendOtp: vi.fn(),
    verifyOtp: vi.fn(),
    logout: vi.fn(),
}))

vi.mock('../lib/auth', () => ({
    authConfig: { mode: 'supabase' },
    useAppAuth: () => auth,
}))
describe('Supabase OTP login', () => {
    beforeEach(() => vi.clearAllMocks())

    it('requests and verifies an email code', async () => {
        const user = userEvent.setup()
        render(
            <MemoryRouter>
                <LoginScreen />
            </MemoryRouter>,
        )

        await user.type(screen.getByLabelText('Email address'), 'USER@Example.COM')
        await user.click(screen.getByRole('button', { name: 'Email me a code' }))
        expect(auth.sendOtp).toHaveBeenCalledWith('user@example.com')

        await user.type(screen.getByLabelText('One-time code'), '12ab3456')
        await user.click(screen.getByRole('button', { name: 'Verify code' }))
        expect(auth.verifyOtp).toHaveBeenCalledWith('user@example.com', '123456')
    })

    it('shows provider errors without advancing to code entry', async () => {
        auth.sendOtp.mockRejectedValueOnce(new Error('Email rate limit exceeded'))
        const user = userEvent.setup()
        render(
            <MemoryRouter>
                <LoginScreen />
            </MemoryRouter>,
        )

        await user.type(screen.getByLabelText('Email address'), 'user@example.com')
        await user.click(screen.getByRole('button', { name: 'Email me a code' }))
        expect(await screen.findByText('Email rate limit exceeded')).toBeVisible()
        expect(screen.queryByLabelText('One-time code')).not.toBeInTheDocument()
    })
})
