import { useRegisterSW } from 'virtual:pwa-register/react'
import { useQuery } from '@tanstack/react-query'
import {
    ChartNoAxesCombined,
    LayoutDashboard,
    NotebookTabs,
    Plus,
    UserRound,
    WifiOff,
} from 'lucide-react'
import { type FormEvent, useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router-dom'
import coach from '../assets/mascot/coach.webp'
import dinoMark from '../assets/mascot/dino-mark-v2.webp'
import { api, queryKeys, setTokenProvider } from '../lib/api'
import { useAppAuth } from '../lib/auth'
import { Button, Field, ToastProvider } from './ui'

const nav = [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/food-log', label: 'Food Log', icon: NotebookTabs },
    { to: '/track', label: 'Track', icon: Plus, track: true },
    { to: '/progress', label: 'Progress', icon: ChartNoAxesCombined },
    { to: '/profile', label: 'Profile', icon: UserRound },
]

export function Brand({ compact = false }: { compact?: boolean }) {
    return (
        <Link
            className={`brand ${compact ? 'brand--compact' : ''}`}
            to="/dashboard"
            aria-label="Macrosaurus dashboard"
        >
            <span className="brand-badge">
                <img src={dinoMark} alt="" />
            </span>
            {!compact && (
                <div>
                    <b>
                        <i>MACRO</i>SAURUS
                    </b>
                    <small>Your personal macro coach</small>
                </div>
            )}
        </Link>
    )
}

export function ProtectedLayout() {
    const auth = useAppAuth()
    const location = useLocation()
    const [tokenReady, setTokenReady] = useState(false)
    useEffect(() => {
        setTokenProvider(auth.getToken)
        setTokenReady(true)
    }, [auth.getToken])
    if (auth.isLoading || !tokenReady)
        return (
            <div className="auth-loading">
                <img src={coach} alt="Macrosaurus coach" />
                <p>Warming up…</p>
            </div>
        )
    if (!auth.isAuthenticated)
        return (
            <Navigate
                to="/login"
                replace
                state={{ returnTo: `${location.pathname}${location.search}${location.hash}` }}
            />
        )
    return <AuthenticatedLayout />
}

function AuthenticatedLayout() {
    const location = useLocation()
    const status = useQuery({ queryKey: queryKeys.coachingStatus, queryFn: api.coachingStatus })
    if (status.isLoading)
        return (
            <div className="auth-loading">
                <img src={coach} alt="" />
                <p>Getting your program ready…</p>
            </div>
        )
    if (status.error)
        return (
            <ToastProvider>
                <div className="route-error">
                    <p>Could not load coaching status. Refresh to try again.</p>
                </div>
            </ToastProvider>
        )
    if (!status.data?.setupComplete && location.pathname !== '/setup')
        return <Navigate to="/setup" replace />
    if (location.pathname === '/setup')
        return (
            <ToastProvider>
                <Outlet />
            </ToastProvider>
        )
    return (
        <ToastProvider>
            <div className="app-shell">
                <aside className="sidebar">
                    <Brand />
                    <nav aria-label="Main navigation">
                        {nav.map((item) => (
                            <NavLink
                                className={item.track ? 'nav-track' : ''}
                                to={item.to}
                                state={item.track ? { from: location.pathname } : undefined}
                                key={item.to}
                            >
                                <item.icon />
                                {item.label}
                            </NavLink>
                        ))}
                    </nav>
                    <div className="coach-note">
                        <img src={coach} alt="" />
                        <div>
                            <b>Small choices. Massive results.</b>
                            <span>Track consistently, not perfectly.</span>
                        </div>
                    </div>
                </aside>
                <div className="app-main">
                    <header className="mobile-header">
                        <Brand />
                        <span className="mobile-date">
                            {new Intl.DateTimeFormat(undefined, {
                                weekday: 'short',
                                day: 'numeric',
                                month: 'short',
                            }).format(new Date())}
                        </span>
                    </header>
                    <OfflineBanner />
                    <main className="page">
                        <Outlet />
                    </main>
                    <nav className="bottom-nav" aria-label="Mobile navigation">
                        {nav.map((item) =>
                            item.track ? (
                                <Link
                                    className="bottom-track"
                                    to={item.to}
                                    state={{ from: location.pathname }}
                                    key={item.to}
                                >
                                    <span>
                                        <item.icon />
                                    </span>
                                    <small>{item.label}</small>
                                </Link>
                            ) : (
                                <NavLink to={item.to} key={item.to}>
                                    <item.icon />
                                    <span>{item.label}</span>
                                </NavLink>
                            ),
                        )}
                    </nav>
                </div>
            </div>
        </ToastProvider>
    )
}

function OfflineBanner() {
    const [online, setOnline] = useState(navigator.onLine)
    useEffect(() => {
        const update = () => setOnline(navigator.onLine)
        addEventListener('online', update)
        addEventListener('offline', update)
        return () => {
            removeEventListener('online', update)
            removeEventListener('offline', update)
        }
    }, [])
    const {
        offlineReady: [offlineReady, setOfflineReady],
    } = useRegisterSW()
    if (!online)
        return (
            <div className="offline-banner">
                <WifiOff />
                You are offline. Reconnect to save changes.
            </div>
        )
    if (offlineReady)
        return (
            <div className="offline-banner offline-banner--ready">
                <span>Macrosaurus is ready offline.</span>
                <button type="button" onClick={() => setOfflineReady(false)}>
                    Dismiss
                </button>
            </div>
        )
    return null
}

export function LoginScreen() {
    const auth = useAppAuth()
    const [email, setEmail] = useState('')
    const [code, setCode] = useState('')
    const [codeSent, setCodeSent] = useState(false)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string>()
    const [resendAt, setResendAt] = useState(0)
    const [now, setNow] = useState(Date.now())
    const resendSeconds = Math.max(0, Math.ceil((resendAt - now) / 1000))

    useEffect(() => {
        if (!codeSent || resendSeconds === 0) return
        const timer = window.setInterval(() => setNow(Date.now()), 1_000)
        return () => window.clearInterval(timer)
    }, [codeSent, resendSeconds])

    const sendCode = async (event?: FormEvent<HTMLFormElement>) => {
        event?.preventDefault()
        setBusy(true)
        setError(undefined)
        try {
            await auth.sendOtp(email.trim().toLowerCase())
            setCodeSent(true)
            setCode('')
            const sentAt = Date.now()
            setNow(sentAt)
            setResendAt(sentAt + 60_000)
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : 'Could not send a sign-in code.')
        } finally {
            setBusy(false)
        }
    }
    const verifyCode = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        setBusy(true)
        setError(undefined)
        try {
            await auth.verifyOtp(email.trim().toLowerCase(), code.trim())
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : 'That code could not be verified.')
        } finally {
            setBusy(false)
        }
    }
    return (
        <main className="login-page">
            <div className="login-copy">
                <Brand />
                <p className="eyebrow">EAT BIG. LIFT BIG. TRACK EVERYTHING.</p>
                <h1>Nutrition that keeps up with you.</h1>
                <p>
                    Track your nutrition, build recipes, scan products, and understand your energy
                    needs.
                </p>
                {codeSent ? (
                    <form className="login-form" onSubmit={verifyCode}>
                        <Field label={`Code sent to ${email}`} error={error}>
                            <input
                                aria-label="One-time code"
                                autoComplete="one-time-code"
                                inputMode="numeric"
                                maxLength={6}
                                pattern="[0-9]{6}"
                                placeholder="123456"
                                required
                                value={code}
                                onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
                            />
                        </Field>
                        <Button type="submit" disabled={busy || code.length !== 6}>
                            {busy ? 'Checking…' : 'Verify code'}
                        </Button>
                        <div className="login-form-actions">
                            <Button
                                type="button"
                                variant="ghost"
                                disabled={busy || resendSeconds > 0}
                                onClick={() => void sendCode()}
                            >
                                {resendSeconds > 0 ? `Resend in ${resendSeconds}s` : 'Resend code'}
                            </Button>
                            <Button
                                type="button"
                                variant="ghost"
                                disabled={busy}
                                onClick={() => {
                                    setCodeSent(false)
                                    setCode('')
                                    setError(undefined)
                                }}
                            >
                                Use another email
                            </Button>
                        </div>
                    </form>
                ) : (
                    <form className="login-form" onSubmit={sendCode}>
                        <Field label="Email address" error={error || auth.initializationError}>
                            <input
                                type="email"
                                autoComplete="email"
                                placeholder="you@example.com"
                                required
                                value={email}
                                onChange={(event) => setEmail(event.target.value)}
                            />
                        </Field>
                        <Button type="submit" disabled={busy}>
                            {busy ? 'Sending…' : 'Email me a code'}
                        </Button>
                    </form>
                )}
                <small>Adult wellness tracking. Not medical advice.</small>
            </div>
            <div className="login-art">
                <span>YOUR PERSONAL MACRO COACH</span>
                <img src={coach} alt="Macrosaurus, your personal macro coach" />
            </div>
        </main>
    )
}

export function LoginRoute() {
    const auth = useAppAuth()
    const location = useLocation()
    if (auth.isLoading)
        return (
            <div className="auth-loading">
                <img src={coach} alt="" />
                <p>Warming up…</p>
            </div>
        )
    const requested = (location.state as { returnTo?: unknown } | null)?.returnTo
    const returnTo =
        typeof requested === 'string' && requested.startsWith('/') && !requested.startsWith('//')
            ? requested
            : '/dashboard'
    return auth.isAuthenticated ? <Navigate to={returnTo} replace /> : <LoginScreen />
}

export function PublicLayout() {
    return (
        <ToastProvider>
            <header className="public-header">
                <Brand />
            </header>
            <main className="public-main">
                <Outlet />
            </main>
        </ToastProvider>
    )
}
