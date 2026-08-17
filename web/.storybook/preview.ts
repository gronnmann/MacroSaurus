import '@fontsource-variable/dm-sans'
import '@fontsource/barlow-condensed/700.css'
import '../src/styles/tokens.css'
import '../src/styles/global.css'
import '../src/styles/components.css'
import '../src/styles/responsive.css'
import type { Preview } from '@storybook/react-vite'

const preview: Preview = {
    parameters: {
        layout: 'padded',
        a11y: { test: 'error' },
        backgrounds: {
            default: 'cream',
            values: [
                { name: 'cream', value: '#f5f3e6' },
                { name: 'evergreen', value: '#0d2e1a' },
            ],
        },
    },
}
export default preview
