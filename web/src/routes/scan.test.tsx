import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../components/ui'
import type { Food } from '../types'
import { ScanExperience } from './scan'

const api = vi.hoisted(() => ({
    features: vi.fn(),
    nutrients: vi.fn(),
    barcode: vi.fn(),
    importBarcode: vi.fn(),
    startScan: vi.fn(),
}))

vi.mock('../lib/api', () => ({
    api,
    queryKeys: { features: ['features'], nutrients: ['nutrients'] },
}))
vi.mock('../lib/image', () => ({ prepareLabelImage: async () => 'data:image/jpeg;base64,YQ==' }))

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

beforeEach(() => {
    vi.resetAllMocks()
    api.features.mockResolvedValue({ aiLabelScan: { granted: true, available: true } })
    api.nutrients.mockResolvedValue([])
    api.importBarcode.mockResolvedValue(food)
})

describe('barcode scan', () => {
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
        expect(api.barcode).not.toHaveBeenCalled()
        expect(api.importBarcode).toHaveBeenCalledTimes(1)
        expect(screen.queryByText('Choose a product')).not.toBeInTheDocument()
    })
})

it('reads a label without a barcode and shows progress and retryable errors', async () => {
    let rejectScan: (error: Error) => void = () => {}
    api.features.mockResolvedValue({ aiLabelScan: { granted: true, available: true } })
    api.startScan.mockImplementation(
        () =>
            new Promise((_resolve, reject) => {
                rejectScan = reject
            }),
    )
    const user = userEvent.setup()
    render(
        <MemoryRouter>
            <QueryClientProvider client={new QueryClient()}>
                <ToastProvider>
                    <ScanExperience />
                </ToastProvider>
            </QueryClientProvider>
        </MemoryRouter>,
    )
    await screen.findByRole('button', { name: 'Upload label photo' })
    const input = screen.getByLabelText('Upload label photo')
    const file = new File(['label'], 'label.jpg', { type: 'image/jpeg' })
    await user.upload(input, file)
    expect(await screen.findByRole('status')).toHaveTextContent('Reading the label')
    expect(screen.getByRole('button', { name: 'Take label photo' })).toBeDisabled()
    expect(api.startScan).toHaveBeenLastCalledWith(
        expect.objectContaining({ barcode: null, image: 'data:image/jpeg;base64,YQ==' }),
    )
    rejectScan(new Error('AI credits are unavailable.'))
    expect(await screen.findByRole('alert')).toHaveTextContent('AI credits are unavailable')
    await user.upload(input, file)
    await waitFor(() => expect(api.startScan).toHaveBeenCalledTimes(2))
})

function renderScanner() {
    return render(
        <MemoryRouter>
            <QueryClientProvider client={new QueryClient()}>
                <ToastProvider>
                    <ScanExperience />
                </ToastProvider>
            </QueryClientProvider>
        </MemoryRouter>,
    )
}

it('offers manual creation on a missing product', async () => {
    api.importBarcode.mockRejectedValue(
        Object.assign(new Error('Not found'), { problem: { status: 404 } }),
    )
    const user = userEvent.setup()
    renderScanner()
    await user.type(screen.getByLabelText('Enter barcode'), '3017620422003')
    await user.click(screen.getByRole('button', { name: 'Look up' }))
    expect(await screen.findByRole('link', { name: 'Create food manually' })).toHaveAttribute(
        'href',
        '/foods/new?barcode=3017620422003',
    )
})

it('keeps the barcode and retries a failed lookup', async () => {
    api.importBarcode.mockRejectedValueOnce(new Error('Offline')).mockResolvedValueOnce(food)
    const user = userEvent.setup()
    renderScanner()
    await user.type(screen.getByLabelText('Enter barcode'), '3017620422003')
    await user.click(screen.getByRole('button', { name: 'Look up' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Product lookup failed')
    expect(screen.getByLabelText('Enter barcode')).toHaveValue('3017620422003')
    expect(screen.queryByRole('link', { name: 'Create food manually' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry lookup' }))
    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
})

it('shows detection before the network responds and prevents duplicate submissions', async () => {
    let finish!: (food: Food) => void
    api.importBarcode.mockImplementationOnce(
        () =>
            new Promise<Food>((resolve) => {
                finish = resolve
            }),
    )
    const user = userEvent.setup()
    renderScanner()
    await user.type(screen.getByLabelText('Enter barcode'), '3017620422003')
    await user.click(screen.getByRole('button', { name: 'Look up' }))
    expect(await screen.findByRole('status', { name: '' })).toHaveTextContent(
        'Barcode found — finding product',
    )
    expect(screen.getByRole('button', { name: 'Look up' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Open camera' })).toBeDisabled()
    finish(food)
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
})
