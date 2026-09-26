import { useMutation, useQuery } from '@tanstack/react-query'
import { Camera, FileImage, Keyboard, ScanLine } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { BarcodeCamera } from '../components/barcode-camera'
import { FoodForm } from '../components/food-form'
import { PhotoInput } from '../components/photo-input'
import {
    Badge,
    Button,
    Card,
    ErrorPanel,
    PageHeader,
    SectionHeader,
    Skeleton,
    StatePanel,
    useToast,
} from '../components/ui'
import { api, queryKeys } from '../lib/api'
import { barcodeError } from '../lib/barcode'
import { prepareLabelImage } from '../lib/image'
import type { Food, FoodInput } from '../types'

export function ScanPage() {
    return (
        <>
            <PageHeader
                eyebrow="SCAN"
                title="Scan a product"
                description="Point your camera at the barcode. The number is read on this device."
            />
            <ScanExperience />
        </>
    )
}

export function ScanExperience({
    onFoodReady,
    embedded = false,
}: {
    onFoodReady?: (food: Food) => void
    embedded?: boolean
} = {}) {
    const [reviewId, setReviewId] = useState('')
    const [manual, setManual] = useState(false)
    const definitions = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
        enabled: manual,
    })
    const [code, setCode] = useState('')
    const [camera, setCamera] = useState(false)
    const [codeError, setCodeError] = useState('')
    const [searchedCode, setSearchedCode] = useState('')
    const active = useRef(true)
    const importing = useRef(false)
    useEffect(() => {
        active.current = true
        return () => {
            active.current = false
        }
    }, [])
    const closeCamera = useCallback(() => setCamera(false), [])
    const navigate = useNavigate()
    const toast = useToast()
    const features = useQuery({ queryKey: queryKeys.features, queryFn: api.features })
    const aiEnabled = Boolean(
        features.data?.aiLabelScan?.granted && features.data.aiLabelScan.available,
    )
    const importer = useMutation({
        mutationFn: api.importBarcode,
        networkMode: 'always',
        onSuccess: (food) => {
            if (!active.current) return
            toast.push('Product ready')
            if (onFoodReady) onFoodReady(food)
            else navigate(`/track?food=${food.id}`)
        },
        onSettled: () => {
            importing.current = false
        },
    })
    const notFound =
        importer.error instanceof Error &&
        'problem' in importer.error &&
        (importer.error.problem as { status?: number }).status === 404
    const scan = useMutation({
        mutationFn: async (file: File) =>
            api.startScan({
                image: await prepareLabelImage(file),
                barcode: code || null,
                localeHint: navigator.language,
            }),
        onSuccess: (job) => {
            setCamera(false)
            embedded ? setReviewId(job.id) : navigate(`/scan/${job.id}`)
        },
        onError: (error) => toast.push('Could not read label', error.message, 'error'),
    })

    const create = useMutation({
        mutationFn: api.createFood,
        onSuccess: (food) => onFoodReady?.(food),
        onError: (error) => toast.push('Could not create food', error.message, 'error'),
    })

    const lookUp = useCallback(
        (raw: string) => {
            if (importing.current) return
            const value = raw.replace(/\D/g, '')
            const error = barcodeError(value)
            setCode(value)
            setCodeError(error)
            if (!error) {
                setCamera(false)
                setSearchedCode(value)
                importing.current = true
                importer.mutate(value)
            }
        },
        [importer.mutate],
    )

    if (reviewId)
        return <ScanReview id={reviewId} onFoodReady={onFoodReady} onBack={() => setReviewId('')} />
    if (manual)
        return (
            <>
                <button type="button" className="sheet-back" onClick={() => setManual(false)}>
                    ← Scan barcode
                </button>
                <FoodForm
                    food={{
                        id: '',
                        revisionId: '',
                        revision: 0,
                        name: '',
                        barcode: code,
                        source: 'USER',
                        basisType: 'PER_100_G',
                        basisAmount: 100,
                        basisUnit: 'g',
                        nutrients: {},
                        portions: [],
                        createdAt: '',
                    }}
                    definitions={definitions.data}
                    pending={create.isPending}
                    onSubmit={(input) => create.mutate(input)}
                />
            </>
        )
    return (
        <div className="scan-experience">
            <Card tone="dark" className="barcode-card">
                <SectionHeader eyebrow="BARCODE" title="Find the product" aside={<ScanLine />} />
                {camera ? (
                    <BarcodeCamera onDetected={lookUp} onClose={closeCamera} />
                ) : (
                    <div className="scan-start">
                        <ScanLine />
                        <h3>{importer.isPending ? 'Barcode found' : 'Ready to scan'}</h3>
                        <p>
                            Camera images stay on this device. Only the barcode number is looked up.
                        </p>
                        <Button
                            disabled={importer.isPending}
                            onClick={() => {
                                importer.reset()
                                setCamera(true)
                            }}
                        >
                            <Camera />
                            Open camera
                        </Button>
                    </div>
                )}
                <div className="manual-code">
                    <Keyboard />
                    <input
                        aria-label="Enter barcode"
                        inputMode="numeric"
                        disabled={importer.isPending}
                        value={code}
                        onChange={(event) => {
                            setCode(event.target.value.replace(/\D/g, ''))
                            setCodeError('')
                        }}
                        placeholder="3017620422003"
                    />
                    <Button
                        variant="secondary"
                        disabled={!code || importer.isPending}
                        onClick={() => lookUp(code)}
                    >
                        Look up
                    </Button>
                </div>
                {codeError && <p className="field-error">{codeError}</p>}
            </Card>
            {importer.isPending && (
                <>
                    <p role="status">Barcode found — finding product…</p>
                    <Skeleton lines={3} />
                </>
            )}
            {searchedCode === code && importer.isError && !notFound && (
                <Card>
                    <p role="alert">
                        {navigator.onLine
                            ? 'Product lookup failed. Your barcode is retained; try again.'
                            : 'Barcode found. Reconnect to look up the product.'}
                    </p>
                    <Button onClick={() => lookUp(code)}>Retry lookup</Button>
                </Card>
            )}
            {searchedCode === code && notFound && (
                <Card className="label-fallback">
                    <SectionHeader
                        eyebrow="NO MATCH"
                        title="Create this food"
                        aside={<FileImage />}
                    />
                    <p>
                        The barcode was not found. Start a food with the barcode already filled in,
                        then add the values from the package.
                    </p>
                    {embedded ? (
                        <Button
                            onClick={() => {
                                setCamera(false)
                                setManual(true)
                            }}
                        >
                            Create food manually
                        </Button>
                    ) : (
                        <Link
                            className="button button--primary"
                            to={`/foods/new?barcode=${encodeURIComponent(code)}`}
                        >
                            Create food manually
                        </Link>
                    )}
                </Card>
            )}
            {aiEnabled && (
                <Card className="label-fallback">
                    <SectionHeader
                        eyebrow="AI LABEL READER"
                        title="Fill from a label photo"
                        aside={<FileImage />}
                    />
                    <p>
                        Upload or take a clear photo of the nutrition table, then review the
                        extracted values.
                    </p>
                    <PhotoInput
                        subject="label photo"
                        disabled={scan.isPending}
                        onFiles={(files) => scan.mutate(files[0])}
                    />
                    {scan.isPending && (
                        <p role="status">Reading the label… This can take a moment.</p>
                    )}
                    {scan.error && (
                        <p role="alert" className="field-error">
                            {scan.error.message}
                        </p>
                    )}
                    <small>The resized photo is sent to AI and is not stored by Macrosaurus.</small>
                </Card>
            )}
        </div>
    )
}

export function ScanReviewPage() {
    const { id = '' } = useParams()
    return <ScanReview id={id} />
}

function ScanReview({
    id,
    onFoodReady,
    onBack,
}: {
    id: string
    onFoodReady?: (food: Food) => void
    onBack?: () => void
}) {
    const navigate = useNavigate()
    const toast = useToast()
    const nutrients = useQuery({
        queryKey: queryKeys.nutrients,
        queryFn: api.nutrients,
    })
    const job = useQuery({
        queryKey: queryKeys.scan(id),
        queryFn: () => api.scan(id),
        refetchInterval: (query) =>
            query.state.data?.status === 'PENDING' || query.state.data?.status === 'PROCESSING'
                ? 1500
                : false,
    })
    const confirm = useMutation({
        mutationFn: (input: FoodInput) => api.confirmScan(id, input),
        onSuccess: (food) => {
            toast.push('Food created')
            if (onFoodReady) onFoodReady(food)
            else navigate(`/track?food=${food.id}`)
        },
        onError: (error) => toast.push('Could not save food', error.message, 'error'),
    })
    if (job.isLoading || job.data?.status === 'PENDING' || job.data?.status === 'PROCESSING')
        return (
            <div className="scan-pending">
                <ScanLine />
                <h1>Reading the label…</h1>
                <p>This usually takes a moment.</p>
                <Skeleton lines={4} />
            </div>
        )
    if (job.error || job.data?.errorMessage)
        return (
            <>
                <ErrorPanel error={job.error || new Error(job.data?.errorMessage)} />
                {onBack && <Button onClick={onBack}>Back to scan</Button>}
            </>
        )
    if (!job.data?.draft)
        return (
            <StatePanel
                title="No label found"
                message="Try taking a clearer photo of the nutrition table."
                action={
                    onBack ? (
                        <Button onClick={onBack}>Try again</Button>
                    ) : (
                        <Link className="button button--primary" to="/track">
                            Try again
                        </Link>
                    )
                }
            />
        )
    const draft = job.data.draft
    const initial: Food = {
        id: '',
        revisionId: '',
        revision: 0,
        name: draft.name || '',
        brand: draft.brand,
        barcode: draft.barcode,
        source: 'USER',
        basisType: draft.basisType || 'PER_100_G',
        basisAmount: draft.basisAmount || 100,
        basisUnit: draft.basisUnit || 'g',
        nutrients: Object.fromEntries(draft.nutrients.map((n) => [n.code, n.amount])),
        portions:
            draft.servingName && (draft.servingMassG || draft.servingVolumeMl)
                ? [
                      {
                          id: 'draft',
                          name: draft.servingName,
                          quantity: 1,
                          gramWeight: draft.servingMassG,
                          milliliterVolume: draft.servingVolumeMl,
                          default: true,
                      },
                  ]
                : [],
        createdAt: new Date().toISOString(),
    }
    return (
        <>
            <PageHeader
                eyebrow="CHECK THE LABEL"
                title="Does everything look right?"
                description="Correct anything that differs from the package."
            />
            {onBack && (
                <Button variant="secondary" onClick={onBack}>
                    Back to scan
                </Button>
            )}
            <div className="scan-warnings">
                {draft.warnings.map((warning) => (
                    <Badge tone="orange" key={warning}>
                        {warning}
                    </Badge>
                ))}
                {draft.allergens.length > 0 && (
                    <Badge>Allergens: {draft.allergens.join(', ')}</Badge>
                )}
            </div>
            <FoodForm
                food={initial}
                definitions={nutrients.data}
                submitLabel="Save food"
                pending={confirm.isPending}
                onSubmit={(input) => confirm.mutate(input)}
            />
        </>
    )
}
