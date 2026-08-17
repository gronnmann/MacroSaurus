import { AlertTriangle, CheckCircle2, LoaderCircle, X } from 'lucide-react'
import {
    createContext,
    type PropsWithChildren,
    type ReactNode,
    useCallback,
    useContext,
    useMemo,
    useState,
} from 'react'
import {
    Button as AriaButton,
    type ButtonProps,
    Dialog,
    DialogTrigger,
    Heading,
    Modal,
    ModalOverlay,
} from 'react-aria-components'
import footprint from '../assets/footprint.svg'
import focused from '../assets/mascot/focused.webp'
import goofy from '../assets/mascot/goofy.webp'
import proud from '../assets/mascot/proud.webp'

type AppButtonProps = Omit<ButtonProps, 'className' | 'isDisabled'> & {
    className?: string
    disabled?: boolean
    isDisabled?: boolean
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
}
export function Button({
    variant = 'primary',
    className = '',
    disabled,
    isDisabled,
    ...props
}: AppButtonProps) {
    return (
        <AriaButton
            {...props}
            isDisabled={disabled ?? isDisabled}
            className={`button button--${variant} ${className}`}
        />
    )
}

export function Card({
    children,
    className = '',
    tone = 'plain',
    id,
}: PropsWithChildren<{
    className?: string
    tone?: 'plain' | 'green' | 'orange' | 'dark'
    id?: string
}>) {
    return (
        <section id={id} className={`card card--${tone} ${className}`}>
            {children}
        </section>
    )
}

export function PageHeader({
    eyebrow,
    title,
    description,
    actions,
}: {
    eyebrow?: string
    title: string
    description?: string
    actions?: ReactNode
}) {
    return (
        <header className="page-header">
            <div>
                {eyebrow && <p className="eyebrow">{eyebrow}</p>}
                <h1>{title}</h1>
                {description && <p>{description}</p>}
            </div>
            {actions && <div className="page-actions">{actions}</div>}
        </header>
    )
}

export function SectionHeader({
    eyebrow,
    title,
    aside,
}: {
    eyebrow?: string
    title: string
    aside?: ReactNode
}) {
    return (
        <header className="section-header">
            <div>
                {eyebrow && <p className="eyebrow">{eyebrow}</p>}
                <h2>{title}</h2>
            </div>
            {aside && <div>{aside}</div>}
        </header>
    )
}

export function Badge({
    children,
    tone = 'neutral',
}: PropsWithChildren<{
    tone?: 'neutral' | 'green' | 'orange' | 'teal' | 'dark'
}>) {
    return <span className={`badge badge--${tone}`}>{children}</span>
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
    return (
        <span className="spinner" role="status">
            <LoaderCircle aria-hidden="true" />
            <span className="sr-only">{label}</span>
        </span>
    )
}

export function StatePanel({
    title,
    message,
    action,
    compact = false,
    kind = 'empty',
}: {
    title: string
    message: string
    action?: ReactNode
    compact?: boolean
    kind?: 'empty' | 'error' | 'success'
}) {
    const mascot = kind === 'error' ? goofy : kind === 'success' ? proud : focused
    return (
        <div
            className={`state-panel state-panel--${kind} ${compact ? 'state-panel--compact' : ''}`}
        >
            <img className="state-mascot" src={mascot} alt="" />
            <div>
                <h3>{title}</h3>
                <p>{message}</p>
                {action}
            </div>
            {kind === 'empty' && <img className="footprint" src={footprint} alt="" />}
        </div>
    )
}

export function ErrorPanel({
    error,
    title = 'Something went sideways',
}: {
    error: unknown
    title?: string
}) {
    return (
        <StatePanel
            kind="error"
            title={title}
            message={error instanceof Error ? error.message : 'Please try again.'}
        />
    )
}

export function Skeleton({ lines = 3 }: { lines?: number }) {
    return (
        <div className="skeleton" role="status" aria-label="Loading">
            {Array.from({ length: lines }, (_, index) => (
                // biome-ignore lint/suspicious/noArrayIndexKey: Decorative lines are static and never reordered.
                <i key={index} />
            ))}
        </div>
    )
}

export function ConfirmDialog({
    trigger,
    title,
    description,
    confirmLabel = 'Confirm',
    danger = false,
    onConfirm,
}: {
    trigger: ReactNode
    title: string
    description: string
    confirmLabel?: string
    danger?: boolean
    onConfirm: () => void
}) {
    return (
        <DialogTrigger>
            {trigger}
            <ModalOverlay className="modal-overlay">
                <Modal className="modal">
                    <Dialog className="dialog">
                        {({ close }) => (
                            <>
                                <button
                                    type="button"
                                    className="dialog-close"
                                    onClick={close}
                                    aria-label="Close"
                                >
                                    <X />
                                </button>
                                <Heading slot="title">{title}</Heading>
                                <p>{description}</p>
                                <div className="dialog-actions">
                                    <Button variant="ghost" onClick={close}>
                                        Cancel
                                    </Button>
                                    <Button
                                        variant={danger ? 'danger' : 'primary'}
                                        onClick={() => {
                                            onConfirm()
                                            close()
                                        }}
                                    >
                                        {confirmLabel}
                                    </Button>
                                </div>
                            </>
                        )}
                    </Dialog>
                </Modal>
            </ModalOverlay>
        </DialogTrigger>
    )
}

export function AppDialog({
    open,
    onOpenChange,
    title,
    children,
    wide = false,
}: PropsWithChildren<{
    open: boolean
    onOpenChange: (open: boolean) => void
    title: string
    wide?: boolean
}>) {
    return (
        <ModalOverlay
            className="modal-overlay"
            isOpen={open}
            onOpenChange={onOpenChange}
            isDismissable
        >
            <Modal className={`modal ${wide ? 'modal--wide' : ''}`}>
                <Dialog className="dialog">
                    {({ close }) => (
                        <>
                            <button
                                type="button"
                                className="dialog-close"
                                onClick={close}
                                aria-label="Close"
                            >
                                <X />
                            </button>
                            <Heading slot="title">{title}</Heading>
                            {children}
                        </>
                    )}
                </Dialog>
            </Modal>
        </ModalOverlay>
    )
}

type Toast = {
    id: number
    title: string
    message?: string
    tone: 'success' | 'error'
}
const ToastContext = createContext<{
    push: (title: string, message?: string, tone?: Toast['tone']) => void
} | null>(null)

export function ToastProvider({ children }: PropsWithChildren) {
    const [toasts, setToasts] = useState<Toast[]>([])
    const push = useCallback((title: string, message?: string, tone: Toast['tone'] = 'success') => {
        const id = Date.now()
        setToasts((current) => [...current, { id, title, message, tone }])
        window.setTimeout(
            () => setToasts((current) => current.filter((item) => item.id !== id)),
            4000,
        )
    }, [])
    const value = useMemo(() => ({ push }), [push])
    return (
        <ToastContext.Provider value={value}>
            {children}
            <div className="toast-region" aria-live="polite">
                {toasts.map((toast) => (
                    <div className={`toast toast--${toast.tone}`} key={toast.id}>
                        {toast.tone === 'success' ? <CheckCircle2 /> : <AlertTriangle />}
                        <div>
                            <b>{toast.title}</b>
                            {toast.message && <span>{toast.message}</span>}
                        </div>
                        <button
                            type="button"
                            aria-label="Dismiss"
                            onClick={() =>
                                setToasts((current) =>
                                    current.filter((item) => item.id !== toast.id),
                                )
                            }
                        >
                            <X />
                        </button>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    )
}

export function useToast() {
    const value = useContext(ToastContext)
    if (!value) throw new Error('useToast must be used inside ToastProvider')
    return value
}

export function Field({
    label,
    hint,
    error,
    children,
    className = '',
}: PropsWithChildren<{
    label: string
    hint?: string
    error?: string
    className?: string
}>) {
    return (
        // biome-ignore lint/a11y/noLabelWithoutControl: The supplied React child is the wrapped form control.
        <label className={`field ${className}`}>
            <span className="field-label">{label}</span>
            {children}
            {error && <span className="field-error">{error}</span>}
            {hint && !error && <span className="field-hint">{hint}</span>}
        </label>
    )
}
