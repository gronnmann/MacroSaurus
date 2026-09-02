import { lazy, type ReactNode, Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { LoginRoute, ProtectedLayout, PublicLayout } from './components/layout'
import { ErrorPanel, Skeleton } from './components/ui'

const DashboardPage = lazy(() =>
    import('./routes/dashboard').then((module) => ({
        default: module.DashboardPage,
    })),
)
const FoodLogPage = lazy(() =>
    import('./routes/food-log').then((module) => ({
        default: module.FoodLogPage,
    })),
)
const TrackPage = lazy(() =>
    import('./routes/track').then((module) => ({ default: module.TrackPage })),
)
const FoodDetailPage = lazy(() =>
    import('./routes/foods').then((module) => ({
        default: module.FoodDetailPage,
    })),
)
const FoodEditorPage = lazy(() =>
    import('./routes/foods').then((module) => ({
        default: module.FoodEditorPage,
    })),
)
const RecipeDetailPage = lazy(() =>
    import('./routes/recipes').then((module) => ({
        default: module.RecipeDetailPage,
    })),
)
const RecipeEditorPage = lazy(() =>
    import('./routes/recipes').then((module) => ({
        default: module.RecipeEditorPage,
    })),
)
const ScanReviewPage = lazy(() =>
    import('./routes/scan').then((module) => ({
        default: module.ScanReviewPage,
    })),
)
const ProgressPage = lazy(() =>
    import('./routes/progress').then((module) => ({
        default: module.ProgressPage,
    })),
)
const ProfilePage = lazy(() =>
    import('./routes/profile').then((module) => ({
        default: module.ProfilePage,
    })),
)
const AdminPage = lazy(() =>
    import('./routes/admin').then((module) => ({ default: module.AdminPage })),
)
const SetupPage = lazy(() =>
    import('./routes/setup').then((module) => ({ default: module.SetupPage })),
)
const CheckInPage = lazy(() =>
    import('./routes/check-in').then((module) => ({ default: module.CheckInPage })),
)
const SharedPage = lazy(() =>
    import('./routes/shared').then((module) => ({ default: module.SharedPage })),
)
const NotFoundPage = lazy(() =>
    import('./routes/shared').then((module) => ({
        default: module.NotFoundPage,
    })),
)

const screen = (element: ReactNode) => (
    <Suspense fallback={<Skeleton lines={7} />}>{element}</Suspense>
)
const RouteError = () => (
    <div className="route-error">
        <ErrorPanel
            title="This screen could not load"
            error={
                new Error(
                    'Try returning to Dashboard. If the problem continues, restart the frontend and backend.',
                )
            }
        />
    </div>
)

export const router = createBrowserRouter([
    { path: '/login', element: <LoginRoute /> },
    {
        element: <PublicLayout />,
        children: [
            {
                path: '/shared/:token',
                element: screen(<SharedPage />),
                errorElement: <RouteError />,
            },
        ],
    },
    {
        element: <ProtectedLayout />,
        errorElement: <RouteError />,
        children: [
            { index: true, element: <Navigate to="/dashboard" replace /> },
            { path: '/dashboard', element: screen(<DashboardPage />) },
            { path: '/food-log', element: screen(<FoodLogPage />) },
            { path: '/track', element: screen(<TrackPage />) },
            { path: '/today', element: <Navigate to="/dashboard" replace /> },
            { path: '/foods', element: <Navigate to="/track" replace /> },
            { path: '/foods/new', element: screen(<FoodEditorPage />) },
            { path: '/foods/:id', element: screen(<FoodDetailPage />) },
            { path: '/foods/:id/edit', element: screen(<FoodEditorPage />) },
            { path: '/recipes', element: <Navigate to="/track" replace /> },
            { path: '/recipes/new', element: screen(<RecipeEditorPage />) },
            { path: '/recipes/:id', element: screen(<RecipeDetailPage />) },
            { path: '/recipes/:id/edit', element: screen(<RecipeEditorPage />) },
            { path: '/scan', element: <Navigate to="/track" replace /> },
            { path: '/scan/:id', element: screen(<ScanReviewPage />) },
            { path: '/progress', element: screen(<ProgressPage />) },
            { path: '/setup', element: screen(<SetupPage />) },
            { path: '/check-in', element: screen(<CheckInPage />) },
            {
                path: '/goals',
                element: <Navigate to="/profile#nutrition-goals" replace />,
            },
            { path: '/profile', element: screen(<ProfilePage />) },
            { path: '/admin', element: screen(<AdminPage />) },
            {
                path: '/settings/profile',
                element: <Navigate to="/profile" replace />,
            },
            { path: '*', element: screen(<NotFoundPage />) },
        ],
    },
])
