import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
    plugins: [
        react(),
        VitePWA({
            registerType: 'autoUpdate',
            injectRegister: 'auto',
            includeAssets: ['dino-mark-512.png'],
            manifest: {
                name: 'Macrosaurus',
                short_name: 'Macrosaurus',
                description: 'Your personal macro and micronutrient coach.',
                theme_color: '#1e4d2b',
                background_color: '#f5f3e6',
                display: 'standalone',
                start_url: '/dashboard',
                icons: [
                    {
                        src: '/dino-mark-512.png',
                        sizes: '512x512',
                        type: 'image/png',
                        purpose: 'any maskable',
                    },
                ],
            },
            workbox: {
                clientsClaim: true,
                skipWaiting: true,
                navigateFallback: '/index.html',
                navigateFallbackDenylist: [/^\/api\//],
                runtimeCaching: [{ urlPattern: /\/api\/v1\//, handler: 'NetworkOnly' }],
            },
        }),
    ],
    server: { port: 5173, proxy: { '/api': 'http://localhost:8080' } },
})
