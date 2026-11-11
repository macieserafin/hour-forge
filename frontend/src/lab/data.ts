/** Static sample for the direction choice, shaped like demo set C (Maja in three teams). Not used by the product. */
export interface SampleEntry { id: string; team: string; project: string; color: string; description: string; seconds: number; date: string | null; kind: 'timer' | 'manual' }

export const teams = [
  { id: 'atlas', name: 'Studio Atlas', role: 'Właścicielka', zone: 'Warszawa', today: 6300, week: 21600 },
  { id: 'nord', name: 'Agencja Nord', role: 'Administratorka', zone: 'Londyn', today: 2700, week: 12600 },
  { id: 'club', name: 'Koło naukowe', role: 'Członkini', zone: 'Nowy Jork', today: 0, week: 5400 },
]

export const projects = [
  { id: 'p1', team: 'Studio Atlas', name: 'Strona WWW', color: '#0F766E' },
  { id: 'p2', team: 'Studio Atlas', name: 'Aplikacja mobilna', color: '#2563EB' },
  { id: 'p3', team: 'Agencja Nord', name: 'Strona WWW', color: '#C2410C' },
  { id: 'p4', team: 'Agencja Nord', name: 'Kampania jesienna', color: '#BE123C' },
  { id: 'p5', team: 'Koło naukowe', name: 'Projekt badawczy', color: '#15803D' },
]

export const week = [
  { day: 'pon', date: '9 lis', seconds: 27000 },
  { day: 'wt', date: '10 lis', seconds: 23400 },
  { day: 'śr', date: '11 lis', seconds: 9000, today: true },
  { day: 'czw', date: '12 lis', seconds: 0 },
  { day: 'pt', date: '13 lis', seconds: 0 },
]

export const entries: SampleEntry[] = [
  { id: 'e1', team: 'Studio Atlas', project: 'Strona WWW', color: '#0F766E', description: 'Sprawdzenie wersji mobilnej', seconds: 5400, date: 'dziś', kind: 'timer' },
  { id: 'e2', team: 'Agencja Nord', project: 'Kampania jesienna', color: '#BE123C', description: 'Spotkanie z klientem', seconds: 2700, date: 'dziś', kind: 'manual' },
  { id: 'e3', team: 'Studio Atlas', project: 'Aplikacja mobilna', color: '#2563EB', description: 'Poprawki po testach', seconds: 900, date: 'dziś', kind: 'manual' },
  { id: 'e4', team: 'Agencja Nord', project: 'Strona WWW', color: '#C2410C', description: 'Implementacja formularza', seconds: 10800, date: 'wczoraj', kind: 'timer' },
]

export const undated = { seconds: 9000, count: 2 }

export function hours(seconds: number) {
  const h = Math.floor(seconds / 3600)
  const m = Math.round((seconds % 3600) / 60)
  return m ? `${h} h ${m} min` : `${h} h`
}

export function clock(seconds: number) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** "1h 30m", "90m", "1,5 h", "1:30" → seconds, or null. The product version gets full tests in P05. */
export function parseDuration(text: string): number | null {
  const value = text.trim().toLowerCase().replace(',', '.')
  if (!value) return null
  const colon = /^(\d{1,2}):(\d{2})$/.exec(value)
  if (colon) return Number(colon[1]) * 3600 + Number(colon[2]) * 60
  const parts = /^(?:(\d+(?:\.\d+)?)\s*h)?\s*(?:(\d+)\s*(?:m|min))?$/.exec(value)
  if (parts && (parts[1] || parts[2])) return Math.round(Number(parts[1] ?? 0) * 3600 + Number(parts[2] ?? 0) * 60)
  const plain = /^(\d+(?:\.\d+)?)$/.exec(value)
  if (plain) return Math.round(Number(plain[1]) * 3600)
  return null
}
