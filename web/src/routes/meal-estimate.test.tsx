import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ToastProvider } from '../components/ui'
import { MealEstimateEntry } from './meal-estimate'

const api = vi.hoisted(() => ({
    features: vi.fn(),
    estimateMeal: vi.fn(),
    quickTrack: vi.fn(),
    nutrients: vi.fn(),
}))
vi.mock('../lib/api', () => ({
    api,
    queryKeys: { features: ['features'], nutrients: ['nutrients'] },
}))
vi.mock('../lib/image', () => ({
    prepareLabelImage: async (file: File) => `data:image/jpeg;base64,${file.name}`,
}))

const result = {
    name: 'Chicken and rice',
    calories: 650,
    proteinG: 45,
    carbohydrateG: 70,
    fatG: 20,
    fiberG: 3,
    assumptions: ['Includes one tablespoon of oil'],
}

function setup() {
    const done = vi.fn()
    render(
        <QueryClientProvider
            client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
        >
            <ToastProvider>
                <MealEstimateEntry onDone={done} />
            </ToastProvider>
        </QueryClientProvider>,
    )
    return { user: userEvent.setup(), done }
}

beforeEach(() => {
    vi.clearAllMocks()
    api.features.mockResolvedValue({ aiLabelScan: { granted: true, available: true } })
    api.estimateMeal.mockResolvedValue(result)
    api.quickTrack.mockResolvedValue({})
    api.nutrients.mockResolvedValue([])
})

it('submits photos together with text, then logs only the reviewed values', async () => {
    const { user, done } = setup()
    await screen.findByRole('button', { name: 'Upload meal photo' })
    expect(screen.getByRole('button', { name: 'Estimate calories' })).toBeDisabled()
    await user.upload(screen.getByLabelText('Upload meal photo'), [
        new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
        new File(['b'], 'b.jpg', { type: 'image/jpeg' }),
    ])
    await screen.findByAltText('Meal view 2')
    await user.type(screen.getByLabelText('Meal description'), '200 g chicken with rice and oil')
    await user.click(screen.getByRole('button', { name: 'Estimate calories' }))
    await screen.findByDisplayValue('Chicken and rice')
    expect(api.estimateMeal).toHaveBeenCalledWith(
        {
            text: '200 g chicken with rice and oil',
            images: ['data:image/jpeg;base64,a.jpg', 'data:image/jpeg;base64,b.jpg'],
            localeHint: navigator.language,
        },
        expect.anything(),
    )
    expect(api.quickTrack).not.toHaveBeenCalled()
    expect(screen.getByText('Includes one tablespoon of oil')).toBeVisible()
    await user.clear(screen.getByLabelText('Calories'))
    await user.type(screen.getByLabelText('Calories'), '720')
    await user.click(screen.getByText('More nutrients'))
    await user.type(screen.getByLabelText('Saturated fat (g)'), '4,5')
    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-08-18' } })
    fireEvent.change(screen.getByLabelText('Time'), { target: { value: '12:45' } })
    await user.click(screen.getByRole('button', { name: 'Add to Food Log' }))
    await waitFor(() => expect(done).toHaveBeenCalled())
    expect(api.quickTrack).toHaveBeenCalledWith(
        expect.objectContaining({
            name: result.name,
            calories: 720,
            fiberG: 3,
            additionalNutrients: { saturated_fat_g: 4.5 },
            proteinG: 45,
            saveAsFood: false,
            localDate: '2026-08-18',
            consumedAt: new Date('2026-08-18T12:45:00').toISOString(),
        }),
    )
})

it('retains text and photos after failure and supports camera-only retry', async () => {
    api.estimateMeal.mockRejectedValueOnce(new Error('AI is busy. Please try again.'))
    const { user } = setup()
    await screen.findByRole('button', { name: 'Take meal photo' })
    const camera = screen.getByLabelText('Take meal photo')
    expect(camera).toHaveAttribute('capture', 'environment')
    expect(screen.getByLabelText('Upload meal photo')).not.toHaveAttribute('capture')
    await user.upload(camera, new File(['a'], 'camera.jpg', { type: 'image/jpeg' }))
    await screen.findByAltText('Meal view 1')
    await user.click(screen.getByRole('button', { name: 'Estimate calories' }))
    await screen.findByRole('alert')
    expect(screen.getByAltText('Meal view 1')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Estimate calories' }))
    await screen.findByDisplayValue(result.name)
    expect(api.estimateMeal).toHaveBeenLastCalledWith(
        expect.objectContaining({ text: '', images: ['data:image/jpeg;base64,camera.jpg'] }),
        expect.anything(),
    )
})

it('supports text-only estimates and prevents more than three photos', async () => {
    const { user } = setup()
    await screen.findByRole('button', { name: 'Upload meal photo' })
    await user.upload(
        screen.getByLabelText('Upload meal photo'),
        [1, 2, 3, 4].map((i) => new File(['a'], `${i}.jpg`, { type: 'image/jpeg' })),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('up to 3 photos')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Meal description'), 'Two eggs')
    await user.click(screen.getByRole('button', { name: 'Estimate calories' }))
    await waitFor(() =>
        expect(api.estimateMeal).toHaveBeenCalledWith(
            expect.objectContaining({ text: 'Two eggs', images: [] }),
            expect.anything(),
        ),
    )
})

it('does not offer submission without an AI grant', async () => {
    api.features.mockResolvedValue({ aiLabelScan: { granted: false, available: true } })
    setup()
    expect(await screen.findByText(/require AI access/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Estimate calories' })).not.toBeInTheDocument()
    expect(api.estimateMeal).not.toHaveBeenCalled()
})
