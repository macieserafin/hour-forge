import { it, expect } from 'vitest'
import { clearedSearch, filtersFromSearch, filtersToSearch, reportRanges, reportWeek } from '../../src/lib/reports'

// 22:30 UTC on Monday 19 October is already Tuesday 20 October in Warsaw (UTC+2).
const now = new Date('2026-10-19T22:30:00Z')

it('computes the quick ranges in the team time zone with weeks from Monday to Sunday', () => {
  expect(reportRanges('Europe/Warsaw', now)).toEqual({
    today: { from: '2026-10-20', to: '2026-10-20' },
    week: { from: '2026-10-19', to: '2026-10-25' },
    lastWeek: { from: '2026-10-12', to: '2026-10-18' },
    month: { from: '2026-10-01', to: '2026-10-31' },
  })
  expect(reportRanges('UTC', now).today.from).toBe('2026-10-19')
})

it('handles month ends, leap years and a week that spans two months', () => {
  const leap = reportRanges('UTC', new Date('2028-02-10T12:00:00Z'))
  expect(leap.month).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  const boundary = reportRanges('UTC', new Date('2026-12-31T12:00:00Z'))
  expect(boundary.week).toEqual({ from: '2026-12-28', to: '2027-01-03' })
  expect(boundary.lastWeek).toEqual({ from: '2026-12-21', to: '2026-12-27' })
})

it('reads filters from the address and falls back to the current week for anything malformed', () => {
  const fromAddress = filtersFromSearch(new URLSearchParams('team=t&from=2026-10-01&to=2026-10-10&person=p&project=r&type=TIMER&q=login'), 'UTC', now)
  expect(fromAddress).toEqual({ undated: false, from: '2026-10-01', to: '2026-10-10', userId: 'p', projectId: 'r', entryType: 'TIMER', q: 'login' })

  const week = reportWeek('UTC', now)
  const malformed = filtersFromSearch(new URLSearchParams(`from=yesterday&to=2026-10-10&type=OTHER&q=${'x'.repeat(300)}`), 'UTC', now)
  expect(malformed).toMatchObject({ from: week.from, to: week.to, entryType: '' })
  expect(malformed.q).toHaveLength(200)

  expect(filtersFromSearch(new URLSearchParams('undated=true&from=2026-10-01&to=2026-10-10'), 'UTC', now))
    .toMatchObject({ undated: true, from: '', to: '' })
  expect(filtersFromSearch(new URLSearchParams(''), 'UTC', now)).toMatchObject({ from: week.from, to: week.to, undated: false })
})

it('writes only the filter parameters and leaves the rest of the address alone', () => {
  const base = new URLSearchParams('team=t&view=team&from=2000-01-01&q=old')
  const written = filtersToSearch({ from: '2026-10-01', to: '2026-10-10', userId: '', projectId: 'r', entryType: '', q: ' new ' }, base)
  expect(Object.fromEntries(written)).toEqual({ team: 't', view: 'team', from: '2026-10-01', to: '2026-10-10', project: 'r', q: 'new' })
  const undated = filtersToSearch({ undated: true, from: '', to: '', userId: '', projectId: '', entryType: '' }, base)
  expect(Object.fromEntries(undated)).toEqual({ team: 't', view: 'team', undated: 'true' })
  expect(Object.fromEntries(clearedSearch(base))).toEqual({ team: 't', view: 'team' })
})

it('starts the quick week on Sunday when the person chose so', () => {
  const now = new Date('2026-10-21T10:00:00Z') // a Wednesday
  expect(reportRanges('UTC', now).week).toEqual({ from: '2026-10-19', to: '2026-10-25' })
  expect(reportRanges('UTC', now, 'SUNDAY').week).toEqual({ from: '2026-10-18', to: '2026-10-24' })
  expect(reportRanges('UTC', now, 'SUNDAY').lastWeek).toEqual({ from: '2026-10-11', to: '2026-10-17' })
  const sunday = new Date('2026-10-18T10:00:00Z')
  expect(reportRanges('UTC', sunday, 'SUNDAY').week).toEqual({ from: '2026-10-18', to: '2026-10-24' })
  expect(reportRanges('UTC', sunday).week).toEqual({ from: '2026-10-12', to: '2026-10-18' })
})
