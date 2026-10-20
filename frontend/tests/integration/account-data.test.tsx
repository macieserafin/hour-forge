import { it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'

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
