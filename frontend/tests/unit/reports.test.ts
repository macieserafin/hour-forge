import { expect, it } from 'vitest'
import { getReportBreakdown, getReportEntries, getReportMembers, getReportProjects, getReportSummary } from '../../src/lib/api'
import { reportWeek } from '../../src/lib/reports'
import { json, mockApi, team } from '../support/api'
import { reportFilters, reportHandlers } from '../support/reports'

it.each([
  ['Pacific/Kiritimati', '2026-01-05', '2026-01-11'],
  ['America/Los_Angeles', '2025-12-29', '2026-01-04'],
  ['Europe/Warsaw', '2026-01-05', '2026-01-11'],
])('uses the team timezone for the default reporting week in %s', (zone, from, to) => {
  expect(reportWeek(zone, new Date('2026-01-04T23:30:00Z'))).toEqual({ ...reportFilters, from, to })
})

it('serializes report filters, sorting, pagination and cancellation using backend parameter names', async () => {
  const fetcher = mockApi(reportHandlers())
  const controller = new AbortController()
  await getReportEntries(team.id, { ...reportFilters, userId: 'worker /', projectId: 'project /', entryType: 'TIMER' }, 'DURATION:ASC', 3, controller.signal)
  const [path, options] = fetcher.mock.calls[0]!
  const url = new URL(String(path), window.location.origin)
  expect(Object.fromEntries(url.searchParams)).toEqual({ from: reportFilters.from, to: reportFilters.to,
    userIds: 'worker /', projectIds: 'project /', entryType: 'TIMER', sort: 'DURATION', direction: 'ASC', page: '3', size: '20' })
  expect(options).toMatchObject({ signal: controller.signal, credentials: 'include', cache: 'no-store' })
})

it('encodes team paths and preserves server pagination for grouping and filter options', async () => {
  const fetcher = mockApi({
    'GET /api/teams/team%20%2F/reports/breakdown': () => json({ data: { page: 2, hasNext: true } }),
    'GET /api/teams/team%20%2F/reports/members': () => json({ data: { page: 1, hasNext: true } }),
    'GET /api/teams/team%20%2F/reports/projects': () => json({ data: { page: 4, hasNext: false } }),
  })
  expect((await getReportBreakdown('team /', reportFilters, 'PROJECT', 2)).data).toEqual({ page: 2, hasNext: true })
  expect((await getReportMembers('team /', 1)).data).toEqual({ page: 1, hasNext: true })
  expect((await getReportProjects('team /', 4)).data).toEqual({ page: 4, hasNext: false })
  expect(String(fetcher.mock.calls[0]![0])).toContain('groupBy=PROJECT')
  expect(String(fetcher.mock.calls[1]![0])).toContain('size=100')
})

it.each([400, 401, 403, 404, 500])('maps report HTTP %s to a readable typed error', async status => {
  mockApi({ 'GET /api/teams/team/reports/summary': () => json({}, status) })
  await expect(getReportSummary(team.id, reportFilters)).rejects.toMatchObject({ name: 'ApiError', status })
})
