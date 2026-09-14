import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Food } from '../types'
import { FoodForm } from './food-form'

it('shows the label nutrients in EU order and stores entered salt as sodium', async () => {
    const save = vi.fn()
    const user = userEvent.setup()
    const { container } = render(<FoodForm onSubmit={save} />)
    expect(
        [...container.querySelectorAll('.nutrient-editor input')].map((input) =>
            input.getAttribute('name'),
        ),
    ).toEqual([
        'nutrients.energy_kcal',
        'nutrients.fat_g',
        'nutrients.saturated_fat_g',
        'nutrients.carbohydrate_g',
        'nutrients.sugars_g',
        'nutrients.fiber_g',
        'nutrients.protein_g',
        'nutrients.sodium_mg',
    ])
    await user.type(screen.getByLabelText('Food name'), 'Soup')
    await user.type(screen.getByLabelText('Saturated fat (g)'), '1,5')
    await user.type(screen.getByLabelText('Salt (g)'), '1,2')
    await user.click(screen.getByRole('button', { name: 'Save food' }))
    await waitFor(() =>
        expect(save).toHaveBeenCalledWith(
            expect.objectContaining({ nutrients: { saturated_fat_g: 1.5, sodium_mg: 480 } }),
        ),
    )
})

it('round trips existing nutrient values while displaying salt in grams', async () => {
    const save = vi.fn()
    const user = userEvent.setup()
    const food: Food = {
        id: 'food',
        revisionId: 'revision',
        revision: 1,
        name: 'Soup',
        source: 'USER',
        basisType: 'PER_100_G',
        basisAmount: 100,
        basisUnit: 'g',
        nutrients: { energy_kcal: 100, sodium_mg: 480, saturated_fat_g: 0 },
        portions: [],
        createdAt: '',
    }
    render(<FoodForm food={food} onSubmit={save} />)
    expect(screen.getByLabelText('Salt (g)')).toHaveValue('1.2')
    await user.click(screen.getByRole('button', { name: 'Save food' }))
    await waitFor(() =>
        expect(save).toHaveBeenCalledWith(expect.objectContaining({ nutrients: food.nutrients })),
    )
})

it('orders optional fats and vitamins from definitions independently of catalog order', () => {
    const { container } = render(
        <FoodForm
            onSubmit={vi.fn()}
            definitions={[
                {
                    code: 'vitamin_c_mg',
                    displayName: 'Vitamin C',
                    unit: 'mg',
                    category: 'VITAMIN',
                    sortOrder: 1,
                },
                {
                    code: 'vitamin_d_ug',
                    displayName: 'Vitamin D',
                    unit: 'ug',
                    category: 'VITAMIN',
                    sortOrder: 2,
                },
                {
                    code: 'monounsaturated_fat_g',
                    displayName: 'Monounsaturated fat',
                    unit: 'g',
                    category: 'MACRO',
                    sortOrder: 3,
                },
            ]}
        />,
    )
    const fields = [...container.querySelectorAll('.nutrient-editor input')].map((input) =>
        input.getAttribute('name'),
    )
    expect(fields.indexOf('nutrients.saturated_fat_g')).toBeLessThan(
        fields.indexOf('nutrients.monounsaturated_fat_g'),
    )
    expect(fields.indexOf('nutrients.monounsaturated_fat_g')).toBeLessThan(
        fields.indexOf('nutrients.carbohydrate_g'),
    )
    expect(fields.indexOf('nutrients.vitamin_d_ug')).toBeLessThan(
        fields.indexOf('nutrients.vitamin_c_mg'),
    )
})
