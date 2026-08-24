/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/client" />

interface ImportMetaEnv {
    readonly VITE_AUTH_MODE?: 'supabase' | 'dev'
    readonly VITE_DEV_USER_ID?: string
    readonly VITE_SUPABASE_URL?: string
    readonly VITE_SUPABASE_PUBLISHABLE_KEY?: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}
