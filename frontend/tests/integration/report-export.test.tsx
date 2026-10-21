import { it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { renderApp } from '../support/render'
import { mockApi, team } from '../support/api'
import { reportHandlers } from '../support/reports'

let saved: HTMLAnchorElement[] = []

beforeEach(() => {
  saved = []
  URL.createObjectURL = vi.fn(() => 'blob:report')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { saved.push(this) })
})

afterEach(() => vi.restoreAllMocks())

const exportCall = (fetcher: ReturnType<typeof mockApi>) => fetcher.mock.calls
  .map(([path]) => new URL(String(path), window.location.origin)).find(url => url.pathname.endsWith('/reports/export.csv'))

it('downloads the CSV of the applied filters, not of unsaved form changes', async () => {
  const fetcher = mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/export.csv': () => new Response('x', { status: 200,
    headers: { 'Content-Type': 'text/csv', 'Content-Disposition': 'attachment; filename="hourforge-raport-2026-10-01_2026-10-10.csv"' } }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />, '/dashboard?team=team&from=2026-10-01&to=2026-10-10&q=login&project=project')
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  await userEvent.type(screen.getByLabelText('Szukaj w opisach'), ' unsaved')
  await userEvent.click(screen.getByRole('button', { name: 'Pobierz CSV' }))
  await waitFor(() => expect(saved).toHaveLength(1))
  expect(saved[0]!.download).toBe('hourforge-raport-2026-10-01_2026-10-10.csv')
  expect(Object.fromEntries(exportCall(fetcher)!.searchParams)).toEqual({ from: '2026-10-01', to: '2026-10-10', q: 'login', projectIds: 'project' })
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report')
})

it.each([
  [400, 'najwyżej 50 000 wpisów'],
  [403, 'Raport jest niedostępny'],
])('explains a refused CSV download (HTTP %s) and saves nothing', async (status, message) => {
  mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/export.csv': () => new Response(null, { status }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz CSV' }))
  expect(await screen.findByText(new RegExp(message))).toBeVisible()
  expect(saved).toHaveLength(0)
})

it('explains when too many reports were downloaded', async () => {
  mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/export.csv': () => new Response(null, { status: 429, headers: { 'Retry-After': '1200' } }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz CSV' }))
  expect(await screen.findByText('Zbyt wiele prób. Spróbuj ponownie za 20 min.')).toBeVisible()
})

it('downloads the Excel version of the applied filters', async () => {
  const fetcher = mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/export.xlsx': () => new Response('x', { status: 200,
    headers: { 'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'Content-Disposition': 'attachment; filename="hourforge-raport-2026-10-01_2026-10-10.xlsx"' } }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />, '/dashboard?team=team&from=2026-10-01&to=2026-10-10&q=login&issue=PROJ-7')
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  await userEvent.click(screen.getByRole('button', { name: 'Pobierz Excel' }))
  await waitFor(() => expect(saved).toHaveLength(1))
  expect(saved[0]!.download).toBe('hourforge-raport-2026-10-01_2026-10-10.xlsx')
  const call = fetcher.mock.calls.map(([path]) => new URL(String(path), window.location.origin)).find(url => url.pathname.endsWith('/reports/export.xlsx'))
  expect(Object.fromEntries(call!.searchParams)).toEqual({ from: '2026-10-01', to: '2026-10-10', q: 'login', issueKey: 'PROJ-7' })
})
