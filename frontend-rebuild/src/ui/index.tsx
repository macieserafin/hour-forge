import { useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { AlertCircle, CheckCircle2, Info, LoaderCircle, RefreshCw, X } from 'lucide-react'
import { useT } from '../content/i18n'

type Variant = 'primary' | 'accent' | 'ghost' | 'danger' | 'default'

/** A button that shows its own pending state; the label stays readable while it waits. */
export function Button({ variant = 'default', pending, wide, small, children, className = '', ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; pending?: boolean; wide?: boolean; small?: boolean }) {
  const classes = ['btn', variant !== 'default' && `btn-${variant}`, wide && 'btn-wide', small && 'btn-sm', className].filter(Boolean).join(' ')
  return <button type="button" {...rest} className={classes} disabled={rest.disabled || pending} aria-busy={pending || undefined}>
    {pending && <LoaderCircle size={16} className="spin" aria-hidden="true" />}{children}
  </button>
}

/** Label, control, hint and error wired together; the control receives the generated id through `children(id, describedBy)`. */
export function Field({ label, optional, hint, ok, error, children }: {
  label: ReactNode; optional?: boolean; hint?: ReactNode; ok?: ReactNode; error?: ReactNode
  children: (props: { id: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean }) => ReactNode
}) {
  const t = useT()
  const id = useId()
  const describedBy = [hint && `${id}-hint`, ok && `${id}-ok`, error && `${id}-error`].filter(Boolean).join(' ') || undefined
  return <div className="field">
    <label className="field-label" htmlFor={id}>{label}{optional && <> <i>{t('common.optional')}</i></>}</label>
    {children({ id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })}
    {hint && <span className="field-hint" id={`${id}-hint`}>{hint}</span>}
    {ok && !error && <span className="field-ok" id={`${id}-ok`}>{ok}</span>}
    {error && <span className="field-error" id={`${id}-error`} role="alert">{error}</span>}
  </div>
}

export function Notice({ tone, children, action }: { tone: 'error' | 'success' | 'info' | 'warning'; children: ReactNode; action?: ReactNode }) {
  const Icon = tone === 'error' || tone === 'warning' ? AlertCircle : tone === 'success' ? CheckCircle2 : Info
  return <div className={`notice notice-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
    <Icon size={18} aria-hidden="true" /><div>{children}{action}</div>
  </div>
}

export function Loading({ label }: { label?: string }) {
  const t = useT()
  return <p className="loading" role="status"><LoaderCircle size={18} className="spin" aria-hidden="true" />{label ?? t('common.loading')}</p>
}

export function LoadError({ message, onRetry, retrying }: { message?: string; onRetry: () => void; retrying?: boolean }) {
  const t = useT()
  return <Notice tone="error" action={<div><Button small onClick={onRetry} pending={retrying}><RefreshCw size={15} aria-hidden="true" />{t('common.retry')}</Button></div>}>
    {message ?? t('common.loadError')}
  </Notice>
}

export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return <div className="empty"><h3>{title}</h3>{children && <p className="muted">{children}</p>}{action}</div>
}

export function Dot({ color }: { color?: string | null }) {
  return <span className="dot" style={color ? { background: color } : undefined} aria-hidden="true" />
}

/** Native modal dialog: focus stays inside, Escape closes, focus returns to the opener. */
export function Dialog({ title, open, onClose, children, busy }: { title: string; open: boolean; onClose: () => void; children: ReactNode; busy?: boolean }) {
  const t = useT()
  const ref = useRef<HTMLDialogElement>(null)
  const headingId = useId()
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])
  return <dialog ref={ref} className="dialog" aria-labelledby={headingId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}
    onClick={event => { if (event.target === event.currentTarget && !busy) onClose() }}>
    {open && <div className="dialog-body">
      <div className="dialog-head"><h2 id={headingId}>{title}</h2>
        <button type="button" className="icon-btn" aria-label={t('common.close')} disabled={busy} onClick={onClose}><X size={18} /></button></div>
      {children}
    </div>}
  </dialog>
}
