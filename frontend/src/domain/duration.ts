import type { Locale } from '../content/i18n'

export const MAX_ENTRY_SECONDS = 24 * 3600

/**
 * Reads a duration typed the way people say it: "45m", "1h 30m", "1h30", "90 min", "1,5", "1.5 h", "1:30", "2 godz".
 * A bare number is hours. Returns whole seconds, or null when the text is not a duration or is zero.
 */
export function parseDuration(text: string): number | null {
  const value = text.trim().toLowerCase().replace(/,/g, '.').replace(/\s+/g, ' ')
  if (!value) return null
  let seconds: number | null = null
  const colon = /^(\d{1,2}):([0-5]\d)$/.exec(value)
  const units =
    /^(?:(\d+(?:\.\d+)?) ?(?:h|godz\.?|godzin[ya]?|hours?|hrs?))? ?(?:(\d+) ?(?:m|min\.?|minut[ya]?|minutes?|mins?)?)?$/.exec(
      value,
    )
  const plain = /^(\d+(?:\.\d+)?)$/.exec(value)
  if (colon) seconds = Number(colon[1]) * 3600 + Number(colon[2]) * 60
  else if (plain) seconds = Math.round(Number(plain[1]) * 3600)
  else if (units && (units[1] || units[2])) {
    const minutesOnly = !units[1] && /m|min/.test(value)
    if (!units[1] && !minutesOnly) return null
    seconds = Math.round(Number(units[1] ?? 0) * 3600) + Number(units[2] ?? 0) * 60
  }
  return seconds && seconds > 0 ? seconds : null
}

/** How saved time is written, from the account preference "Zapis czasu": hours and minutes, or decimal hours. */
let style: 'CLOCK' | 'DECIMAL' = 'CLOCK'
let separator = ','
export function setDurationStyle(next: 'CLOCK' | 'DECIMAL', locale: Locale) {
  style = next
  separator = locale === 'pl' ? ',' : '.'
}

/** "2 h 30 min", "45 min", "3 h", or "2,5 h" in the decimal style; Polish and English use the same short units. */
export function formatDuration(seconds: number): string {
  if (style === 'DECIMAL')
    return `${
      (Math.max(0, seconds) / 3600)
        .toFixed(2)
        .replace(/\.?0+$/, '')
        .replace('.', separator) || '0'
    } h`
  const total = Math.max(0, Math.round(seconds / 60))
  const hours = Math.floor(total / 60)
  const minutes = total % 60
  if (!hours) return `${minutes} min`
  return minutes ? `${hours} h ${minutes} min` : `${hours} h`
}

/** Running clock "1:05:09". */
export function formatClock(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  const h = Math.floor(safe / 3600)
  const m = Math.floor((safe % 3600) / 60)
  const s = safe % 60
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const localeTag = (locale: Locale) => (locale === 'pl' ? 'pl-PL' : 'en-GB')

/** Calendar date of a work entry, written for people: "11 lis", "wt 10 lis". */
export function formatWorkDate(isoDate: string, locale: Locale, withWeekday = false): string {
  return new Date(`${isoDate}T12:00:00Z`).toLocaleDateString(localeTag(locale), {
    timeZone: 'UTC',
    day: 'numeric',
    month: 'short',
    ...(withWeekday ? { weekday: 'short' } : {}),
  })
}

export function formatLongDate(date: Date, locale: Locale): string {
  return date.toLocaleDateString(localeTag(locale), { weekday: 'long', day: 'numeric', month: 'long' })
}
