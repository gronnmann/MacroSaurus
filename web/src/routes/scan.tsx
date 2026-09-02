import { useMutation, useQuery } from '@tanstack/react-query'
import { BarcodeFormat, BrowserMultiFormatReader, type IScannerControls } from '@zxing/browser'
import { Camera, FileImage, Keyboard, ScanLine, X } from 'lucide-react'
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

export function ScanExperience({ onFoodReady }: { onFoodReady?: (food: Food) => void } = {}) {
    const [code, setCode] = useState('')
    const [camera, setCamera] = useState(false)
    const [cameraError, setCameraError] = useState('')
    const [codeError, setCodeError] = useState('')
    const [searchedCode, setSearchedCode] = useState('')
    const video = useRef<HTMLVideoElement>(null)
    const controls = useRef<IScannerControls | undefined>(undefined)
    const navigate = useNavigate()
    const toast = useToast()
    const features = useQuery({ queryKey: queryKeys.features, queryFn: api.features })
    const aiEnabled = Boolean(
        features.data?.aiLabelScan?.granted && features.data.aiLabelScan.available,
    )
    const importer = useMutation({
        mutationFn: api.importBarcode,
        onSuccess: (food) => {
            toast.push('Product ready')
            if (onFoodReady) onFoodReady(food)
            else navigate(`/track?food=${food.id}`)
        },
        onError: (error) => toast.push('Could not add product', error.message, 'error'),
    })
    const lookup = useMutation({
        mutationFn: api.barcode,
        onSuccess: (candidates, barcode) => {
            setSearchedCode(barcode)
            if (candidates.length > 0) importer.mutate(barcode)
        },
        onError: (_error, barcode) => {
            setSearchedCode(barcode)
            toast.push('Online lookup failed', 'You can still create this food manually.', 'error')
        },
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

    const readLabel = async (file?: File) => {
        if (!file) return
        try {
            scan.mutate({
                image: await prepareLabelImage(file),
                barcode: code,
                localeHint: navigator.language,
            })
        } catch (error) {
            toast.push(
                'Could not open photo',
                error instanceof Error ? error.message : undefined,
                'error',
            )
        }
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
            {(lookup.isPending || importer.isPending) && <Skeleton lines={3} />}
            {searchedCode === code && (lookup.data?.length === 0 || lookup.isError) && (
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
                    <Link
                        className="button button--primary"
                        to={`/foods/new?barcode=${encodeURIComponent(code)}`}
                    >
                        Create food manually
                    </Link>
                    {aiEnabled && (
                        <>
                            <label className="upload-zone">
                                <input
                                    type="file"
                                    accept="image/jpeg,image/png,image/webp"
                                    capture="environment"
                                    onChange={(event) => readLabel(event.target.files?.[0])}
                                />
                                <FileImage />
                                <b>Fill from a label photo</b>
                                <span>JPEG, PNG, or WebP</span>
                            </label>
                            <small>
                                The resized photo is sent for extraction and is not stored by
                                Macrosaurus.
                            </small>
                        </>
                    )}
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
            navigate(`/track?food=${food.id}`)
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
