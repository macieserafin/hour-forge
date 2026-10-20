import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'

const fill = async (label: string, value: string) => userEvent.type(screen.getByLabelText(label), value)

it('renames the account and shows the new name in the session without a reload', async () => {
  let body: Record<string, unknown> | undefined
  let name = user.displayName
  mockApi({ 'GET /api/me': () => json({ ...user, displayName: name }),
    'PATCH /api/account/profile': (_url, options) => { body = JSON.parse(String(options.body)) as Record<string, unknown>; name = String(body.displayName); return json({ ...user, displayName: name }) } })
  renderApp(<App />, '/account')
  const field = await screen.findByLabelText('Nazwa wyświetlana')
  expect(screen.getByRole('button', { name: 'Zapisz nazwę' })).toBeDisabled()
  await userEvent.clear(field)
  await userEvent.type(field, '  Nowa Nazwa  ')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz nazwę' }))
  expect(await screen.findByText('Nazwa wyświetlana została zapisana.')).toBeVisible()
  expect(body).toEqual({ displayName: 'Nowa Nazwa' })
  expect(screen.getAllByText('Nowa Nazwa').length).toBeGreaterThan(0)
})

it('rejects an empty display name without calling the server', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/account')
  const field = await screen.findByLabelText('Nazwa wyświetlana')
  await userEvent.clear(field)
  await userEvent.type(field, '   ')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz nazwę' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Wpisz nazwę wyświetlaną')
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/account/profile'))).toBe(false)
})

it('validates a password change locally and never posts an invalid form', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/account')
  await screen.findByLabelText('Obecne hasło')
  const submit = () => userEvent.click(screen.getByRole('button', { name: 'Zmień hasło' }))
  await submit()
  expect(await screen.findByRole('alert')).toHaveTextContent('Podaj obecne hasło.')
  await fill('Obecne hasło', 'Current-password-2026')
  await fill('Nowe hasło', 'short')
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('co najmniej 12 znaków')
  await userEvent.clear(screen.getByLabelText('Nowe hasło'))
  await fill('Nowe hasło', 'Current-password-2026')
  await fill('Powtórz nowe hasło', 'Current-password-2026')
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('różnić się od obecnego')
  await userEvent.clear(screen.getByLabelText('Nowe hasło'))
  await fill('Nowe hasło', 'Brand-new-password-2026')
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('identyczne')
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/account/password'))).toBe(false)
})

it('changes the password, clears the form and confirms', async () => {
  let body: Record<string, unknown> | undefined
  mockApi({ 'POST /api/account/password': (_url, options) => { body = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, '/account')
  await screen.findByLabelText('Obecne hasło')
  await fill('Obecne hasło', 'Current-password-2026')
  await fill('Nowe hasło', 'Brand-new-password-2026')
  await fill('Powtórz nowe hasło', 'Brand-new-password-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Zmień hasło' }))
  expect(await screen.findByText(/Hasło zostało zmienione/)).toBeVisible()
  expect(body).toEqual({ currentPassword: 'Current-password-2026', newPassword: 'Brand-new-password-2026' })
  for (const label of ['Obecne hasło', 'Nowe hasło', 'Powtórz nowe hasło']) expect(screen.getByLabelText(label)).toHaveValue('')
})

it('explains a wrong current password and a temporary block', async () => {
  let status = 400
  mockApi({ 'POST /api/account/password': () => status === 429
    ? new Response(null, { status, headers: { 'Retry-After': '600' } }) : json({}, status) })
  renderApp(<App />, '/account')
  await screen.findByLabelText('Obecne hasło')
  await fill('Obecne hasło', 'wrong-password-value')
  await fill('Nowe hasło', 'Brand-new-password-2026')
  await fill('Powtórz nowe hasło', 'Brand-new-password-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Zmień hasło' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Obecne hasło jest nieprawidłowe.')
  status = 429
  await userEvent.click(screen.getByRole('button', { name: 'Zmień hasło' }))
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Zbyt wiele prób. Spróbuj ponownie za 10 min.'))
})

it('signs out everywhere only after confirmation and returns to the login page', async () => {
  let signedOut = false
  mockApi({ 'GET /api/me': () => signedOut ? json(null, 401) : json(user),
    'POST /api/account/sign-out-everywhere': () => { signedOut = true; return json(null, 204) } })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Wyloguj ze wszystkich urządzeń' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Anuluj' }))
  expect(signedOut).toBe(false)
  await userEvent.click(screen.getByRole('button', { name: 'Wyloguj ze wszystkich urządzeń' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Wyloguj wszędzie' }))
  expect(await screen.findByRole('heading', { name: /Zaloguj/i })).toBeVisible()
  expect(signedOut).toBe(true)
})

it('keeps the dialog open with an error when signing out everywhere fails', async () => {
  mockApi({ 'POST /api/account/sign-out-everywhere': () => json({}, 500) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Wyloguj ze wszystkich urządzeń' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Wyloguj wszędzie' }))
  expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('Nie udało się wylogować ze wszystkich urządzeń')
})
