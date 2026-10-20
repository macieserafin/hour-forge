import { it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { PersonalDashboard } from '../../src/pages/PersonalDashboard'
import { reportRanges } from '../../src/lib/reports'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'
import { reportHandlers } from '../support/reports'

let saved: HTMLAnchorElement[] = []

beforeEach(() => {
  saved = []
  URL.createObjectURL = vi.fn(() => 'blob:pdf')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { saved.push(this) })
})

afterEach(() => vi.restoreAllMocks())

const pdf = (name: string) => new Response('%PDF-', { status: 200, headers: { 'Content-Type': 'application/pdf',
  'Content-Disposition': `attachment; filename="${name}"` } })
const urlsFor = (fetcher: ReturnType<typeof mockApi>, suffix: string) => fetcher.mock.calls
  .map(([path]) => new URL(String(path), window.location.origin)).filter(url => url.pathname.endsWith(suffix))

it('downloads the team PDF for the applied filters', async () => {
  const fetcher = mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/export.pdf': () => pdf('hourforge-raport-2026-10-01_2026-10-10.pdf') })
  renderApp(<OwnerDashboard userId="actor" team={team} />, '/dashboard?team=team&from=2026-10-01&to=2026-10-10&type=MANUAL')
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz PDF' }))
  await waitFor(() => expect(saved).toHaveLength(1))
  expect(saved[0]!.download).toBe('hourforge-raport-2026-10-01_2026-10-10.pdf')
  expect(Object.fromEntries(urlsFor(fetcher, '/reports/export.pdf')[0]!.searchParams)).toEqual({ from: '2026-10-01', to: '2026-10-10', entryType: 'MANUAL' })
})

it('shows the error of whichever download failed', async () => {
  mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/export.pdf': () => new Response(null, { status: 403 }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz PDF' }))
  expect(await screen.findByText(/Raport jest niedostępny/)).toBeVisible()
  expect(saved).toHaveLength(0)
})

it('lets a member download their own report for a chosen range', async () => {
  const fetcher = mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]),
    'GET /api/teams/team/reports/mine/export.pdf': () => pdf('hourforge-moj-raport-2026-10-12_2026-10-18.pdf') })
  renderApp(<PersonalDashboard userId="actor" />, '/dashboard?team=team')
  const range = await screen.findByLabelText('Zakres raportu')
  await userEvent.selectOptions(range, 'lastWeek')
  await userEvent.click(screen.getByRole('button', { name: 'Pobierz mój raport (PDF)' }))
  await waitFor(() => expect(saved).toHaveLength(1))
  expect(saved[0]!.download).toBe('hourforge-moj-raport-2026-10-12_2026-10-18.pdf')
  const lastWeek = reportRanges(team.timezone).lastWeek
  expect(Object.fromEntries(urlsFor(fetcher, '/mine/export.pdf')[0]!.searchParams)).toEqual(lastWeek)
})

it('explains why the personal report could not be downloaded', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]),
    'GET /api/teams/team/reports/mine/export.pdf': () => new Response(null, { status: 404 }) })
  renderApp(<PersonalDashboard userId="actor" />, '/dashboard?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz mój raport (PDF)' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Raport jest niedostępny')
})
