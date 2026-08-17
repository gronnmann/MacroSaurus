import { Auth0Provider, useAuth0 } from '@auth0/auth0-react'
import { createContext, type PropsWithChildren, useCallback, useContext } from 'react'

type AuthMode = 'auth0' | 'dev'
type AuthState = {
    mode: AuthMode
    isLoading: boolean
    isAuthenticated: boolean
    user?: { name?: string; email?: string; picture?: string }
    getToken: () => Promise<string | undefined>
    login: () => Promise<void>
    logout: () => Promise<void>
}

const mode: AuthMode = import.meta.env.VITE_AUTH_MODE === 'auth0' ? 'auth0' : 'dev'
const AuthContext = createContext<AuthState | null>(null)

function DevAuthProvider({ children }: PropsWithChildren) {
    const value: AuthState = {
        mode: 'dev',
        isLoading: false,
        isAuthenticated: true,
        user: { name: import.meta.env.VITE_DEV_USER_ID || 'dev-user' },
        getToken: async () => undefined,
        login: async () => undefined,
        logout: async () => undefined,
    }
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function Auth0Bridge({ children }: PropsWithChildren) {
    const auth = useAuth0()
    const login = useCallback(async () => {
        await auth.loginWithRedirect({
            appState: { returnTo: `${location.pathname}${location.search}` },
        })
    }, [auth])
    const logout = useCallback(async () => {
        await auth.logout({ logoutParams: { returnTo: location.origin } })
    }, [auth])
    const value: AuthState = {
        mode: 'auth0',
        isLoading: auth.isLoading,
        isAuthenticated: auth.isAuthenticated,
        user: auth.user,
        getToken: auth.getAccessTokenSilently,
        login,
        logout,
    }
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function AppAuthProvider({ children }: PropsWithChildren) {
    if (mode === 'dev') return <DevAuthProvider>{children}</DevAuthProvider>
    const domain = import.meta.env.VITE_AUTH0_DOMAIN
    const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID
    const audience = import.meta.env.VITE_AUTH0_AUDIENCE
    if (!domain || !clientId || !audience) {
        return (
            <div className="fatal-config">
                <h1>Authentication is not configured</h1>
                <p>
                    Set the Auth0 Vite variables or use <code>VITE_AUTH_MODE=dev</code>.
                </p>
            </div>
        )
    }
    return (
        <Auth0Provider
            domain={domain}
            clientId={clientId}
            authorizationParams={{ redirect_uri: location.origin, audience }}
            onRedirectCallback={(state) => {
                history.replaceState({}, '', state?.returnTo || '/today')
                dispatchEvent(new PopStateEvent('popstate'))
            }}
        >
            <Auth0Bridge>{children}</Auth0Bridge>
        </Auth0Provider>
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
