import { it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json } from '../support/api'

async function fillRegistration(password = 'Valid-password-2026!', confirmation = password) {
  await screen.findByRole('heading', { name: 'Utwórz konto' })
  await userEvent.type(screen.getByLabelText('Nazwa wyświetlana'), 'New User')
  await userEvent.type(screen.getByLabelText('Adres e-mail'), 'new@example.test')
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), password)
  await userEvent.type(screen.getByLabelText('Powtórz hasło'), confirmation)
}

it('registers and returns to login with a success message and the invitation preserved', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/register': () => json(null, 201) })
  renderApp(<App />, '/login?returnTo=%2Finvite%2Fabc_DEF-123')
  await userEvent.click(await screen.findByRole('link', { name: 'Utwórz konto' }))
  await fillRegistration()
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
  expect(screen.getByRole('status')).toHaveTextContent('Konto zostało utworzone')
  expect(screen.getByLabelText('Adres e-mail')).toHaveValue('new@example.test')
  expect(screen.getByLabelText('Hasło', { exact: true })).toHaveValue('')
  expect(screen.getByRole('link', { name: 'Utwórz konto' })).toHaveAttribute('href', '/register?returnTo=%2Finvite%2Fabc_DEF-123')
})

it('does not submit mismatching passwords', async () => {
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401) })
  renderApp(<App />, '/register')
  await fillRegistration('Valid-password-2026!', 'Different-password!')
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Hasła muszą być identyczne')
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
})

it('reports a duplicate address and keeps a safe link back to login', async () => {
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/register': () => json({}, 409) })
  renderApp(<App />, '/register?returnTo=https%3A%2F%2Fevil.test')
  await fillRegistration()
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Ten adres e-mail jest już zarejestrowany')
  expect(screen.getByRole('link', { name: 'Zaloguj się' })).toHaveAttribute('href', '/login?returnTo=%2Fdashboard')
  await waitFor(() => expect(fetcher.mock.calls.filter(([path]) => String(path) === '/api/auth/register')).toHaveLength(1))
})

it('disables repeated submissions and never sends the confirmation password', async () => {
  let complete!: (response: Response) => void
  const pending = new Promise<Response>(resolve => { complete = resolve })
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/register': () => pending })
  renderApp(<App />, '/register')
  await fillRegistration()
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('button', { name: 'Tworzenie konta…' })).toBeDisabled()
  expect(screen.getByLabelText('Adres e-mail')).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: 'Tworzenie konta…' }))
  const calls = fetcher.mock.calls.filter(([path]) => String(path) === '/api/auth/register')
  expect(calls).toHaveLength(1)
  expect(JSON.parse(String(calls[0]![1]?.body))).not.toHaveProperty('passwordConfirmation')
  complete(json(null, 201))
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
})

it('does not show registration to an already signed-in user', async () => {
  mockApi()
  renderApp(<App />, '/register?returnTo=%2Fteams')
  expect(await screen.findByRole('heading', { name: 'Zespoły' })).toBeVisible()
  expect(screen.queryByLabelText('Nazwa wyświetlana')).not.toBeInTheDocument()
})
