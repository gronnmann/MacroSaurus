import { useRegisterSW } from 'virtual:pwa-register/react'
import {
    ChartNoAxesCombined,
    LayoutDashboard,
    NotebookTabs,
    Plus,
    UserRound,
    WifiOff,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Outlet, useLocation } from 'react-router-dom'
import coach from '../assets/mascot/coach.webp'
import dinoMark from '../assets/mascot/dino-mark-v2.webp'
import { setTokenProvider } from '../lib/api'
import { useAppAuth } from '../lib/auth'
import { Button, ToastProvider } from './ui'

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
    useEffect(() => setTokenProvider(auth.getToken), [auth.getToken])
    if (auth.isLoading)
        return (
            <div className="auth-loading">
                <img src={coach} alt="Macrosaurus coach" />
                <p>Warming up…</p>
            </div>
        )
    if (!auth.isAuthenticated) return <LoginScreen />
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
        needRefresh: [needRefresh, setNeedRefresh],
        updateServiceWorker,
    } = useRegisterSW()
    if (!online)
        return (
            <div className="offline-banner">
                <WifiOff />
                You are offline. Reconnect to save changes.
            </div>
        )
    if (needRefresh)
        return (
            <div className="offline-banner offline-banner--update">
                <span>An update is ready.</span>
                <button type="button" onClick={() => updateServiceWorker(true)}>
                    Update now
                </button>
                <button type="button" onClick={() => setNeedRefresh(false)}>
                    Later
                </button>
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
                <Button onClick={() => auth.login()}>Start tracking</Button>
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
    if (auth.isLoading)
        return (
            <div className="auth-loading">
                <img src={coach} alt="" />
                <p>Warming up…</p>
            </div>
        )
    return auth.isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginScreen />
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
