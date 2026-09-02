import type {
    AdminUser,
    BarcodeCandidate,
    CheckIn,
    CoachingSetupDraft,
    CoachingStatus,
    CreatedShare,
    DiaryDay,
    DiaryEntry,
    EnergyEstimate,
    Food,
    FoodInput,
    GoalSettings,
    LastTrackedAmount,
    MyFeatures,
    NutrientDefinition,
    NutrientTarget,
    NutritionDayReview,
    ProblemDetails,
    Profile,
    ProgressSeriesPoint,
    Recipe,
    RecipeInput,
    ResolvedFoodAmount,
    ResolvedGoal,
    ScanJob,
    SetupPreview,
    SharedSnapshot,
    TimeOfDaySuggestions,
    Trackable,
    UpdateDiaryEntryInput,
    Weight,
} from '../types'
import { authConfig } from './auth'

let tokenProvider: (() => Promise<string | undefined>) | undefined
export function setTokenProvider(provider: () => Promise<string | undefined>) {
    tokenProvider = provider
}

export class ApiError extends Error {
    constructor(public problem: ProblemDetails) {
        super(problem.detail)
        this.name = 'ApiError'
    }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    if (!navigator.onLine && init.method && init.method !== 'GET')
        throw new ApiError({
            status: 0,
            detail: 'You are offline. Reconnect before saving changes.',
        })
    const headers = new Headers(init.headers)
    if (init.body) headers.set('Content-Type', 'application/json')
    if (authConfig.mode === 'dev') headers.set('X-User-Id', authConfig.devUserId)
    else {
        const token = await tokenProvider?.()
        if (token) headers.set('Authorization', `Bearer ${token}`)
    }
    const response = await fetch(`/api/v1${path}`, { ...init, headers })
    if (!response.ok) {
        const raw = (await response.json().catch(() => ({}))) as Partial<ProblemDetails>
        throw new ApiError({
            status: response.status,
            detail: raw.detail || response.statusText || 'Request failed',
            ...raw,
        })
    }
    if (response.status === 204) return undefined as T
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
}

const body = (value: unknown) => JSON.stringify(value)
export const api = {
    nutrients: () => request<NutrientDefinition[]>('/nutrients'),
    profile: () => request<Profile>('/me/profile'),
    features: () => request<MyFeatures>('/me/features'),
    adminUsers: (query = '') =>
        request<AdminUser[]>(`/admin/users?query=${encodeURIComponent(query)}`),
    setAiLabelScan: (userId: string, enabled: boolean) =>
        request<AdminUser>(`/admin/users/${encodeURIComponent(userId)}/features/ai-label-scan`, {
            method: 'PUT',
            body: body({ enabled }),
        }),
    updateProfile: (input: Omit<Profile, 'userId'>) =>
        request<Profile>('/me/profile', { method: 'PUT', body: body(input) }),
    coachingStatus: () => request<CoachingStatus>('/me/coaching/status'),
    setupDraft: () => request<CoachingSetupDraft>('/me/coaching/setup-draft'),
    saveSetupDraft: (input: CoachingSetupDraft) =>
        request<CoachingSetupDraft>('/me/coaching/setup-draft', {
            method: 'PUT',
            body: body(input),
        }),
    previewSetup: (input: CoachingSetupDraft) =>
        request<SetupPreview>('/me/coaching/setup-draft/preview', {
            method: 'POST',
            body: body(input),
        }),
    completeSetup: (input: CoachingSetupDraft) =>
        request<CoachingStatus>('/me/coaching/setup-draft/complete', {
            method: 'POST',
            body: body(input),
        }),
    currentCheckIn: () => request<CheckIn>('/me/coaching/check-ins/current'),
    refreshCheckIn: (id: string) =>
        request<CheckIn>(`/me/coaching/check-ins/${id}/refresh`, { method: 'POST' }),
    acceptCheckIn: (id: string) =>
        request<CheckIn>(`/me/coaching/check-ins/${id}/accept`, { method: 'POST' }),
    skipCheckIn: (id: string) =>
        request<CheckIn>(`/me/coaching/check-ins/${id}/skip`, { method: 'POST' }),
    reviewNutritionDay: (date: string, input: Omit<NutritionDayReview, 'date'>) =>
        request<NutritionDayReview>(`/diary-days/${date}/analysis`, {
            method: 'PUT',
            body: body(input),
        }),
    targets: () => request<NutrientTarget[]>('/me/targets'),
    setTarget: (
        code: string,
        input: Pick<NutrientTarget, 'targetAmount' | 'minimumAmount' | 'maximumAmount'>,
    ) =>
        request<NutrientTarget>(`/me/targets/${encodeURIComponent(code)}`, {
            method: 'PUT',
            body: body(input),
        }),
    clearTarget: (code: string) =>
        request<void>(`/me/targets/${encodeURIComponent(code)}`, {
            method: 'DELETE',
        }),
    goals: () => request<GoalSettings>('/me/goals'),
    updateGoals: (input: Omit<GoalSettings, 'configured'>) =>
        request<GoalSettings>('/me/goals', { method: 'PUT', body: body(input) }),
    resolvedGoals: (from: string, to = from) =>
        request<ResolvedGoal[]>(`/me/goals/resolved?from=${from}&to=${to}`),
    diary: (date: string) => request<DiaryDay>(`/diary-days/${date}`),
    diaryRange: (from: string, to: string) =>
        request<DiaryDay[]>(`/diary-days?from=${from}&to=${to}`),
    quickTrack: (input: unknown) =>
        request('/quick-entries', { method: 'POST', body: body(input) }),
    addFoodEntry: (input: unknown) =>
        request('/diary-entries/food', { method: 'POST', body: body(input) }),
    addRecipeEntry: (input: unknown) =>
        request('/diary-entries/recipe', { method: 'POST', body: body(input) }),
    updateDiaryEntry: (id: string, input: UpdateDiaryEntryInput) =>
        request<DiaryEntry>(`/diary-entries/${id}`, {
            method: 'PUT',
            body: body(input),
        }),
    copyDiaryEntry: (
        id: string,
        input: { destinationDate: string; destinationTime?: string | null },
    ) =>
        request<DiaryEntry>(`/diary-entries/${id}/copies`, {
            method: 'POST',
            body: body(input),
        }),
    deleteDiaryEntry: (id: string) => request<void>(`/diary-entries/${id}`, { method: 'DELETE' }),
    trackables: (query = '', type = 'ALL', limit = 30) =>
        request<Trackable[]>(
            `/trackables?query=${encodeURIComponent(query)}&type=${type}&limit=${limit}`,
        ),
    lastTrackedAmount: (type: Trackable['type'], revisionId: string) =>
        request<LastTrackedAmount | undefined>(
            `/trackables/${type}/revisions/${revisionId}/last-amount`,
        ),
    timeOfDaySuggestions: (type = 'ALL', limit = 5) =>
        request<TimeOfDaySuggestions>(
            `/trackables/suggestions/time-of-day?type=${type}&limit=${limit}`,
        ),
    foods: (query = '', limit = 25) =>
        request<Food[]>(`/foods?query=${encodeURIComponent(query)}&limit=${limit}`),
    food: (id: string) => request<Food>(`/foods/${id}`),
    foodRevision: (id: string) => request<Food>(`/food-revisions/${id}`),
    createFood: (input: FoodInput) =>
        request<Food>('/foods', { method: 'POST', body: body(input) }),
    updateFood: (id: string, input: FoodInput) =>
        request<Food>(`/foods/${id}`, { method: 'PUT', body: body(input) }),
    resolveFood: (
        revisionId: string,
        input: { quantity: number; unit: string; portionId?: string | null },
    ) =>
        request<ResolvedFoodAmount>(`/food-revisions/${revisionId}/resolve`, {
            method: 'POST',
            body: body(input),
        }),
    recipes: () => request<Recipe[]>('/recipes'),
    recipe: (id: string) => request<Recipe>(`/recipes/${id}`),
    recipeRevision: (id: string) => request<Recipe>(`/recipes/revisions/${id}`),
    createRecipe: (input: RecipeInput) =>
        request<Recipe>('/recipes', { method: 'POST', body: body(input) }),
    updateRecipe: (id: string, input: RecipeInput) =>
        request<Recipe>(`/recipes/${id}`, { method: 'PUT', body: body(input) }),
    weights: (limit = 100) => request<Weight[]>(`/weight-measurements?limit=${limit}`),
    addWeight: (input: { weightKg: number; measuredAt?: string; note?: string | null }) =>
        request<Weight>('/weight-measurements', {
            method: 'POST',
            body: body(input),
        }),
    deleteWeight: (id: string) => request<void>(`/weight-measurements/${id}`, { method: 'DELETE' }),
    expenditure: (persist = false) =>
        request<EnergyEstimate>(`/expenditure-estimates/current?persist=${persist}`),
    progressSeries: (from: string, to: string) =>
        request<ProgressSeriesPoint[]>(`/expenditure-estimates/series?from=${from}&to=${to}`),
    barcode: (code: string) => request<BarcodeCandidate[]>(`/barcodes/${encodeURIComponent(code)}`),
    importBarcode: (code: string) =>
        request<Food>(`/barcodes/${encodeURIComponent(code)}/import`, {
            method: 'POST',
        }),
    startScan: (input: { image: string; barcode?: string | null; localeHint?: string }) =>
        request<ScanJob>('/food-scans', { method: 'POST', body: body(input) }),
    scan: (id: string) => request<ScanJob>(`/food-scans/${id}`),
    confirmScan: (id: string, input: FoodInput) =>
        request<Food>(`/food-scans/${id}/confirm`, {
            method: 'POST',
            body: body(input),
        }),
    createShare: (input: {
        resourceType: 'FOOD' | 'RECIPE'
        resourceRevisionId: string
        expiresAt?: string | null
    }) =>
        request<CreatedShare>('/share-links', {
            method: 'POST',
            body: body(input),
        }),
    revokeShare: (id: string) => request<void>(`/share-links/${id}`, { method: 'DELETE' }),
    shared: (token: string) => request<SharedSnapshot>(`/shared/${encodeURIComponent(token)}`),
}

export const queryKeys = {
    nutrients: ['nutrients'] as const,
    profile: ['profile'] as const,
    features: ['features'] as const,
    adminUsers: (query: string) => ['admin-users', query] as const,
    coachingStatus: ['coaching-status'] as const,
    setupDraft: ['coaching-setup-draft'] as const,
    checkIn: ['coaching-check-in'] as const,
    targets: ['targets'] as const,
    goals: ['goals'] as const,
    resolvedGoals: (from: string, to = from) => ['resolved-goals', from, to] as const,
    diary: (date: string) => ['diary', date] as const,
    foods: (query: string) => ['foods', query] as const,
    trackables: (query: string, type = 'ALL') => ['trackables', query, type] as const,
    lastTrackedAmount: (type: Trackable['type'], revisionId: string) =>
        ['last-tracked-amount', type, revisionId] as const,
    timeOfDaySuggestions: (type: string) => ['time-of-day-suggestions', type] as const,
    food: (id: string) => ['food', id] as const,
    recipes: ['recipes'] as const,
    recipe: (id: string) => ['recipe', id] as const,
    weights: ['weights'] as const,
    expenditure: ['expenditure'] as const,
    progressSeries: (from: string, to: string) => ['progress-series', from, to] as const,
    scan: (id: string) => ['scan', id] as const,
}
