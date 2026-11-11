import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { pl } from './pl'
import { en } from './en'

/**
 * Interface language (O03: PL and EN). Until the account stores it (B05) the choice lives in this browser.
 * Server messages from the API layer are still Polish; they move to keys when the API module is split (P05/P06).
 */
export type Locale = 'pl' | 'en'
export type MessageKey = keyof typeof pl
const dictionaries: Record<Locale, Record<MessageKey, string>> = { pl, en }
const storageKey = 'hourforge:locale'

function initialLocale(): Locale {
  try {
    const stored = window.localStorage.getItem(storageKey)
    if (stored === 'pl' || stored === 'en') return stored
  } catch { /* Storage may be blocked. */ }
  return navigator.language?.toLowerCase().startsWith('pl') !== false ? 'pl' : 'en'
}

interface I18n { locale: Locale; setLocale: (locale: Locale) => void; t: (key: MessageKey, values?: Record<string, string | number>) => string }
const Context = createContext<I18n | null>(null)

export function format(template: string, values?: Record<string, string | number>) {
  return values ? template.replace(/\{(\w+)\}/g, (match, name: string) => name in values ? String(values[name]) : match) : template
}

export function I18nProvider({ children, locale: fixed }: { children: ReactNode; locale?: Locale }) {
  const [locale, setState] = useState<Locale>(() => fixed ?? initialLocale())
  const setLocale = useCallback((next: Locale) => {
    setState(next)
    try { window.localStorage.setItem(storageKey, next) } catch { /* Kept for this visit only. */ }
  }, [])
  const value = useMemo<I18n>(() => ({
    locale, setLocale,
    t: (key, values) => format(dictionaries[locale][key] ?? dictionaries.pl[key] ?? key, values),
  }), [locale, setLocale])
  if (typeof document !== 'undefined') document.documentElement.lang = locale
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useI18n() {
  const value = useContext(Context)
  if (!value) throw new Error('I18nProvider is missing.')
  return value
}
export const useT = () => useI18n().t

/**
 * Texts kept next to the screen that uses them: `pl` defines the keys, `en` must provide every one of them.
 * Returns a translator for the current language with the same {placeholder} rules as the shared dictionary.
 */
export function useMessages<K extends string>(messages: { pl: Record<K, string>; en: Record<K, string> }) {
  const { locale } = useI18n()
  return (key: K, values?: Record<string, string | number>) => format(messages[locale][key] ?? messages.pl[key], values)
}
