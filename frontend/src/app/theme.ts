import { useEffect, useState } from 'react'

/**
 * Light, dark or following the system. Until the account stores it the choice lives in this browser;
 * index.html applies it before the first paint so the page does not flash.
 */
export type ThemeChoice = 'system' | 'light' | 'dark'
const storageKey = 'hourforge:theme'
const media = () => window.matchMedia('(prefers-color-scheme: dark)')

export function readTheme(): ThemeChoice {
  try {
    const stored = window.localStorage.getItem(storageKey)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch {
    /* Storage may be blocked. */
  }
  return 'system'
}

export function applyTheme(choice: ThemeChoice) {
  const dark = choice === 'dark' || (choice === 'system' && media().matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
}

export function useTheme() {
  const [choice, setChoice] = useState<ThemeChoice>(readTheme)
  useEffect(() => {
    applyTheme(choice)
    if (choice !== 'system') return
    const list = media()
    const follow = () => applyTheme('system')
    list.addEventListener('change', follow)
    return () => list.removeEventListener('change', follow)
  }, [choice])
  const change = (next: ThemeChoice) => {
    setChoice(next)
    try {
      window.localStorage.setItem(storageKey, next)
    } catch {
      /* Kept for this visit only. */
    }
  }
  return [choice, change] as const
}
