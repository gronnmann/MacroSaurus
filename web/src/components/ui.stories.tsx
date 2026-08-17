import type { Meta, StoryObj } from '@storybook/react-vite'
import { Button, Card, SectionHeader, StatePanel } from './ui'

const meta = {
    title: 'Design system/Foundation',
    component: Button,
    tags: ['autodocs'],
} satisfies Meta<typeof Button>
export default meta
type Story = StoryObj<typeof meta>
export const Buttons: Story = {
    render: () => (
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            <Button>Primary</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="ghost">Ghost</Button>
            <Button variant="danger">Danger</Button>
            <Button disabled>Disabled</Button>
        </div>
    ),
}
export const CardAndEmptyState: Story = {
    render: () => (
        <div style={{ maxWidth: 600 }}>
            <Card>
                <SectionHeader eyebrow="YOUR LIBRARY" title="Saved foods" />
                <StatePanel
                    title="Nothing saved yet"
                    message="Create a private food to see it here."
                />
            </Card>
        </div>
    ),
}
