import { expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { projectColorHex } from '../../src/lib/projectColors'
import { renderApp } from '../support/render'
import { entry, history, json, mockApi, project, team, timer } from '../support/api'
import { reportBucket, reportHandlers, reportPage } from '../support/reports'

const activityDays = (count: number, overrides: Record<string, number> = {}) => Array.from({ length: count }, (_, index) => {
  const workDate = new Date(Date.UTC(2026, 7, 3 + index)).toISOString().slice(0, 10)
  return { workDate, seconds: overrides[workDate] ?? 0 }
})

it('shows the time of all teams together when the person belongs to several', async () => {
  const fetcher = mockApi({
    'GET /api/teams': () => json([team, { ...team, id: 'other', name: 'Another team' }]),
    'GET /api/dashboard/overview': () => json({ todaySeconds: 5400, weekSeconds: 18000, teams: [
      { teamId: 'team', teamName: 'Studio', archived: false, todaySeconds: 3600, weekSeconds: 10800 },
      { teamId: 'other', teamName: 'Another team', archived: true, todaySeconds: 1800, weekSeconds: 7200 }] }),
  })
  renderApp(<App />, '/dashboard?team=team')
  const card = within(await screen.findByRole('region', { name: 'Wszystkie zespoły' }))
  expect(card.getByText('01:30:00')).toBeVisible()
  expect(card.getByText('05:00:00')).toBeVisible()
  expect(card.getByRole('row', { name: /Another team.*zarchiwizowany.*00:30:00.*02:00:00/ })).toBeVisible()
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/dashboard/overview?weekStart=MONDAY'))).toBe(true)
})

it('hides the all-teams block for a person with a single team', async () => {
  mockApi()
  renderApp(<App />, '/dashboard?team=team')
  await screen.findByLabelText('Czas dzisiaj')
  expect(screen.queryByRole('region', { name: 'Wszystkie zespoły' })).not.toBeInTheDocument()
})

it('draws a week-by-week activity grid that can be read as text', async () => {
  mockApi({ 'GET /api/dashboard/activity': () => json({ from: '2026-08-03', to: '2026-10-25', today: '2026-10-19', maxSeconds: 7200,
    days: activityDays(84, { '2026-10-15': 7200, '2026-10-16': 1800 }) }) })
  renderApp(<App />, '/dashboard?team=team')
  const grid = await screen.findByRole('list')
  const cells = within(grid).getAllByRole('listitem')
  expect(cells).toHaveLength(84)
  const busiest = cells.find(cell => cell.getAttribute('aria-label')?.includes('02:00:00'))!
  expect(busiest.className).toContain('heatmap-level-4')
  expect(busiest.getAttribute('aria-label')).toContain('15 października 2026')
  expect(cells.find(cell => cell.getAttribute('aria-label')?.includes('00:30:00'))!.className).toContain('heatmap-level-1')
  expect(cells.find(cell => cell.getAttribute('aria-label')?.includes('20 października'))!.getAttribute('aria-label')).toContain('jeszcze nie ma tego dnia')
  expect(screen.getByText(/Najbardziej pracowity dzień/)).toHaveTextContent('02:00:00')
})

it('lets the owner pick a colour for a project and sends it', async () => {
  let posted: Record<string, unknown> = {}
  mockApi({ 'POST /api/teams/team/projects': (_url, options) => { posted = JSON.parse(String(options.body)); return json({ ...project, id: 'new' }, 201) } })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Utwórz projekt' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Bez koloru')).toBeChecked()
  await userEvent.type(modal.getByLabelText('Nazwa projektu'), 'Painted')
  await userEvent.click(modal.getByLabelText('Morski'))
  await userEvent.click(modal.getByRole('button', { name: 'Utwórz projekt' }))
  await waitFor(() => expect(posted).toMatchObject({ name: 'Painted', color: 'TEAL' }))
})

it('keeps the current colour when editing and can remove it', async () => {
  let put: Record<string, unknown> = {}
  mockApi({
    'GET /api/teams/team/projects': () => json([{ ...project, color: 'AMBER' }]),
    'GET /api/teams/team/projects/project': () => json({ ...project, color: 'AMBER' }),
    'PUT /api/teams/team/projects/project': (_url, options) => { put = JSON.parse(String(options.body)); return json({ ...project, color: null }) },
  })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: project.name }))
  await userEvent.click(await screen.findByRole('button', { name: 'Edytuj projekt' }))
  const modal = within(screen.getByRole('dialog', { name: 'Edytuj projekt' }))
  expect(modal.getByLabelText('Bursztynowy')).toBeChecked()
  await userEvent.click(modal.getByLabelText('Bez koloru'))
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await waitFor(() => expect(put).toMatchObject({ color: null }))
})

it('shows the project colour next to the name in lists, history and the running timer', async () => {
  const swatch = (container: HTMLElement) => container.querySelector<HTMLElement>('.project-dot')
  mockApi({ 'GET /api/teams/team/projects': () => json([{ ...project, color: 'GREEN' }]), 'GET /api/teams/team/projects/project': () => json({ ...project, color: 'GREEN' }),
    'GET /api/time-entries': () => json(history([{ ...entry, projectColor: 'GREEN' }])),
    'GET /api/timer/active': () => json({ ...timer, startedAt: new Date(Date.now() - 600_000).toISOString(), updatedAt: new Date().toISOString() }) })
  renderApp(<App />, '/time?team=team')
  const running = await screen.findByRole('region', { name: 'Aktywny timer' })
  await waitFor(() => expect(swatch(running)).not.toBeNull())
  expect(swatch(running)!.style.background).toBe('rgb(63, 143, 79)')
  const table = await screen.findByRole('table')
  expect(swatch(table)).not.toBeNull()
  expect(swatch(table)!).toHaveAttribute('aria-hidden', 'true')
  expect(projectColorHex('GREEN')).toBe('#3f8f4f')
  expect(projectColorHex('<script>')).toBeUndefined()
  expect(projectColorHex(null)).toBeUndefined()
})

it('colours the bars of the project chart with the project colours', async () => {
  mockApi({ ...reportHandlers(),
    'GET /api/teams/team/reports/breakdown': url => json({ ...reportPage([{ ...reportBucket, projectId: 'project', projectName: 'Website', projectColor: 'RED', durationSeconds: 3600 },
      { ...reportBucket, projectId: 'plain', projectName: 'Plain', projectColor: null, durationSeconds: 1800 }]), groupBy: url.searchParams.get('groupBy') }) })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await waitFor(() => expect(document.querySelectorAll('figure svg rect.chart-bar').length).toBeGreaterThan(0))
  const fills = [...document.querySelectorAll<SVGRectElement>('figure svg rect.chart-bar')].map(rect => rect.style.fill)
  expect(fills).toContain('#cf4f5a')
})
