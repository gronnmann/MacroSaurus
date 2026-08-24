import { createClient, type Session } from '@supabase/supabase-js'
import {
    createContext,
    type PropsWithChildren,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useState,
} from 'react'

type AuthMode = 'supabase' | 'dev'
type AuthState = {
    mode: AuthMode
    isLoading: boolean
    isAuthenticated: boolean
    initializationError?: string
    user?: { name?: string; email?: string; picture?: string }
    getToken: () => Promise<string | undefined>
    sendOtp: (email: string) => Promise<void>
    verifyOtp: (email: string, code: string) => Promise<void>
    logout: () => Promise<void>
}

const mode: AuthMode = import.meta.env.VITE_AUTH_MODE === 'supabase' ? 'supabase' : 'dev'
const AuthContext = createContext<AuthState | null>(null)

function DevAuthProvider({ children }: PropsWithChildren) {
    const value: AuthState = {
        mode: 'dev',
        isLoading: false,
        isAuthenticated: true,
        user: { name: import.meta.env.VITE_DEV_USER_ID || 'dev-user' },
        getToken: async () => undefined,
        sendOtp: async () => undefined,
        verifyOtp: async () => undefined,
        logout: async () => undefined,
    }
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function SupabaseAuthProvider({
    children,
    url,
    publishableKey,
}: PropsWithChildren<{ url: string; publishableKey: string }>) {
    const client = useMemo(() => createClient(url, publishableKey), [url, publishableKey])
    const [session, setSession] = useState<Session | null>(null)
    const [isLoading, setIsLoading] = useState(true)
    const [initializationError, setInitializationError] = useState<string>()

    useEffect(() => {
        let active = true
        const {
            data: { subscription },
        } = client.auth.onAuthStateChange((_event, nextSession) => {
            if (!active) return
            setSession(nextSession)
            setIsLoading(false)
            setInitializationError(undefined)
        })
        void client.auth.getSession().then(({ data, error }) => {
            if (!active) return
            if (error) setInitializationError(error.message)
            setSession(data.session)
            setIsLoading(false)
        })
        return () => {
            active = false
            subscription.unsubscribe()
        }
    }, [client])

    const getToken = useCallback(async () => session?.access_token, [session])
    const sendOtp = useCallback(
        async (email: string) => {
            const { error } = await client.auth.signInWithOtp({
                email,
                options: { shouldCreateUser: true },
            })
            if (error) throw error
        },
        [client],
    )
    const verifyOtp = useCallback(
        async (email: string, code: string) => {
            const { error } = await client.auth.verifyOtp({ email, token: code, type: 'email' })
            if (error) throw error
        },
        [client],
    )
    const logout = useCallback(async () => {
        const { error } = await client.auth.signOut({ scope: 'local' })
        if (error) throw error
        location.replace('/login')
    }, [client])
    const metadata = session?.user.user_metadata
    const value: AuthState = {
        mode: 'supabase',
        isLoading,
        isAuthenticated: Boolean(session),
        initializationError,
        user: session
            ? {
                  name: metadata?.full_name || metadata?.name || session.user.email,
                  email: session.user.email,
                  picture: metadata?.avatar_url || metadata?.picture,
              }
            : undefined,
        getToken,
        sendOtp,
        verifyOtp,
        logout,
    }
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function AppAuthProvider({ children }: PropsWithChildren) {
    if (mode === 'dev') return <DevAuthProvider>{children}</DevAuthProvider>
    const url = import.meta.env.VITE_SUPABASE_URL
    const publishableKey = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY
    if (!url || !publishableKey) {
        return (
            <div className="fatal-config">
                <h1>Authentication is not configured</h1>
                <p>
                    Set the Supabase Vite variables or use <code>VITE_AUTH_MODE=dev</code>.
                </p>
            </div>
        )
    }
    return (
        <SupabaseAuthProvider url={url} publishableKey={publishableKey}>
            {children}
        </SupabaseAuthProvider>
    )
}

export function useAppAuth() {
    const auth = useContext(AuthContext)
    if (!auth) throw new Error('useAppAuth must be used inside AppAuthProvider')
    return auth
}

export const authConfig = {
    mode,
    devUserId: import.meta.env.VITE_DEV_USER_ID || 'dev-user',
}
