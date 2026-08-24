import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'

const supabase = vi.hoisted(() => ({
    auth: {
        getSession: vi.fn(),
        onAuthStateChange: vi.fn(),
        signInWithOtp: vi.fn(),
        verifyOtp: vi.fn(),
        signOut: vi.fn(),
    },
    stateChanged: undefined as
        | ((event: string, session: Record<string, unknown> | null) => void)
        | undefined,
}))

vi.mock('@supabase/supabase-js', () => ({ createClient: () => ({ auth: supabase.auth }) }))
vi.stubEnv('VITE_AUTH_MODE', 'supabase')
vi.stubEnv('VITE_SUPABASE_URL', 'https://example.supabase.co')
vi.stubEnv('VITE_SUPABASE_PUBLISHABLE_KEY', 'sb_publishable_test')

const { AppAuthProvider, useAppAuth } = await import('./auth')

function Consumer() {
    const auth = useAppAuth()
    const [token, setToken] = useState('')
    return (
        <>
            <span>{auth.isLoading ? 'loading' : auth.user?.email || 'signed out'}</span>
            <span>{token}</span>
            <button
                type="button"
                onClick={() => void auth.getToken().then((value) => setToken(value || ''))}
            >
                Read token
            </button>
            <button type="button" onClick={() => void auth.sendOtp('user@example.com')}>
                Send code
            </button>
            <button type="button" onClick={() => void auth.verifyOtp('user@example.com', '123456')}>
                Verify code
            </button>
        </>
    )
}

const session = (email: string, accessToken: string) => ({
    access_token: accessToken,
    user: { email, user_metadata: {} },
})

describe('Supabase auth provider', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        supabase.auth.getSession.mockResolvedValue({
            data: { session: session('user@example.com', 'initial-token') },
            error: null,
        })
        supabase.auth.onAuthStateChange.mockImplementation(
            (callback: (event: string, value: Record<string, unknown> | null) => void) => {
                supabase.stateChanged = callback
                return { data: { subscription: { unsubscribe: vi.fn() } } }
            },
        )
        supabase.auth.signInWithOtp.mockResolvedValue({ error: null })
        supabase.auth.verifyOtp.mockResolvedValue({ error: null })
    })

    it('restores the session and serves refreshed access tokens', async () => {
        const user = userEvent.setup()
        render(
            <AppAuthProvider>
                <Consumer />
            </AppAuthProvider>,
        )

        expect(await screen.findByText('user@example.com')).toBeVisible()
        await user.click(screen.getByRole('button', { name: 'Read token' }))
        expect(await screen.findByText('initial-token')).toBeVisible()

        act(() =>
            supabase.stateChanged?.(
                'TOKEN_REFRESHED',
                session('user@example.com', 'refreshed-token'),
            ),
        )
        await user.click(screen.getByRole('button', { name: 'Read token' }))
        expect(await screen.findByText('refreshed-token')).toBeVisible()
    })

    it('uses open-signup email OTP operations only', async () => {
        const user = userEvent.setup()
        render(
            <AppAuthProvider>
                <Consumer />
            </AppAuthProvider>,
        )
        await screen.findByText('user@example.com')

        await user.click(screen.getByRole('button', { name: 'Send code' }))
        expect(supabase.auth.signInWithOtp).toHaveBeenCalledWith({
            email: 'user@example.com',
            options: { shouldCreateUser: true },
        })
        await user.click(screen.getByRole('button', { name: 'Verify code' }))
        expect(supabase.auth.verifyOtp).toHaveBeenCalledWith({
            email: 'user@example.com',
            token: '123456',
            type: 'email',
        })
    })
})
