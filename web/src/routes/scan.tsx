import { useMutation, useQuery } from '@tanstack/react-query'
import { BarcodeFormat, BrowserMultiFormatReader, type IScannerControls } from '@zxing/browser'
import { Camera, Check, FileImage, Keyboard, ScanLine, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FoodForm } from '../components/food-form'
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
import { kcal } from '../lib/utils'
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

export function ScanExperience() {
    const [code, setCode] = useState('')
    const [camera, setCamera] = useState(false)
    const [cameraError, setCameraError] = useState('')
    const [codeError, setCodeError] = useState('')
    const video = useRef<HTMLVideoElement>(null)
    const controls = useRef<IScannerControls | undefined>(undefined)
    const navigate = useNavigate()
    const toast = useToast()
    const lookup = useMutation({
        mutationFn: api.barcode,
        onError: (error) => toast.push('Barcode lookup failed', error.message, 'error'),
    })
    const importer = useMutation({
        mutationFn: api.importBarcode,
        onSuccess: (food) => {
            toast.push('Product ready')
            navigate(`/foods/${food.id}`)
        },
        onError: (error) => toast.push('Could not add product', error.message, 'error'),
    })
    const scan = useMutation({
        mutationFn: api.startScan,
        onSuccess: (job) => navigate(`/scan/${job.id}`),
        onError: (error) => toast.push('Could not read label', error.message, 'error'),
    })

    const lookUp = useCallback(
        (raw: string) => {
            const value = raw.replace(/\D/g, '')
            const error = barcodeError(value)
            setCode(value)
            setCodeError(error)
            if (!error) lookup.mutate(value)
        },
        [lookup.mutate],
    )

    useEffect(() => {
        if (!camera || !video.current) return
        const reader = new BrowserMultiFormatReader()
        reader.possibleFormats = [
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.ITF,
        ]
        let cancelled = false
        reader
            .decodeFromConstraints(
                { video: { facingMode: { ideal: 'environment' } } },
                video.current,
                (result) => {
                    if (result && !cancelled) {
                        controls.current?.stop()
                        setCamera(false)
                        lookUp(result.getText())
                    }
                },
            )
            .then((value) => {
                controls.current = value
            })
            .catch((error) => {
                setCameraError(error instanceof Error ? error.message : 'Camera access was denied')
                setCamera(false)
            })
        return () => {
            cancelled = true
            controls.current?.stop()
            controls.current = undefined
        }
    }, [camera, lookUp])

    const readLabel = (file?: File) => {
        if (!file) return
        const reader = new FileReader()
        reader.onload = () =>
            scan.mutate({
                image: String(reader.result),
                barcode: code,
                localeHint: navigator.language,
            })
        reader.onerror = () => toast.push('Could not open photo', undefined, 'error')
        reader.readAsDataURL(file)
    }

    return (
        <div className="scan-experience">
            <Card tone="dark" className="barcode-card">
                <SectionHeader eyebrow="BARCODE" title="Find the product" aside={<ScanLine />} />
                {camera ? (
                    <div className="camera-view">
                        <video ref={video} muted playsInline />
                        <span>Hold the barcode inside the frame</span>
                        <Button
                            variant="secondary"
                            onClick={() => {
                                controls.current?.stop()
                                setCamera(false)
                            }}
                        >
                            <X />
                            Stop camera
                        </Button>
                    </div>
                ) : (
                    <div className="scan-start">
                        <ScanLine />
                        <h3>Ready to scan</h3>
                        <p>
                            Camera images stay on this device. Only the barcode number is looked up.
                        </p>
                        <Button
                            onClick={() => {
                                setCameraError('')
                                setCamera(true)
                            }}
                        >
                            <Camera />
                            Open camera
                        </Button>
                        {cameraError && <p className="field-error">{cameraError}</p>}
                    </div>
                )}
                <div className="manual-code">
                    <Keyboard />
                    <input
                        aria-label="Enter barcode"
                        inputMode="numeric"
                        value={code}
                        onChange={(event) => {
                            setCode(event.target.value.replace(/\D/g, ''))
                            setCodeError('')
                        }}
                        placeholder="3017620422003"
                    />
                    <Button
                        variant="secondary"
                        disabled={!code || lookup.isPending}
                        onClick={() => lookUp(code)}
                    >
                        Look up
                    </Button>
                </div>
                {codeError && <p className="field-error">{codeError}</p>}
            </Card>
            {lookup.isPending && <Skeleton lines={3} />}{' '}
            {lookup.data && lookup.data.length > 0 && (
                <Card>
                    <SectionHeader
                        eyebrow="MATCH FOUND"
                        title={lookup.data.length === 1 ? lookup.data[0].name : 'Choose a product'}
                    />
                    <div className="candidate-list">
                        {lookup.data.map((candidate) => (
                            <article key={`${candidate.externalId}-${candidate.barcode}`}>
                                <div>
                                    <h3>{candidate.name}</h3>
                                    <p>
                                        {candidate.brand || 'No brand'} ·{' '}
                                        {kcal(candidate.nutrients)} kcal per 100
                                    </p>
                                </div>
                                <Button
                                    onClick={() => importer.mutate(candidate.barcode)}
                                    disabled={importer.isPending}
                                >
                                    <Check />
                                    Use product
                                </Button>
                            </article>
                        ))}
                    </div>
                </Card>
            )}
            {lookup.data?.length === 0 && (
                <Card className="label-fallback">
                    <SectionHeader
                        eyebrow="NO MATCH"
                        title="Read the nutrition label"
                        aside={<FileImage />}
                    />
                    <p>
                        Take one clear photo showing the full nutrition table. You can correct every
                        value before saving.
                    </p>
                    <label className="upload-zone">
                        <input
                            type="file"
                            accept="image/jpeg,image/png,image/webp"
                            capture="environment"
                            onChange={(event) => readLabel(event.target.files?.[0])}
                        />
                        <FileImage />
                        <b>Take one label photo</b>
                        <span>JPEG, PNG, or WebP</span>
                    </label>
                    <small>
                        The photo is used to read the label and is not stored by Macrosaurus.
                    </small>
                </Card>
            )}
        </div>
    )
}

export function ScanReviewPage() {
    const { id = '' } = useParams()
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
            navigate(`/foods/${food.id}`)
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
        return <ErrorPanel error={job.error || new Error(job.data?.errorMessage)} />
    if (!job.data?.draft)
        return (
            <StatePanel
                title="No label found"
                message="Try taking a clearer photo of the nutrition table."
                action={
                    <Link className="button button--primary" to="/track">
                        Try again
                    </Link>
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

function barcodeError(code: string) {
    if (![8, 12, 13, 14].includes(code.length)) return 'Enter an 8, 12, 13, or 14 digit barcode.'
    const check = [...code.slice(0, -1)]
        .reverse()
        .reduce((sum, digit, index) => sum + Number(digit) * (index % 2 === 0 ? 3 : 1), 0)
    return (10 - (check % 10)) % 10 === Number(code.at(-1))
        ? ''
        : 'That barcode number is not valid. Try scanning again.'
}
