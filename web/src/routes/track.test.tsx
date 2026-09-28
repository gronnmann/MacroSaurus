import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/ui'
import type { Food, Trackable } from '../types'
import { TrackPage, TrackSheet } from './track'

const api = vi.hoisted(() => ({
    trackables: vi.fn(),
    timeOfDaySuggestions: vi.fn(),
    lastTrackedAmount: vi.fn(),
    food: vi.fn(),
    foodRevision: vi.fn(),
    recipeRevision: vi.fn(),
    createFood: vi.fn(),
    nutrients: vi.fn(),
    resolveFood: vi.fn(),
    addFoodEntry: vi.fn(),
    addRecipeEntry: vi.fn(),
    quickTrack: vi.fn(),
    addWeight: vi.fn(),
    barcode: vi.fn(),
    importBarcode: vi.fn(),
    startScan: vi.fn(),
}))

vi.mock('../lib/api', () => ({
    api,
    queryKeys: {
        trackables: (query: string, type: string) => ['trackables', query, type],
        timeOfDaySuggestions: (type: string) => ['time-of-day-suggestions', type],
        lastTrackedAmount: (type: string, id: string) => ['last-tracked-amount', type, id],
        food: (id: string) => ['food', id],
        nutrients: ['nutrients'],
        weights: ['weights'],
        expenditure: ['expenditure'],
    },
}))

const trackable: Trackable = {
    type: 'FOOD',
    id: 'food-id',
    revisionId: 'revision-id',
    name: 'Protein milk',
    brand: 'Tine',
    servingLabel: '100 g',
    nutrients: { energy_kcal: 43, protein_g: 5.9, fat_g: 0.1, carbohydrate_g: 4.5 },
}
const food: Food = {
    ...trackable,
    revision: 1,
    barcode: '3017620422003',
    source: 'USER',
    basisType: 'PER_100_G',
    basisAmount: 100,
    basisUnit: 'g',
    portions: [],
    createdAt: '2026-08-25T08:00:00Z',
}

describe('mobile tracking amount', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.trackables.mockResolvedValue([trackable])
        api.timeOfDaySuggestions.mockResolvedValue({ anchorHour: 11, items: [] })
        api.lastTrackedAmount.mockResolvedValue(undefined)
        api.food.mockResolvedValue(food)
        api.foodRevision.mockResolvedValue(food)
        api.createFood.mockResolvedValue(food)
        api.nutrients.mockResolvedValue([
            {
                code: 'vitamin_c_mg',
                displayName: 'Vitamin C',
                unit: 'mg',
                category: 'VITAMIN',
                sortOrder: 200,
            },
        ])
        api.barcode.mockResolvedValue([])
        api.importBarcode.mockResolvedValue(food)
        api.resolveFood.mockImplementation((_revisionId: string, input: { quantity: number }) => {
            const factor = input.quantity / 100
            return Promise.resolve({
                foodRevisionId: food.revisionId,
                displayName: food.name,
                quantity: input.quantity,
                unit: 'g',
                resolvedGrams: input.quantity,
                nutrients: Object.fromEntries(
                    Object.entries(food.nutrients).map(([code, value]) => [code, value * factor]),
                ),
            })
        })
    })

    function setupSheet(ingredients?: (items: unknown[]) => void) {
        render(
            <MemoryRouter initialEntries={['/track?date=2026-08-20']}>
                <QueryClientProvider
                    client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
                >
                    <ToastProvider>
                        {ingredients ? (
                            <TrackSheet onClose={vi.fn()} onIngredients={ingredients} />
                        ) : (
                            <TrackPage />
                        )}
                    </ToastProvider>
                </QueryClientProvider>
            </MemoryRouter>,
        )
        return userEvent.setup()
    }

    it('logs food at the selected local date and time', async () => {
        const user = setupSheet()
        await user.click(await screen.findByRole('button', { name: /Protein milk/ }))
        expect(screen.getByLabelText('Date')).toHaveValue('2026-08-20')
        fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-08-19' } })
        fireEvent.change(screen.getByLabelText('Time'), { target: { value: '18:35' } })
        await waitFor(() =>
            expect(screen.getByRole('button', { name: 'Add to Food Log' })).toBeEnabled(),
        )
        await user.click(screen.getByRole('button', { name: 'Add to Food Log' }))
        await waitFor(() =>
            expect(api.addFoodEntry).toHaveBeenCalledWith(
                expect.objectContaining({
                    localDate: '2026-08-19',
                    consumedAt: new Date('2026-08-19T18:35:00').toISOString(),
                }),
            ),
        )
    })

    it('retains the chosen date and time when switching to Quick Add', async () => {
        const user = setupSheet()
        await user.click(await screen.findByRole('button', { name: /Protein milk/ }))
        fireEvent.change(screen.getByLabelText('Time'), { target: { value: '07:15' } })
        await user.click(screen.getByRole('button', { name: /Search results/ }))
        await user.click(screen.getByRole('tab', { name: 'Quick Add' }))
        expect(screen.getByLabelText('Time')).toHaveValue('07:15')
        await user.type(screen.getByLabelText('Name'), 'Breakfast')
        await user.click(screen.getByRole('button', { name: 'Add to Food Log' }))
        await waitFor(() =>
            expect(api.quickTrack).toHaveBeenCalledWith(
                expect.objectContaining({
                    localDate: '2026-08-20',
                    consumedAt: new Date('2026-08-20T07:15:00').toISOString(),
                }),
            ),
        )
    })

    it('adds an ingredient using its default portion without logging food', async () => {
        const portionFood: Food = {
            ...food,
            portions: [
                { id: 'glass', name: 'Glass (250 g)', quantity: 1, gramWeight: 250, default: true },
            ],
        }
        api.foodRevision.mockResolvedValue(portionFood)
        const addIngredients = vi.fn()
        const user = setupSheet(addIngredients)
        expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
            'Search',
            'Scan',
            'Quick Add',
            'More',
        ])
        await user.click(await screen.findByRole('button', { name: /Protein milk/ }))
        await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveValue('1'))
        expect(screen.queryByLabelText('Date')).not.toBeInTheDocument()
        await user.clear(screen.getByLabelText('Amount'))
        await user.type(screen.getByLabelText('Amount'), '2')
        await waitFor(() =>
            expect(screen.getByRole('button', { name: 'Add ingredient' })).toBeEnabled(),
        )
        await user.click(screen.getByRole('button', { name: 'Add ingredient' }))
        await waitFor(() =>
            expect(addIngredients).toHaveBeenCalledWith([
                { food: portionFood, quantity: 2, unit: 'portion', portionId: 'glass' },
            ]),
        )
        expect(api.addFoodEntry).not.toHaveBeenCalled()
        expect(api.lastTrackedAmount).not.toHaveBeenCalled()
    })

    it('keeps expanded nutrients when collapsed and includes them in Quick Add', async () => {
        const user = setupSheet()
        await user.click(screen.getByRole('tab', { name: 'Quick Add' }))
        await user.type(screen.getByLabelText('Name'), 'Lunch')
        expect(screen.getByLabelText('Saturated fat (g)')).not.toBeVisible()
        await user.click(screen.getByText('More nutrients'))
        await user.type(screen.getByLabelText('Saturated fat (g)'), '2,5')
        await user.type(screen.getByLabelText('Fiber (g)'), '3')
        await user.type(await screen.findByLabelText('Vitamin C (mg)'), '35')
        await user.type(screen.getByLabelText('Salt (g)'), '1,2')
        await user.click(screen.getByText('More nutrients'))
        await user.click(screen.getByLabelText('Save for next time'))
        await user.click(screen.getByRole('button', { name: 'Add to Food Log' }))
        await waitFor(() =>
            expect(api.quickTrack).toHaveBeenCalledWith(
                expect.objectContaining({
                    fiberG: 3,
                    additionalNutrients: { saturated_fat_g: 2.5, vitamin_c_mg: 35, sodium_mg: 480 },
                    saveAsFood: true,
                }),
            ),
        )
    })

    it('creates a quick ingredient and chooses its amount without a diary entry', async () => {
        const addIngredients = vi.fn()
        const user = setupSheet(addIngredients)
        await user.click(screen.getByRole('tab', { name: 'Quick Add' }))
        await user.type(screen.getByLabelText('Name'), 'Homemade sauce')
        await user.type(screen.getByLabelText('Calories'), '120')
        await user.click(screen.getByText('More nutrients'))
        await user.type(screen.getByLabelText('Saturated fat (g)'), '2')
        await user.click(screen.getByRole('button', { name: 'Choose amount' }))
        await screen.findByLabelText('Amount')
        expect(api.createFood).toHaveBeenCalledWith(
            expect.objectContaining({
                name: 'Homemade sauce',
                basisType: 'PER_SERVING',
                nutrients: expect.objectContaining({ energy_kcal: 120, saturated_fat_g: 2 }),
            }),
        )
        expect(api.quickTrack).not.toHaveBeenCalled()
        expect(addIngredients).not.toHaveBeenCalled()
    })

    it('scales the original food amounts when using a recipe as an ingredient', async () => {
        api.trackables.mockResolvedValue([{ ...trackable, type: 'RECIPE', name: 'Milk shake' }])
        api.recipeRevision.mockResolvedValue({
            servings: 4,
            explicitYieldG: 800,
            nutrientsPerServing: { energy_kcal: 100 },
            nutrientsPer100G: { energy_kcal: 50 },
            ingredients: [{ foodRevisionId: food.revisionId, quantity: 800, unit: 'g' }],
        })
        const addIngredients = vi.fn()
        const user = setupSheet(addIngredients)
        await user.click(await screen.findByRole('button', { name: /Milk shake/ }))
        await waitFor(() => expect(screen.getByLabelText('Amount')).toHaveValue('1'))
        expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument()
        await user.clear(screen.getByLabelText('Amount'))
        await user.type(screen.getByLabelText('Amount'), '2')
        await user.click(screen.getByRole('button', { name: 'Add ingredient' }))
        await waitFor(() =>
            expect(addIngredients).toHaveBeenCalledWith([
                { food, quantity: 400, unit: 'g', portionId: undefined },
            ]),
        )
        expect(api.addRecipeEntry).not.toHaveBeenCalled()
    })

    it('edits and tracks a weighted recipe in grams', async () => {
        api.trackables.mockResolvedValue([
            {
                ...trackable,
                id: 'recipe-id',
                revisionId: 'recipe-revision-id',
                type: 'RECIPE',
                name: 'Milk shake',
                nutrients: { energy_kcal: 200, protein_g: 12 },
            },
        ])
        api.recipeRevision.mockResolvedValue({
            id: 'recipe-id',
            revisionId: 'recipe-revision-id',
            name: 'Milk shake',
            servings: 2,
            explicitYieldG: 400,
            nutrientsPerServing: { energy_kcal: 200, protein_g: 12 },
            nutrientsPer100G: { energy_kcal: 100, protein_g: 6 },
            ingredients: [],
        })
        const user = setupSheet()

        await user.click(await screen.findByRole('button', { name: /Milk shake/ }))

        const edit = await screen.findByRole('link', { name: 'Edit' })
        expect(edit).toHaveAttribute('href', '/recipes/recipe-id/edit')
        await user.click(screen.getByRole('button', { name: 'grams' }))
        await user.clear(screen.getByLabelText('Amount'))
        await user.type(screen.getByLabelText('Amount'), '150')
        await waitFor(() => expect(screen.getByText('150')).toBeVisible())
        expect(screen.getByText('9 g')).toBeVisible()

        await user.click(screen.getByRole('button', { name: 'Add to Food Log' }))

        await waitFor(() =>
            expect(api.addRecipeEntry).toHaveBeenCalledWith(
                expect.objectContaining({
                    recipeRevisionId: 'recipe-revision-id',
                    quantity: 150,
                    unit: 'g',
                }),
            ),
        )
    })

    it('shows nutrition for the entered amount before adding it', async () => {
        const user = userEvent.setup()
        render(
            <MemoryRouter initialEntries={['/track']}>
                <QueryClientProvider client={new QueryClient()}>
                    <ToastProvider>
                        <TrackPage />
                    </ToastProvider>
                </QueryClientProvider>
            </MemoryRouter>,
        )

        await user.click(await screen.findByRole('button', { name: /Protein milk/ }))

        expect(await screen.findByText('43')).toBeVisible()
        expect(screen.getByText('5.9 g')).toBeVisible()
        const amount = screen.getByLabelText('Amount')
        await user.clear(amount)
        await user.type(amount, '200')

        await waitFor(() => expect(screen.getByText('86')).toBeVisible())
        expect(screen.getByText('11.8 g')).toBeVisible()
        expect(api.addFoodEntry).not.toHaveBeenCalled()
        expect(screen.queryByText(/per 100/i)).not.toBeInTheDocument()
    })

    it('prefills the last amount without logging it', async () => {
        api.lastTrackedAmount.mockResolvedValue({ quantity: 30, unit: 'g' })
        const user = userEvent.setup()
        render(
            <MemoryRouter initialEntries={['/track']}>
                <QueryClientProvider client={new QueryClient()}>
                    <ToastProvider>
                        <TrackPage />
                    </ToastProvider>
                </QueryClientProvider>
            </MemoryRouter>,
        )

        await user.click(await screen.findByRole('button', { name: /Protein milk/ }))

        expect(await screen.findByDisplayValue('30')).toBeVisible()
        await waitFor(() => expect(screen.getByText('12.9')).toBeVisible())
        expect(api.resolveFood).toHaveBeenCalledWith(
            'revision-id',
            expect.objectContaining({ quantity: 30, unit: 'g' }),
        )
        expect(api.addFoodEntry).not.toHaveBeenCalled()
    })

    it('shows time-of-day go-tos once and opens their amount form', async () => {
        api.timeOfDaySuggestions.mockResolvedValue({ anchorHour: 11, items: [trackable] })
        const user = userEvent.setup()
        render(
            <MemoryRouter initialEntries={['/track']}>
                <QueryClientProvider client={new QueryClient()}>
                    <ToastProvider>
                        <TrackPage />
                    </ToastProvider>
                </QueryClientProvider>
            </MemoryRouter>,
        )

        expect(await screen.findByRole('heading', { name: /Around 11/ })).toBeVisible()
        expect(screen.getAllByRole('button', { name: /Protein milk/ })).toHaveLength(1)
        await user.click(screen.getByRole('button', { name: /Protein milk/ }))

        expect(await screen.findByLabelText('Amount')).toBeVisible()
        expect(api.addFoodEntry).not.toHaveBeenCalled()
    })

    it('previews macros after a barcode import', async () => {
        api.barcode.mockResolvedValue([
            {
                barcode: food.barcode,
                name: food.name,
                source: food.source,
                basisType: food.basisType,
                nutrients: food.nutrients,
                externalId: food.id,
            },
        ])
        const user = userEvent.setup()
        render(
            <MemoryRouter initialEntries={['/track']}>
                <QueryClientProvider client={new QueryClient()}>
                    <ToastProvider>
                        <TrackPage />
                    </ToastProvider>
                </QueryClientProvider>
            </MemoryRouter>,
        )

        await user.click(screen.getByRole('tab', { name: 'Scan' }))
        await user.type(screen.getByLabelText('Enter barcode'), '3017620422003')
        await user.click(screen.getByRole('button', { name: 'Look up' }))

        expect(await screen.findByText('43')).toBeVisible()
        expect(screen.getByText('5.9 g')).toBeVisible()
    })
})
