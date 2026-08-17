import type { Meta, StoryObj } from '@storybook/react-vite'
import { MacroSummary, NutrientFacts } from './nutrition'

const meta = {
    title: 'Domain/Nutrition',
    component: MacroSummary,
    tags: ['autodocs'],
} satisfies Meta<typeof MacroSummary>
export default meta
type Story = StoryObj<typeof meta>
const values = {
    energy_kcal: 1840,
    protein_g: 142,
    carbohydrate_g: 205,
    fat_g: 61,
    fiber_g: 28,
    iron_mg: 8.4,
}
export const DailyMacros: Story = {
    args: {
        nutrients: values,
        targets: {
            energy_kcal: 2200,
            protein_g: 165,
            carbohydrate_g: 240,
            fat_g: 70,
        },
    },
}
export const CompleteNutrition: Story = {
    render: () => (
        <div style={{ maxWidth: 700 }}>
            <NutrientFacts
                nutrients={values}
                definitions={[
                    {
                        code: 'energy_kcal',
                        displayName: 'Energy',
                        category: 'ENERGY',
                        unit: 'kcal',
                        sortOrder: 1,
                    },
                    {
                        code: 'protein_g',
                        displayName: 'Protein',
                        category: 'MACRONUTRIENT',
                        unit: 'g',
                        sortOrder: 2,
                    },
                    {
                        code: 'iron_mg',
                        displayName: 'Iron',
                        category: 'MINERAL',
                        unit: 'mg',
                        sortOrder: 3,
                    },
                ]}
            />
        </div>
    ),
}
