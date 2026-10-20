import { it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json } from '../support/api'

it('confirms the address from the e-mail link exactly once', async () => {
  const bodies: unknown[] = []
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/auth/email-verification/confirm': (_url, options) => { bodies.push(JSON.parse(String(options.body))); return json(null, 204) } })
  renderApp(<App />, '/verify-email/abc-123_token')
  expect(await screen.findByText('Adres e-mail został potwierdzony.')).toBeVisible()
  expect(bodies).toEqual([{ token: 'abc-123_token' }])
  expect(screen.getByRole('link', { name: 'Przejdź do logowania' })).toHaveAttribute('href', '/login')
  expect(fetcher.mock.calls.filter(([url]) => String(url).includes('/confirm'))).toHaveLength(1)
})

it('explains an invalid or expired link', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/email-verification/confirm': () => json({}, 400) })
  renderApp(<App />, '/verify-email/old')
  expect(await screen.findByRole('alert')).toHaveTextContent('Link jest nieprawidłowy albo wygasł')
})

it('offers a new link when sign-in is blocked until the address is confirmed', async () => {
  let resent: unknown
  mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/auth/login': () => json({ code: 'EMAIL_NOT_VERIFIED' }, 403),
    'POST /api/auth/email-verification/resend': (_url, options) => { resent = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, '/login')
  await userEvent.type(await screen.findByLabelText('Adres e-mail'), 'ty@firma.pl')
  await userEvent.type(screen.getByLabelText('Hasło'), 'Current-password-2026')
  await userEvent.click(screen.getByRole('button', { name: /Zaloguj się/ }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Potwierdź adres e-mail')
  await userEvent.click(screen.getByRole('button', { name: 'Wyślij link potwierdzający ponownie' }))
  expect(await screen.findByText(/wysłaliśmy nowy link/)).toBeVisible()
  expect(resent).toEqual({ email: 'ty@firma.pl' })
})
