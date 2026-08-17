import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Clock3 } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { NutrientFacts } from '../components/nutrition'
import { Badge, Card, ErrorPanel, PageHeader, SectionHeader, Skeleton } from '../components/ui'
import { api } from '../lib/api'
import { formatDate, formatNumber, kcal } from '../lib/utils'
import type { Food, Recipe } from '../types'

export function SharedPage() {
    const { token = '' } = useParams()
    const shared = useQuery({
        queryKey: ['shared', token],
        queryFn: () => api.shared(token),
        retry: false,
    })
    if (shared.isLoading) return <Skeleton lines={8} />
    if (shared.error || !shared.data)
        return <ErrorPanel title="This share link is unavailable" error={shared.error} />
    const { resourceType, snapshot, expiresAt } = shared.data
    const nutrients =
        resourceType === 'FOOD'
            ? (snapshot as Food).nutrients
            : (snapshot as Recipe).nutrientsPerServing
    return (
        <>
            <PageHeader
                eyebrow={`SHARED ${resourceType}`}
                title={snapshot.name}
                description={`Shared from Macrosaurus.`}
                actions={
                    expiresAt && (
                        <Badge tone="orange">
                            <Clock3 />
                            Expires {formatDate(expiresAt)}
                        </Badge>
                    )
                }
            />
            <div className="shared-grid">
                <Card>
                    <SectionHeader
                        eyebrow={resourceType}
                        title={`${kcal(nutrients)} kcal ${resourceType === 'RECIPE' ? 'per serving' : ''}`}
                    />
                    {resourceType === 'FOOD' && (
                        <div className="detail-badges">
                            <Badge>
                                per{' '}
                                {formatNumber(
                                    (snapshot as Food).basisAmount,
                                    (snapshot as Food).basisUnit,
                                )}
                            </Badge>
                        </div>
                    )}
                    <NutrientFacts nutrients={nutrients} />
                </Card>
                <Card tone="green">
                    {resourceType === 'RECIPE' ? (
                        <>
                            <SectionHeader
                                eyebrow="INGREDIENTS"
                                title={`${formatNumber((snapshot as Recipe).servings)} servings`}
                            />
                            <div className="shared-ingredients">
                                {(snapshot as Recipe).ingredients.map((item) => (
                                    <div key={item.id}>
                                        <b>{item.name}</b>
                                        <span>{formatNumber(item.quantity, item.unit)}</span>
                                    </div>
                                ))}
                            </div>
                        </>
                    ) : (
                        <>
                            <SectionHeader eyebrow="PORTIONS" title="Serving options" />
                            <div className="shared-ingredients">
                                {(snapshot as Food).portions.map((item) => (
                                    <div key={item.id}>
                                        <b>{item.name}</b>
                                        <span>
                                            {item.gramWeight
                                                ? `${item.gramWeight} g`
                                                : `${item.milliliterVolume} ml`}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </>
                    )}
                    <Link className="button button--primary" to="/dashboard">
                        Open Macrosaurus
                        <ArrowRight />
                    </Link>
                </Card>
            </div>
        </>
    )
}

export function NotFoundPage() {
    return (
        <Card>
            <div className="not-found">
                <span>404</span>
                <h1>This trail went cold.</h1>
                <p>The page may have moved, but your macros are still right where you left them.</p>
                <Link className="button button--primary" to="/today">
                    Back to today
                </Link>
            </div>
        </Card>
    )
}
