import { it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user, timer } from '../support/api'

let clicked: HTMLAnchorElement[] = []

beforeEach(() => {
  clicked = []
  URL.createObjectURL = vi.fn(() => 'blob:export')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { clicked.push(this) })
})

afterEach(() => vi.restoreAllMocks())

it('downloads a copy of the account data as a file', async () => {
  mockApi({ 'GET /api/account/export': () => new Response('{"profile":{}}', { status: 200,
    headers: { 'Content-Type': 'application/json', 'Content-Disposition': 'attachment; filename="hourforge-export-2026-10-20.json"' } }) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz moje dane' }))
  await vi.waitFor(() => expect(clicked).toHaveLength(1))
  expect(clicked[0]!.download).toBe('hourforge-export-2026-10-20.json')
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export')
})

it('explains when the data export is rate limited', async () => {
  mockApi({ 'GET /api/account/export': () => new Response(null, { status: 429, headers: { 'Retry-After': '1800' } }) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Pobierz moje dane' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Zbyt wiele prób. Spróbuj ponownie za 30 min.')
  expect(clicked).toHaveLength(0)
})

it('deletes the account only after the password is confirmed and returns to sign-in', async () => {
  let body: unknown
  let deleted = false
  mockApi({ 'GET /api/me': () => deleted ? json(null, 401) : json(user),
    'POST /api/account/delete': (_url, options) => { body = JSON.parse(String(options.body)); deleted = true; return json(null, 204) } })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  expect(dialog.getByRole('button', { name: 'Usuń konto na stałe' })).toBeDisabled()
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await screen.findByRole('heading', { name: /Zaloguj/i })).toBeVisible()
  expect(body).toEqual({ currentPassword: 'Current-password-2026' })
})

it('tells an owner to hand over or delete the teams first and keeps the account', async () => {
  mockApi({ 'POST /api/account/delete': () => json({}, 409) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('przekaż własność lub usuń zespoły')
  expect(dialog.getByRole('link', { name: 'Przejdź do zespołów' })).toHaveAttribute('href', '/teams')
})

it('explains a wrong password when deleting', async () => {
  mockApi({ 'POST /api/account/delete': () => json({}, 400) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'wrong-password-value')
  await userEvent.click(dialog.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Obecne hasło jest nieprawidłowe.')
})

it('warns by name that a running timer is lost without saving its time', async () => {
  mockApi({ 'GET /api/timer/active': () => json(timer) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  expect(await dialog.findByText(/Masz uruchomiony timer.*nie zostanie zapisany/)).toBeVisible()
})

it('does not claim a running timer when there is none', async () => {
  mockApi({ 'GET /api/timer/active': () => json(null) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  expect(within(screen.getByRole('dialog')).queryByText(/Masz uruchomiony timer/)).not.toBeInTheDocument()
})

it('removes what the browser keeps for the deleted account but not for other people', async () => {
  window.localStorage.setItem(`hourforge:timer-draft:${user.id}:entry`, 'Secret draft of the work')
  window.localStorage.setItem(`hourforge:long-timer-dismissed:${user.id}:entry`, '1')
  window.localStorage.setItem(`hourforge:last-project:${user.id}:team`, 'project')
  window.localStorage.setItem(`hourforge:whats-new-seen:${user.id}`, 'r1')
  window.localStorage.setItem('hourforge:timer-draft:somebody-else:entry', 'Not mine')
  let deleted = false
  mockApi({ 'GET /api/me': () => deleted ? json(null, 401) : json(user),
    'POST /api/account/delete': () => { deleted = true; return json(null, 204) } })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await screen.findByRole('heading', { name: /Zaloguj/i })).toBeVisible()
  const left = Object.keys(window.localStorage)
  expect(left.filter(key => key.includes(`:${user.id}`))).toEqual([])
  expect(left).toContain('hourforge:timer-draft:somebody-else:entry')
})

it('sends a deletion link to an account without a password and keeps it signed in until the link is used', async () => {
  let body: unknown
  const signedIn = { ...user, hasPassword: false, google: { linked: true, email: 'actor@gmail.test', linkedAt: '2026-10-19T10:00:00Z' } }
  mockApi({ 'GET /api/me': () => json(signedIn),
    'POST /api/account/delete': (_url, options) => { body = JSON.parse(String(options.body)); return json(null, 202) } })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  expect(dialog.queryByLabelText('Obecne hasło')).not.toBeInTheDocument()
  expect(dialog.getByText(/wyślemy link potwierdzający na adres/)).toHaveTextContent(user.email)
  await userEvent.click(dialog.getByRole('button', { name: 'Wyślij link potwierdzający' }))
  expect(await dialog.findByRole('status')).toHaveTextContent('Wysłaliśmy link potwierdzający.')
  expect(body).toEqual({ confirmByEmail: true })
  expect(screen.queryByRole('heading', { name: /Zaloguj/i })).not.toBeInTheDocument()
  expect(dialog.queryByRole('button', { name: 'Wyślij link potwierdzający' })).not.toBeInTheDocument()
  expect(dialog.getByRole('button', { name: 'Zamknij' })).toHaveFocus()
})

it('tells an owner without a password to hand over the teams before a deletion link is sent', async () => {
  mockApi({ 'GET /api/me': () => json({ ...user, hasPassword: false }), 'POST /api/account/delete': () => json({}, 409) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.click(dialog.getByRole('button', { name: 'Wyślij link potwierdzający' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('przekaż własność lub usuń zespoły')
})

it('says when the server cannot send the deletion link', async () => {
  mockApi({ 'GET /api/me': () => json({ ...user, hasPassword: false }), 'POST /api/account/delete': () => json({}, 503) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.click(dialog.getByRole('button', { name: 'Wyślij link potwierdzający' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Wysyłanie e-maili nie jest skonfigurowane na tym serwerze.')
})

it('explains a missing password when deleting while the account still looks like it has one', async () => {
  mockApi({ 'POST /api/account/delete': () => json({ code: 'PASSWORD_NOT_SET' }, 400) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń konto' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Anything-typed-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Twoje konto nie ma jeszcze hasła. Użyj przycisku „Ustaw hasło”')
  expect(screen.queryByRole('heading', { name: /Zaloguj/i })).not.toBeInTheDocument()
})
