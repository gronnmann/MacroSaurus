import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/ui'
import type { Food, Trackable } from '../types'
import { TrackPage } from './track'

const api = vi.hoisted(() => ({
    trackables: vi.fn(),
    food: vi.fn(),
    resolveFood: vi.fn(),
    addFoodEntry: vi.fn(),
    addRecipeEntry: vi.fn(),
    quickTrack: vi.fn(),
    addWeight: vi.fn(),
}))

vi.mock('../lib/api', () => ({
    api,
    queryKeys: {
        trackables: (query: string, type: string) => ['trackables', query, type],
        food: (id: string) => ['food', id],
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
        api.food.mockResolvedValue(food)
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

        await user.click(screen.getByRole('button', { name: /Search foods & recipes/ }))
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
})
