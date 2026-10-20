import { it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { reportRanges } from '../../src/lib/reports'
import { renderApp } from '../support/render'
import { mockApi, team } from '../support/api'
import { reportHandlers } from '../support/reports'

const summaryCalls = (fetcher: ReturnType<typeof mockApi>) => fetcher.mock.calls
  .map(([path]) => new URL(String(path), window.location.origin)).filter(url => url.pathname.endsWith('/reports/summary'))

it('opens with the filters from the address and shows them in the form', async () => {
  const fetcher = mockApi(reportHandlers())
  renderApp(<OwnerDashboard userId="actor" team={team} />, '/dashboard?team=team&view=team&from=2026-10-01&to=2026-10-10&type=TIMER&q=login')
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  expect(Object.fromEntries(summaryCalls(fetcher)[0]!.searchParams)).toMatchObject({ from: '2026-10-01', to: '2026-10-10', entryType: 'TIMER', q: 'login' })
  expect(screen.getByLabelText('Data od')).toHaveValue('2026-10-01')
  expect(screen.getByLabelText('Szukaj w opisach')).toHaveValue('login')
  expect(screen.getByLabelText('Rodzaj wpisu')).toHaveValue('TIMER')
})

it('applies a quick range and the description search to every report request', async () => {
  const fetcher = mockApi(reportHandlers())
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  const lastWeek = reportRanges(team.timezone).lastWeek
  await userEvent.click(screen.getByRole('button', { name: 'Poprzedni tydzień' }))
  await waitFor(() => expect(summaryCalls(fetcher).some(url => url.searchParams.get('from') === lastWeek.from)).toBe(true))
  await userEvent.type(screen.getByLabelText('Szukaj w opisach'), ' faktura ')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  await waitFor(() => {
    const requests = fetcher.mock.calls.map(([path]) => new URL(String(path), window.location.origin))
      .filter(url => url.pathname.includes('/reports/') && url.searchParams.get('q') === 'faktura')
    expect(requests.map(url => url.pathname.split('/').at(-1)).sort()).toEqual(['breakdown', 'breakdown', 'breakdown', 'breakdown', 'entries', 'summary'])
    expect(requests.every(url => url.searchParams.get('from') === lastWeek.from && url.searchParams.get('to') === lastWeek.to)).toBe(true)
  })
})

it('does not send blank searches and rejects ranges over 366 days without a request', async () => {
  const fetcher = mockApi(reportHandlers())
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  await userEvent.type(screen.getByLabelText('Szukaj w opisach'), '   ')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  expect(summaryCalls(fetcher).every(url => !url.searchParams.has('q'))).toBe(true)
  const before = summaryCalls(fetcher).length
  await userEvent.clear(screen.getByLabelText('Data od'))
  await userEvent.type(screen.getByLabelText('Data od'), '2024-01-01')
  await userEvent.clear(screen.getByLabelText('Data do'))
  await userEvent.type(screen.getByLabelText('Data do'), '2026-01-01')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('najwyżej 366 dni')
  expect(summaryCalls(fetcher)).toHaveLength(before)
})

it('clears the filters back to the current week', async () => {
  const fetcher = mockApi(reportHandlers())
  renderApp(<OwnerDashboard userId="actor" team={team} />, '/dashboard?team=team&from=2026-10-01&to=2026-10-10&q=login')
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  await userEvent.click(screen.getByRole('button', { name: 'Wyczyść filtry' }))
  const week = reportRanges(team.timezone).week
  await waitFor(() => expect(summaryCalls(fetcher).at(-1)!.searchParams.get('from')).toBe(week.from))
  expect(screen.getByLabelText('Szukaj w opisach')).toHaveValue('')
})
