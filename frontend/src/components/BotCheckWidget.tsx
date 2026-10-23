import { useEffect, useRef, useState } from 'react'

interface Turnstile {
  render(element: HTMLElement, options: { sitekey: string; language?: string; callback: (token: string) => void;
    'expired-callback'?: () => void; 'error-callback'?: () => void }): string
  remove(widgetId: string): void
}

declare global {
  interface Window { turnstile?: Turnstile }
}

const SCRIPT_URL = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
let loading: Promise<void> | null = null

/** Loads Cloudflare's script once, and only on pages that need the check. */
function loadTurnstile(): Promise<void> {
  if (window.turnstile) return Promise.resolve()
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => { loading = null; reject(new Error('Turnstile could not be loaded')) }
    document.head.appendChild(script)
  })
  return loading
}

/**
 * The Cloudflare Turnstile check shown on registration when the server has keys. Each token works once, so a new
 * {@code attempt} renders a fresh widget after a failed registration.
 */
export function BotCheckWidget({ siteKey, attempt, onToken }: { siteKey: string; attempt: number; onToken: (token: string | null) => void }) {
  const container = useRef<HTMLDivElement>(null)
  const report = useRef(onToken)
  const [failed, setFailed] = useState(false)
  report.current = onToken

  useEffect(() => {
    let widgetId: string | undefined
    let cancelled = false
    report.current(null)
    loadTurnstile().then(() => {
      if (cancelled || !container.current || !window.turnstile) return
      widgetId = window.turnstile.render(container.current, { sitekey: siteKey, language: 'pl',
        callback: token => report.current(token), 'expired-callback': () => report.current(null), 'error-callback': () => report.current(null) })
    }).catch(() => { if (!cancelled) setFailed(true) })
    return () => { cancelled = true; if (widgetId && window.turnstile) window.turnstile.remove(widgetId) }
  }, [siteKey, attempt])

  return <div className="bot-check">
    <div ref={container} />
    {failed && <p className="form-error" role="alert">Nie udało się wczytać weryfikacji. Sprawdź połączenie lub blokadę skryptów i odśwież stronę.</p>}
  </div>
}
