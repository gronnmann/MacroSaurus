import '@fontsource-variable/dm-sans'
import '@fontsource/barlow-condensed/700.css'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { AppAuthProvider } from './lib/auth'
import { installDecimalCommaSupport } from './lib/decimal-input'
import './styles/tokens.css'
import './styles/global.css'
import './styles/components.css'
import './styles/responsive.css'

const queryClient = new QueryClient({
    defaultOptions: {
        queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false },
        mutations: { retry: 0 },
    },
})

installDecimalCommaSupport()

const root = document.getElementById('root')
if (!root) throw new Error('Macrosaurus could not find its application root.')

createRoot(root).render(
    <StrictMode>
        <AppAuthProvider>
            <QueryClientProvider client={queryClient}>
                <App />
            </QueryClientProvider>
        </AppAuthProvider>
    </StrictMode>,
)
