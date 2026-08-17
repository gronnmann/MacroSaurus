import type { StorybookConfig } from '@storybook/react-vite'

const config: StorybookConfig = {
    stories: ['../src/**/*.stories.@(ts|tsx)'],
    addons: ['@storybook/addon-a11y', '@storybook/addon-vitest'],
    framework: { name: '@storybook/react-vite', options: {} },
    core: { disableWhatsNewNotifications: true },
    viteFinal: async (config) => ({
        ...config,
        plugins: (config.plugins || [])
            .flat()
            .filter(
                (plugin) =>
                    !plugin ||
                    typeof plugin !== 'object' ||
                    !('name' in plugin) ||
                    !String(plugin.name).includes('vite-plugin-pwa'),
            ),
    }),
}
export default config
