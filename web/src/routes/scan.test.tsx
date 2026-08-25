import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/ui'
import type { Food } from '../types'
import { ScanExperience } from './scan'

const api = vi.hoisted(() => ({
    barcode: vi.fn(),
    importBarcode: vi.fn(),
    startScan: vi.fn(),
}))

vi.mock('../lib/api', () => ({ api, queryKeys: {} }))

const food: Food = {
    id: 'food-id',
    revisionId: 'revision-id',
    revision: 1,
    name: 'User protein milk',
    brand: 'Mine',
    barcode: '3017620422003',
    source: 'USER',
    basisType: 'PER_100_G',
    basisAmount: 100,
    basisUnit: 'g',
    nutrients: { energy_kcal: 43, protein_g: 5.9 },
    portions: [],
    createdAt: '2026-08-25T08:00:00Z',
}

describe('barcode scan', () => {
    beforeEach(() => {
        vi.clearAllMocks()
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
        api.importBarcode.mockResolvedValue(food)
    })

    it('opens the preferred barcode match without showing a chooser', async () => {
        const user = userEvent.setup()
        const ready = vi.fn()
        render(
            <MemoryRouter>
                <QueryClientProvider client={new QueryClient()}>
                    <ToastProvider>
                        <ScanExperience onFoodReady={ready} />
                    </ToastProvider>
                </QueryClientProvider>
            </MemoryRouter>,
        )

        await user.type(screen.getByLabelText('Enter barcode'), '3017620422003')
        await user.click(screen.getByRole('button', { name: 'Look up' }))

        await waitFor(() =>
            expect(api.importBarcode).toHaveBeenCalledWith('3017620422003', expect.anything()),
        )
        await waitFor(() => expect(ready).toHaveBeenCalledWith(food))
        expect(screen.queryByText('Choose a product')).not.toBeInTheDocument()
    })
})
