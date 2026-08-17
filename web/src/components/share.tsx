import { useMutation } from '@tanstack/react-query'
import { Copy, Share2, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { api } from '../lib/api'
import { Button, ConfirmDialog, useToast } from './ui'

export function ShareButton({
    type,
    revisionId,
    label,
}: {
    type: 'FOOD' | 'RECIPE'
    revisionId: string
    label: string
}) {
    const toast = useToast()
    const [share, setShare] = useState<{ id: string; url: string }>()
    const create = useMutation({
        mutationFn: () => api.createShare({ resourceType: type, resourceRevisionId: revisionId }),
        onSuccess: async (data) => {
            const url = `${location.origin}/shared/${data.urlToken}`
            setShare({ id: data.id, url })
            await navigator.clipboard?.writeText(url).catch(() => undefined)
            toast.push('Share link copied', `${label} is ready to share.`)
        },
        onError: (error) => toast.push('Could not create link', error.message, 'error'),
    })
    const revoke = useMutation({
        mutationFn: (id: string) => api.revokeShare(id),
        onSuccess: () => {
            setShare(undefined)
            toast.push('Share link revoked')
        },
    })
    if (!share)
        return (
            <Button variant="secondary" onClick={() => create.mutate()} disabled={create.isPending}>
                <Share2 />
                {create.isPending ? 'Creating…' : 'Share'}
            </Button>
        )
    return (
        <div className="share-result">
            <Button
                variant="secondary"
                onClick={() => {
                    navigator.clipboard?.writeText(share.url)
                    toast.push('Link copied')
                }}
            >
                <Copy />
                Copy link
            </Button>
            <ConfirmDialog
                title="Revoke this share link?"
                description="Anyone using this link will immediately lose access."
                confirmLabel="Revoke link"
                danger
                onConfirm={() => revoke.mutate(share.id)}
                trigger={
                    <Button variant="ghost">
                        <Trash2 />
                        Revoke
                    </Button>
                }
            />
        </div>
    )
}
