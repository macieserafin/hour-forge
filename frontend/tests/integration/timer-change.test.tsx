import { afterEach, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { entry, json, mockApi, project, team, timer } from '../support/api'

const second = { ...project, id: 'second', name: 'Second project' }
const running = (hours: number, extra = {}) => ({ ...timer, startedAt: new Date(Date.now() - hours * 3600_000).toISOString(), updatedAt: new Date().toISOString(), ...extra })
const prefs = (overrides = {}) => ({ 'GET /api/account/preferences': () => json({ defaultTeamId: null, startPage: 'DASHBOARD', timeFormat: 'CLOCK', weekStart: 'MONDAY',
  longTimerEmail: true, browserNotifications: false, longTimerHours: 10, titleTimer: true, ...overrides }) })
const jira = { available: true, siteConnected: true, siteName: 'Studio', siteUrl: 'https://studio.atlassian.net', userConnected: true, canManage: false }

afterEach(() => { document.title = 'HourForge' })

it('changes the project, note and Jira task of the running timer without stopping it', async () => {
  let active = running(0.5, { description: 'Original', jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' })
  let sent: Record<string, unknown> = {}
  mockApi({
    'GET /api/timer/active': () => json(active),
    'GET /api/teams/team/projects': () => json([project, second]),
    'GET /api/integrations/jira/status': () => json(jira),
    'POST /api/timer/update': (_url, options) => { sent = JSON.parse(String(options.body)); active = { ...active, projectId: 'second', description: String(sent.description), jiraIssueId: null as never, jiraIssueKey: null as never }; return json(active) },
  })
  renderApp(<App />, '/time?team=team')
  const panel = within(await screen.findByRole('region', { name: 'Aktywny timer' }))
  await userEvent.click(panel.getByRole('button', { name: 'Zmień projekt lub zadanie' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Opis pracy', { exact: false })).toHaveValue('Original')
  expect(await modal.findByText('PROJ-1')).toBeVisible()
  await userEvent.selectOptions(await modal.findByLabelText('Projekt'), 'second')
  await userEvent.clear(modal.getByLabelText('Opis pracy', { exact: false }))
  await userEvent.type(modal.getByLabelText('Opis pracy', { exact: false }), 'Moved on')
  await userEvent.click(modal.getByRole('button', { name: 'Usuń powiązanie z zadaniem PROJ-1' }))
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(sent).toEqual({ entryId: timer.id, projectId: 'second', description: 'Moved on', jiraIssueId: null })
  expect(panel.getByLabelText('Opis wykonanej pracy', { exact: false })).toHaveValue('Moved on')
})

it('keeps the dialog open and explains a refused change', async () => {
  mockApi({ 'GET /api/timer/active': () => json(running(0.5)), 'GET /api/teams/team/projects': () => json([project, second]),
    'POST /api/timer/update': () => json({}, 409) })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Zmień projekt lub zadanie' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.click(await modal.findByRole('button', { name: 'Zapisz zmiany' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Nie można zmienić tego timera')
})

it('switches to another task in one request and shows the finished work', async () => {
  let active: ReturnType<typeof running> | null = running(0.5, { description: 'Finish me' })
  let sent: { stop: Record<string, unknown>; start: Record<string, unknown> } | undefined
  mockApi({
    'GET /api/timer/active': () => json(active),
    'GET /api/teams/team/projects': () => json([project, second]),
    'POST /api/timer/switch': (_url, options) => {
      sent = JSON.parse(String(options.body))
      active = running(0, { id: 'next', projectId: 'second', description: 'Next up' })
      return json({ stopped: { ...entry, id: timer.id, entryType: 'TIMER', durationSeconds: 1800 }, started: active })
    },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Przełącz zadanie' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Opis kończonej pracy', { exact: false })).toHaveValue('Finish me')
  await userEvent.click(modal.getByRole('button', { name: 'Przełącz' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Wybierz projekt')
  expect(sent).toBeUndefined()
  await userEvent.selectOptions(await modal.findByLabelText('Nowy projekt'), 'second')
  await userEvent.type(modal.getByLabelText('Opis nowej pracy', { exact: false }), 'Next up')
  await userEvent.click(modal.getByRole('button', { name: 'Przełącz' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(sent).toEqual({ stop: { entryId: timer.id, description: 'Finish me' }, start: { teamId: 'team', projectId: 'second', description: 'Next up', jiraIssueId: null } })
  expect(await screen.findByText(/Praca zakończona i zapisana/)).toBeVisible()
  expect(screen.getByLabelText('Opis wykonanej pracy', { exact: false })).toHaveValue('Next up')
})

it('lets a paused or brand-new timer be changed but not switched', async () => {
  mockApi({ 'GET /api/timer/active': () => json(running(0.5, { pausedAt: new Date().toISOString(), pausedDurationSeconds: 0 })) })
  renderApp(<App />, '/time?team=team')
  expect(await screen.findByRole('button', { name: 'Zmień projekt lub zadanie' })).toBeEnabled()
  expect(screen.getByRole('button', { name: 'Przełącz zadanie' })).toBeDisabled()
})

it('shows the running time in the tab title and restores the title afterwards', async () => {
  document.title = 'HourForge — Twoja przestrzeń pracy'
  let active: ReturnType<typeof running> | null = running(1.5)
  mockApi({ ...prefs(), 'GET /api/timer/active': () => active ? json(active) : json(null, 204), 'GET /api/teams/team/projects/project': () => json({ ...project, name: 'Website' }) })
  const view = renderApp(<App />, '/dashboard?team=team')
  await waitFor(() => expect(document.title).toMatch(/^▶ 01:30:0\d · Website$/))
  view.unmount()
  expect(document.title).toBe('HourForge — Twoja przestrzeń pracy')
  active = null
})

it('marks a paused timer in the title', async () => {
  mockApi({ ...prefs(), 'GET /api/timer/active': () => json(running(1, { pausedAt: new Date(Date.now() - 1800_000).toISOString(), pausedDurationSeconds: 0 })),
    'GET /api/teams/team/projects/project': () => json({ ...project, name: 'Website' }) })
  renderApp(<App />, '/dashboard?team=team')
  await waitFor(() => expect(document.title).toMatch(/^⏸ 00:30:0\d · Website$/))
})

it('leaves the title alone when the person turned the countdown off', async () => {
  document.title = 'Original title'
  mockApi({ ...prefs({ titleTimer: false }), 'GET /api/timer/active': () => json(running(1)) })
  renderApp(<App />, '/dashboard?team=team')
  await screen.findByLabelText('Czas dzisiaj')
  await new Promise(resolve => setTimeout(resolve, 100))
  expect(document.title).toBe('Original title')
})

function stubNotification(permission: string) {
  const created = vi.fn()
  vi.stubGlobal('Notification', Object.assign(function Notification(this: unknown, title: string, options?: object) { created(title, options) }, { permission }))
  return created
}

it('shows one browser notification when the timer passes the limit and the person allowed it', async () => {
  const created = stubNotification('granted')
  mockApi({ ...prefs({ browserNotifications: true, longTimerHours: 10, titleTimer: false }), 'GET /api/timer/active': () => json(running(10.5)),
    'GET /api/teams/team/projects/project': () => json({ ...project, name: 'Website' }) })
  const first = renderApp(<App />, '/dashboard?team=team')
  await waitFor(() => expect(created).toHaveBeenCalledTimes(1))
  expect(created.mock.calls[0]![0]).toContain('ponad 10 godz.')
  expect(JSON.stringify(created.mock.calls[0]![1])).toContain('Website')
  first.unmount()
  renderApp(<App />, '/dashboard?team=team')
  await screen.findByLabelText('Czas dzisiaj')
  await new Promise(resolve => setTimeout(resolve, 100))
  expect(created).toHaveBeenCalledTimes(1)
})

it.each([
  ['the person did not turn it on', 'granted', { browserNotifications: false }, 10.5],
  ['the browser did not grant permission', 'default', { browserNotifications: true }, 10.5],
  ['the limit has not been reached', 'granted', { browserNotifications: true }, 3],
])('shows no notification when %s', async (_name, permission, overrides, hours) => {
  const created = stubNotification(permission)
  mockApi({ ...prefs({ longTimerHours: 10, titleTimer: false, ...overrides }), 'GET /api/timer/active': () => json(running(hours)) })
  renderApp(<App />, '/dashboard?team=team')
  await screen.findByLabelText('Czas dzisiaj')
  await new Promise(resolve => setTimeout(resolve, 100))
  expect(created).not.toHaveBeenCalled()
  void team
})
