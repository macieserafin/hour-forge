import { it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json } from '../support/api'

it('links to the password reset from the sign-in page', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401) })
  renderApp(<App />, '/login')
  expect(await screen.findByRole('link', { name: 'Nie pamiętasz hasła?' })).toHaveAttribute('href', '/forgot-password')
})

it('asks for a reset link and gives the same answer for every address', async () => {
  let body: unknown
  mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/auth/password-reset/request': (_url, options) => { body = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, '/forgot-password')
  await userEvent.type(await screen.findByLabelText('Adres e-mail'), ' ktos@firma.pl ')
  await userEvent.click(screen.getByRole('button', { name: 'Wyślij link' }))
  expect(await screen.findByText(/Jeśli konto o takim adresie istnieje/)).toBeVisible()
  expect(body).toEqual({ email: 'ktos@firma.pl' })
})

it('sets a new password from the e-mail link after checking it locally', async () => {
  let body: unknown
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/auth/password-reset/confirm': (_url, options) => { body = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, '/reset-password/link-token')
  await userEvent.type(await screen.findByLabelText('Nowe hasło'), 'short')
  await userEvent.type(screen.getByLabelText('Powtórz nowe hasło'), 'short')
  await userEvent.click(screen.getByRole('button', { name: 'Ustaw hasło' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('co najmniej 12 znaków')
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('password-reset/confirm'))).toBe(false)

  await userEvent.clear(screen.getByLabelText('Nowe hasło'))
  await userEvent.clear(screen.getByLabelText('Powtórz nowe hasło'))
  await userEvent.type(screen.getByLabelText('Nowe hasło'), 'Brand-new-password-2026')
  await userEvent.type(screen.getByLabelText('Powtórz nowe hasło'), 'Brand-new-password-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Ustaw hasło' }))
  expect(await screen.findByText(/Hasło zostało zmienione/)).toBeVisible()
  expect(body).toEqual({ token: 'link-token', newPassword: 'Brand-new-password-2026' })
})

it('explains an expired reset link and offers a new one', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/password-reset/confirm': () => json({}, 400) })
  renderApp(<App />, '/reset-password/old')
  await userEvent.type(await screen.findByLabelText('Nowe hasło'), 'Brand-new-password-2026')
  await userEvent.type(screen.getByLabelText('Powtórz nowe hasło'), 'Brand-new-password-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Ustaw hasło' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Link jest nieprawidłowy albo wygasł')
  expect(screen.getByRole('link', { name: 'Poproś o nowy link' })).toHaveAttribute('href', '/forgot-password')
})

it('confirms an address change exactly once and sends the user to sign in', async () => {
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/email-change/confirm': () => json(null, 204) })
  renderApp(<App />, '/confirm-email-change/change-token')
  expect(await screen.findByText(/Adres e-mail został zmieniony/)).toBeVisible()
  expect(fetcher.mock.calls.filter(([url]) => String(url).includes('email-change/confirm'))).toHaveLength(1)
})

it('reports an address that has been taken in the meantime', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/email-change/confirm': () => json({}, 409) })
  renderApp(<App />, '/confirm-email-change/late')
  expect(await screen.findByRole('alert')).toHaveTextContent('już używany')
})

it('requests an address change with the current password and shows a neutral confirmation', async () => {
  let body: unknown
  mockApi({ 'POST /api/account/email': (_url, options) => { body = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, '/account')
  await userEvent.type(await screen.findByLabelText('Nowy adres e-mail'), ' nowy@firma.pl ')
  await userEvent.click(screen.getAllByRole('button', { name: 'Wyślij link' })[0]!)
  expect(await screen.findByRole('alert')).toHaveTextContent('Podaj obecne hasło.')
  await userEvent.type(screen.getByLabelText('Hasło do potwierdzenia'), 'Current-password-2026')
  await userEvent.click(screen.getAllByRole('button', { name: 'Wyślij link' })[0]!)
  expect(await screen.findByText(/Jeśli adres jest dostępny/)).toBeVisible()
  expect(body).toEqual({ newEmail: 'nowy@firma.pl', currentPassword: 'Current-password-2026' })
})
