import { expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { json, mockApi, team, user } from '../support/api'

const stored = { defaultTeamId: null, startPage: 'DASHBOARD', timeFormat: 'CLOCK', weekStart: 'MONDAY', longTimerEmail: true,
  browserNotifications: false, longTimerHours: 10, titleTimer: true }
const prefs = (overrides = {}) => ({ 'GET /api/account/preferences': () => json({ ...stored, ...overrides }) })
const other = { ...team, id: 'other', name: 'Other team' }

it('saves the chosen settings in one request', async () => {
  let saved: Record<string, unknown> = {}
  mockApi({
    ...prefs(),
    'GET /api/teams': () => json([team, other]),
    'PUT /api/account/preferences': (_url, options) => { saved = JSON.parse(String(options.body)); return json(saved) },
  })
  renderApp(<App />, '/account')
  const section = within(await screen.findByRole('region', { name: 'Preferencje' }))
  expect(section.getByRole('button', { name: 'Zapisz ustawienia' })).toBeDisabled()
  await userEvent.selectOptions(await section.findByLabelText('Domyślny zespół'), 'other')
  await userEvent.selectOptions(section.getByLabelText('Strona startowa po zalogowaniu'), 'TIME')
  await userEvent.selectOptions(section.getByLabelText('Zapis czasu'), 'DECIMAL')
  await userEvent.selectOptions(section.getByLabelText('Pierwszy dzień tygodnia'), 'SUNDAY')
  await userEvent.selectOptions(section.getByLabelText('Przypomnij, gdy timer działa dłużej niż'), '6')
  await userEvent.click(section.getByLabelText('Wyślij przypomnienie e-mailem'))
  await userEvent.click(section.getByLabelText('Pokazuj czas działającego timera w tytule karty przeglądarki'))
  await userEvent.click(section.getByRole('button', { name: 'Zapisz ustawienia' }))
  expect(await section.findByRole('status')).toHaveTextContent('Ustawienia zostały zapisane.')
  expect(saved).toEqual({ defaultTeamId: 'other', startPage: 'TIME', timeFormat: 'DECIMAL', weekStart: 'SUNDAY', longTimerEmail: false,
    browserNotifications: false, longTimerHours: 6, titleTimer: false })
})

it('explains a rejected save and keeps what was chosen', async () => {
  mockApi({ ...prefs(), 'PUT /api/account/preferences': () => json({}, 400) })
  renderApp(<App />, '/account')
  const section = within(await screen.findByRole('region', { name: 'Preferencje' }))
  await userEvent.selectOptions(await section.findByLabelText('Strona startowa po zalogowaniu'), 'PROJECTS')
  await userEvent.click(section.getByRole('button', { name: 'Zapisz ustawienia' }))
  expect(await section.findByRole('alert')).toHaveTextContent('Sprawdź wybrane ustawienia')
  expect(section.getByLabelText('Strona startowa po zalogowaniu')).toHaveValue('PROJECTS')
})

it.each([
  ['granted', 'granted', true, ''],
  ['denied', 'denied', false, 'nie zezwoliła'],
])('asks the browser before turning notifications on (%s)', async (_name, permission, checked, hint) => {
  vi.stubGlobal('Notification', Object.assign(vi.fn(), { permission: 'default', requestPermission: vi.fn(async () => permission) }))
  mockApi(prefs())
  renderApp(<App />, '/account')
  const section = within(await screen.findByRole('region', { name: 'Preferencje' }))
  const box = await section.findByLabelText('Pokaż powiadomienie w przeglądarce, gdy aplikacja jest otwarta')
  await userEvent.click(box)
  await waitFor(() => expect((box as HTMLInputElement).checked).toBe(checked))
  if (hint) expect(await section.findByText(new RegExp(hint))).toBeVisible()
})

it('writes saved time in decimal hours but keeps the running timer in clock form', async () => {
  mockApi({
    ...prefs({ timeFormat: 'DECIMAL' }),
    'GET /api/timer/active': () => json({ id: 't', userId: user.id, teamId: team.id, projectId: 'project', entryType: 'TIMER', workDate: '2026-10-19',
      startedAt: new Date(Date.now() - 3_661_000).toISOString(), endedAt: null, durationSeconds: null, description: null, createdAt: '', updatedAt: new Date().toISOString(),
      pausedAt: null, pausedDurationSeconds: 0 }),
  })
  renderApp(<App />, '/dashboard?team=team')
  expect(await screen.findByLabelText('Czas dzisiaj')).toHaveTextContent('0,25 h')
  expect(await screen.findByRole('timer')).toHaveTextContent(/^01:01:0\d$/)
})

it('opens the start page the person chose after signing in', async () => {
  mockApi(prefs({ startPage: 'TIME' }))
  renderApp(<App />, '/login')
  expect(await screen.findByRole('heading', { name: 'Rejestr czasu' })).toBeVisible()
})

it('keeps a return address instead of the start page', async () => {
  mockApi(prefs({ startPage: 'TIME' }))
  renderApp(<App />, '/login?returnTo=%2Fprojects')
  expect(await screen.findByRole('heading', { name: 'Projekty' })).toBeVisible()
})

it('opens the default team when the address names none', async () => {
  mockApi({ ...prefs({ defaultTeamId: 'other' }), 'GET /api/teams': () => json([team, other]) })
  renderApp(<App />, '/time')
  expect(await screen.findByLabelText('Zespół', { exact: true })).toHaveValue('other')
})

it('asks for the week the person starts on Sunday', async () => {
  const fetcher = mockApi(prefs({ weekStart: 'SUNDAY' }))
  renderApp(<App />, '/dashboard?team=team')
  await screen.findByLabelText('Czas dzisiaj')
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/dashboard/summary') && String(url).includes('weekStart=SUNDAY'))).toBe(true)
})

it('still works when the settings cannot be loaded', async () => {
  mockApi({ 'GET /api/account/preferences': () => json({}, 500) })
  renderApp(<App />, '/dashboard?team=team')
  expect(await screen.findByLabelText('Czas dzisiaj')).toHaveTextContent('00:15:00')
})

it('sends rememberMe only when the box is ticked', async () => {
  const bodies: string[] = []
  mockApi({ 'GET /api/me': () => json(user, 401), 'POST /api/auth/login': (_url, options) => { bodies.push(String(options.body)); return json(null, 401) } })
  renderApp(<App />, '/login')
  await screen.findByRole('heading', { name: 'Zaloguj się' })
  await userEvent.type(screen.getByLabelText('Adres e-mail'), user.email)
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), 'secret')
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
  await screen.findByRole('alert')
  await userEvent.click(screen.getByLabelText('Zapamiętaj mnie na tym urządzeniu (30 dni)'))
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
  await waitFor(() => expect(bodies).toHaveLength(2))
  expect(bodies[0]).not.toContain('rememberMe')
  expect(bodies[1]).toContain('rememberMe=true')
})
