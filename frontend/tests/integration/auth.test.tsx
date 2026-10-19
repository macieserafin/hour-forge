import { it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'

it('shows a recoverable session loading error', async () => {
  let broken = true
  mockApi({ 'GET /api/me': () => broken ? json({}, 500) : json(user) })
  renderApp(<App />)
  expect(await screen.findByText('Nie udało się wczytać konta')).toBeVisible()
  broken = false
  await userEvent.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }))
  expect(await screen.findByRole('heading', { name: 'Cześć, Test.' })).toBeVisible()
})
it('rejects bad credentials and allows a later successful login', async () => {
  let authenticated = false
  let correct = false
  mockApi({ 'GET /api/me': () => json(user, authenticated ? 200 : 401),
    'POST /api/auth/login': () => { authenticated = correct; return json(null, correct ? 204 : 401) } })
  renderApp(<App />, '/time?team=team')
  await screen.findByRole('heading', { name: 'Zaloguj się' })
  await userEvent.type(screen.getByLabelText('Adres e-mail'), user.email)
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), 'secret')
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Nieprawidłowy adres e-mail lub hasło.')
  correct = true
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
  expect(await screen.findByRole('heading', { name: 'Rejestr czasu' })).toBeVisible()
})
it('clears private query data on logout', async () => {
  mockApi({ 'POST /api/auth/logout': () => json(null, 204) })
  const { client } = renderApp(<App />)
  await screen.findByRole('heading', { name: 'Cześć, Test.' })
  client.setQueryData(['time-entries', user.id], { private: true })
  await userEvent.click(screen.getAllByRole('button', { name: 'Wyloguj się' })[0]!)
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
  expect(client.getQueryData(['time-entries', user.id])).toBeUndefined()
})
