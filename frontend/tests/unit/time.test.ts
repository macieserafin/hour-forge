import { it, expect } from 'vitest'
import { formatDuration, teamToday, workDateRange } from '../../src/lib/time'

it.each([[0, '00:00:00'], [-10, '00:00:00'], [59.9, '00:00:59'], [60, '00:01:00'], [3661, '01:01:01'], [360000, '100:00:00']])
  ('formats %s seconds without rolling hours into days', (seconds, formatted) => { expect(formatDuration(Number(seconds))).toBe(formatted) })

it('derives today and the allowed work date range in the team time zone', () => {
  const now = new Date('2026-10-19T10:00:00Z')
  expect(teamToday('Europe/Warsaw', now)).toBe('2026-10-19')
  expect(teamToday('Pacific/Kiritimati', now)).toBe('2026-10-20')
  expect(workDateRange('Europe/Warsaw', now)).toEqual({ min: '2024-10-19', max: '2026-10-19' })
  expect(workDateRange('UTC', new Date('2028-02-29T12:00:00Z'))).toEqual({ min: '2026-02-28', max: '2028-02-29' })
})

it('writes saved time in the chosen format and keeps counters in clock form', async () => {
  const { formatClock, setTimeFormat } = await import('../../src/lib/time')
  setTimeFormat('DECIMAL')
  expect(formatDuration(5400)).toBe('1,50 h')
  expect(formatDuration(900)).toBe('0,25 h')
  expect(formatDuration(-5)).toBe('0,00 h')
  expect(formatClock(5400)).toBe('01:30:00')
  setTimeFormat('CLOCK')
  expect(formatDuration(5400)).toBe('01:30:00')
})
