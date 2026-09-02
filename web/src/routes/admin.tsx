import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
    Badge,
    Card,
    ErrorPanel,
    PageHeader,
    Skeleton,
    StatePanel,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'

export function AdminPage() {
    const [query, setQuery] = useState('')
    const client = useQueryClient()
    const toast = useToast()
    const features = useQuery({ queryKey: queryKeys.features, queryFn: api.features })
    const users = useQuery({
        queryKey: queryKeys.adminUsers(query),
        queryFn: () => api.adminUsers(query),
        enabled: features.data?.isAdmin === true,
    })
    const update = useMutation({
        mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
            api.setAiLabelScan(userId, enabled),
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['admin-users'] })
            toast.push('AI access updated')
        },
        onError: (error) => toast.push('Could not update access', error.message, 'error'),
    })
    if (features.isLoading) return <Skeleton lines={5} />
    if (features.error) return <ErrorPanel error={features.error} />
    if (!features.data?.isAdmin) return <Navigate to="/profile" replace />
    return (
        <>
            <PageHeader
                eyebrow="ADMIN"
                title="Feature access"
                description="Enable paid AI label scanning only for users you trust. The server enforces every grant."
                actions={
                    <Badge tone="green">
                        <ShieldCheck /> Administrator
                    </Badge>
                }
            />
            <Card>
                <label className="search-field">
                    <input
                        value={query}
                        onChange={(event) => setQuery(event.target.value)}
                        placeholder="Search by name or user ID"
                    />
                </label>
                {users.isLoading ? (
                    <Skeleton lines={4} />
                ) : users.error ? (
                    <ErrorPanel error={users.error} />
                ) : users.data?.length ? (
                    <div className="ingredient-table">
                        {users.data.map((user) => (
                            <div key={user.userId}>
                                <div>
                                    <b>{user.displayName}</b>
                                    <small>{user.userId}</small>
                                </div>
                                <span>AI label scan</span>
                                <label className="check">
                                    <input
                                        type="checkbox"
                                        checked={user.aiLabelScanEnabled}
                                        disabled={update.isPending}
                                        onChange={(event) =>
                                            update.mutate({
                                                userId: user.userId,
                                                enabled: event.target.checked,
                                            })
                                        }
                                    />
                                    {user.aiLabelScanEnabled ? 'Enabled' : 'Disabled'}
                                </label>
                            </div>
                        ))}
                    </div>
                ) : (
                    <StatePanel
                        compact
                        title="No users found"
                        message="Users appear after completing their profile."
                    />
                )}
            </Card>
        </>
    )
}
