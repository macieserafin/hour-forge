import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { CheckCircle2 } from 'lucide-react'

interface Toast {
  id: number
  text: string
}
const Context = createContext<(text: string) => void>(() => undefined)

/** Short confirmations of saved work. Read by screen readers once; they stay long enough to read (6 s). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const next = useRef(0)
  const show = useCallback((text: string) => {
    const id = ++next.current
    setToasts(current => [...current.slice(-2), { id, text }])
    window.setTimeout(() => setToasts(current => current.filter(toast => toast.id !== id)), 6000)
  }, [])
  return (
    <Context.Provider value={show}>
      {children}
      <div className="toast-region" role="status" aria-live="polite">
        {toasts.map(toast => (
          <p key={toast.id} className="toast">
            <CheckCircle2 size={18} aria-hidden="true" />
            {toast.text}
          </p>
        ))}
      </div>
    </Context.Provider>
  )
}

export const useToast = () => useContext(Context)
