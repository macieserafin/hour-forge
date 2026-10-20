import { it, expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'
import { reportActiveTimer, reportHandlers, reportPage } from '../support/reports'

const base = 'GET /api/teams/team/reports'

it('shows who is working now with the state and the worked time of each timer', async () => {
  mockApi({ ...reportHandlers(), [`${base}/active-timers`]: () => json({ ...reportPage([reportActiveTimer,
    { entry: { ...reportActiveTimer.entry, id: 'paused', displayName: 'Jan Nowak', pausedAt: '2026-10-19T08:30:00Z' }, elapsedSeconds: 60 }]),
    asOf: '2026-10-19T09:02:05Z' }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const panel = within(await screen.findByRole('region', { name: 'Kto teraz pracuje' }))
  const rows = await panel.findAllByRole('row')
  expect(rows).toHaveLength(3)
  expect(rows[1]).toHaveTextContent('Anna Kowalska')
  expect(rows[1]).toHaveTextContent('Pracuje')
  expect(rows[1]).toHaveTextContent('01:02:05')
  expect(rows[2]).toHaveTextContent('Jan Nowak')
  expect(rows[2]).toHaveTextContent('Wstrzymany')
})

it('says so when nobody is working', async () => {
  mockApi({ ...reportHandlers(), [`${base}/active-timers`]: () => json({ ...reportPage([]), asOf: '2026-10-19T09:02:05Z' }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  expect(await screen.findByText('Nikt nie ma teraz uruchomionego timera.')).toBeVisible()
})

it('does not request the live view when the team turned it off', async () => {
  const fetcher = mockApi(reportHandlers())
  renderApp(<OwnerDashboard userId="actor" team={{ ...team, activeTimersVisible: false }} />)
  expect(await screen.findByText('Podgląd aktywnych timerów jest wyłączony w ustawieniach zespołu.')).toBeVisible()
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  expect(fetcher.mock.calls.some(([path]) => String(path).includes('active-timers'))).toBe(false)
})

it('keeps the rest of the report when only the live view is refused', async () => {
  mockApi({ ...reportHandlers(), [`${base}/active-timers`]: () => json({}, 403) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const panel = within(await screen.findByRole('region', { name: 'Kto teraz pracuje' }))
  expect(await panel.findByRole('alert')).toHaveTextContent('podgląd aktywnych timerów można wyłączyć')
})

it('shows time per day and per person and project next to the existing tables', async () => {
  const fetcher = mockApi(reportHandlers())
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const days = within(await screen.findByRole('region', { name: 'Czas według dni' }))
  expect(await days.findByRole('cell', { name: /19 paź 2026/ })).toBeVisible()
  expect(days.getByRole('cell', { name: '00:15:00' })).toBeVisible()
  const mixed = within(screen.getByRole('region', { name: 'Czas według osób i projektów' }))
  const row = await mixed.findByRole('row', { name: /Anna Kowalska/ })
  expect(row).toHaveTextContent('Website')
  expect(row).toHaveTextContent('Projekt archiwalny')
  const groups = fetcher.mock.calls.map(([path]) => new URL(String(path), window.location.origin).searchParams.get('groupBy')).filter(Boolean)
  expect(groups.sort()).toEqual(['DAY', 'MEMBER', 'MEMBER_PROJECT', 'PROJECT'])
})
