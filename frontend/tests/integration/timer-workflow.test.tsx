import { it, expect, vi } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, history, timer, entry, team, project } from '../support/api'

const running = (hours: number, extra = {}) => ({ ...timer, startedAt: new Date(Date.now() - hours * 3600_000).toISOString(), ...extra })
const lastProjectKey = 'hourforge:last-project:actor:team'
const warning = /działa już ponad 12 godzin pracy/

it('discards a running timer only after confirmation and never saves its time', async () => {
  let active: typeof timer | null = running(0.1)
  let discarded: Record<string, unknown> | undefined
  const fetcher = mockApi({ 'GET /api/timer/active': () => active ? json(active) : json(null, 204),
    'POST /api/timer/discard': (_url, options) => { discarded = JSON.parse(String(options.body)); active = null; return json(null, 204) } })
  renderApp(<App />, '/time?team=team')
  const panel = within(await screen.findByRole('region', { name: 'Aktywny timer' }))
  await userEvent.click(panel.getByRole('button', { name: 'Odrzuć timer' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Anuluj' }))
  expect(discarded).toBeUndefined()
  await userEvent.click(panel.getByRole('button', { name: 'Odrzuć timer' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Odrzuć timer' }))
  expect(await screen.findByText('Żaden timer nie jest uruchomiony.')).toBeVisible()
  expect(discarded).toEqual({ entryId: timer.id })
  expect(screen.queryByText(/Praca zakończona i zapisana/)).not.toBeInTheDocument()
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/timer/stop'))).toBe(false)
})

it('explains a failed discard and keeps the timer', async () => {
  mockApi({ 'GET /api/timer/active': () => json(running(0.1)), 'POST /api/timer/discard': () => json({}, 409) })
  renderApp(<App />, '/time?team=team')
  const panel = within(await screen.findByRole('region', { name: 'Aktywny timer' }))
  await userEvent.click(panel.getByRole('button', { name: 'Odrzuć timer' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Odrzuć timer' }))
  expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('Nie można odrzucić tego timera')
  expect(screen.getByRole('region', { name: 'Aktywny timer' })).toBeVisible()
})

it('restores an unsent timer description after a reload and clears the draft once the timer ends', async () => {
  let active: typeof timer | null = running(0.1)
  mockApi({ 'GET /api/timer/active': () => active ? json(active) : json(null, 204),
    'POST /api/timer/stop': () => { active = null; return json({ ...timer, endedAt: '2026-10-19T10:02:00Z', durationSeconds: 120 }) } })
  const first = renderApp(<App />, '/time?team=team')
  const field = await screen.findByLabelText('Opis wykonanej pracy', { exact: false })
  expect(field).toHaveValue('Original work')
  await userEvent.clear(field)
  await userEvent.type(field, 'Draft text')
  first.unmount()
  renderApp(<App />, '/time?team=team')
  expect(await screen.findByLabelText('Opis wykonanej pracy', { exact: false })).toHaveValue('Draft text')
  await userEvent.click(screen.getByRole('button', { name: 'Zakończ pracę' }))
  expect(await screen.findByText(/Praca zakończona i zapisana/)).toBeVisible()
  expect(Object.keys(window.localStorage).filter(key => key.includes('timer-draft'))).toEqual([])
})

it('warns about a possibly forgotten timer after 12 hours of counted work and lets the user continue', async () => {
  mockApi({ 'GET /api/timer/active': () => json(running(13)) })
  renderApp(<App />, '/time?team=team')
  expect(await screen.findByText(warning)).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Kontynuuj' }))
  expect(screen.queryByText(warning)).not.toBeInTheDocument()
})

it('does not warn for a normal timer or when breaks keep counted work under the limit', async () => {
  mockApi({ 'GET /api/timer/active': () => json(running(13, { pausedDurationSeconds: 3 * 3600 })) })
  renderApp(<App />, '/time?team=team')
  await screen.findByRole('region', { name: 'Aktywny timer' })
  expect(screen.queryByText(warning)).not.toBeInTheDocument()
})

it('preselects the last used project only while it is still an active project', async () => {
  window.localStorage.setItem(lastProjectKey, 'project')
  mockApi()
  const first = renderApp(<App />, '/time?team=team')
  expect(await within(await screen.findByRole('region', { name: 'Rejestrowanie czasu w zespole' })).findByLabelText('Projekt')).toHaveValue('project')
  first.unmount()
  window.localStorage.setItem(lastProjectKey, 'removed-or-archived')
  renderApp(<App />, '/time?team=team')
  expect(await within(await screen.findByRole('region', { name: 'Rejestrowanie czasu w zespole' })).findByLabelText('Projekt')).toHaveValue('')
})

it('remembers the project of a started timer', async () => {
  let active: typeof timer | null = null
  mockApi({ 'GET /api/timer/active': () => active ? json(active) : json(null, 204),
    'POST /api/timer/start': () => { active = running(0.1); return json(active, 201) } })
  renderApp(<App />, '/time?team=team')
  const input = within(await screen.findByRole('region', { name: 'Rejestrowanie czasu w zespole' }))
  await userEvent.selectOptions(await input.findByLabelText('Projekt'), 'project')
  await userEvent.click(input.getByRole('button', { name: 'Uruchom timer' }))
  await screen.findByRole('region', { name: 'Aktywny timer' })
  expect(window.localStorage.getItem(lastProjectKey)).toBe('project')
})

it('keeps working when browser storage is blocked', async () => {
  const blocked = () => { throw new DOMException('blocked', 'SecurityError') }
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(blocked)
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(blocked)
  mockApi({ 'GET /api/timer/active': () => json(running(0.1)) })
  renderApp(<App />, '/time?team=team')
  const field = await screen.findByLabelText('Opis wykonanej pracy', { exact: false })
  await userEvent.type(field, ' more')
  expect(field).toHaveValue('Original work more')
  vi.restoreAllMocks()
})

it('starts a timer from a history entry and blocks the action for archived projects or a running timer', async () => {
  const old = { ...entry, id: 'old', projectId: 'old-project', projectName: 'Old project', description: 'Legacy' }
  let started: Record<string, unknown> | undefined
  let active: typeof timer | null = null
  mockApi({ 'GET /api/time-entries': () => json(history([entry, old])),
    'GET /api/teams/team/projects': url => json(url.searchParams.get('includeArchived') === 'true'
      ? [project, { ...project, id: 'old-project', name: 'Old project', archivedAt: team.createdAt }] : [project]),
    'GET /api/timer/active': () => active ? json(active) : json(null, 204),
    'POST /api/timer/start': (_url, options) => { started = JSON.parse(String(options.body)); active = running(0.01); return json(active, 201) } })
  renderApp(<App />, '/time?team=team')
  const again = await screen.findByRole('button', { name: /Pracuj ponownie nad: Website/ })
  await waitFor(() => expect(again).toBeEnabled())
  expect(screen.getByRole('button', { name: /Pracuj ponownie nad: Old project/ })).toBeDisabled()
  await userEvent.click(again)
  await screen.findByRole('region', { name: 'Aktywny timer' })
  expect(started).toEqual({ teamId: 'team', projectId: 'project', description: 'Original work' })
  await waitFor(() => expect(screen.getByRole('button', { name: /Pracuj ponownie nad: Website/ })).toBeDisabled())
})

it('sends the trimmed description search with the other history filters and keeps it in the active-filter state', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/time?team=team')
  await screen.findByText(entry.description!)
  await userEvent.type(screen.getByLabelText('Szukaj w opisach'), '  login 100%  ')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj' }))
  await waitFor(() => expect(fetcher.mock.calls.some(([url]) => new URL(String(url), window.location.origin).searchParams.get('q') === 'login 100%')).toBe(true))
  expect(await screen.findByText(/Po zastosowaniu filtrów/)).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Wyczyść filtry' }))
  expect(screen.getByLabelText('Szukaj w opisach')).toHaveValue('')
})
