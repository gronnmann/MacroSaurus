import { render, screen } from '@testing-library/react'
import { MacroSummary, NutrientFacts } from './nutrition'

describe('nutrition components', () => {
    it('renders macros without treating missing values as errors', () => {
        render(<MacroSummary nutrients={{ energy_kcal: 540, protein_g: 35 }} />)
        expect(screen.getByText('540')).toBeVisible()
        expect(screen.getByText('35')).toBeVisible()
    })

    it('only lists nutrients whose values are known', () => {
        render(
            <NutrientFacts
                nutrients={{ iron_mg: 4.2 }}
                definitions={[
                    {
                        code: 'iron_mg',
                        displayName: 'Iron',
                        category: 'MINERAL',
                        unit: 'mg',
                        sortOrder: 1,
                    },
                    {
                        code: 'calcium_mg',
                        displayName: 'Calcium',
                        category: 'MINERAL',
                        unit: 'mg',
                        sortOrder: 2,
                    },
                ]}
            />,
        )
        expect(screen.getByText('Iron')).toBeVisible()
        expect(screen.queryByText('Calcium')).not.toBeInTheDocument()
    })
})
