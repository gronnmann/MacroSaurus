/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
    readonly VITE_AUTH_MODE?: 'auth0' | 'dev'
    readonly VITE_DEV_USER_ID?: string
    readonly VITE_AUTH0_DOMAIN?: string
    readonly VITE_AUTH0_CLIENT_ID?: string
    readonly VITE_AUTH0_AUDIENCE?: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
