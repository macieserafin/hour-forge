import { it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'

const path = '/confirm-account-deletion/abc-123_token'
const confirmCalls = (fetcher: ReturnType<typeof mockApi>) => fetcher.mock.calls.filter(([url]) => String(url).includes('/api/account/delete/confirm'))
const deleteButton = () => screen.findByRole('button', { name: 'Usuń konto na stałe' })

it('removes nothing when the link is only opened, as mail scanners do', async () => {
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401) })
  renderApp(<App />, path)
  expect(await screen.findByRole('heading', { name: 'Usunięcie konta' })).toBeVisible()
  expect(await deleteButton()).toBeEnabled()
  expect(screen.getByText(/Tej operacji nie można cofnąć/)).toBeVisible()
  await new Promise(resolve => setTimeout(resolve, 50))
  expect(confirmCalls(fetcher)).toHaveLength(0)
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/auth/csrf'))).toBe(false)
})

it('deletes the account only after the button, with a fresh form token, and says so', async () => {
  let body: unknown
  let csrfHeader: string | null = null
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/account/delete/confirm': (_url, options) => {
      body = JSON.parse(String(options.body)); csrfHeader = new Headers(options.headers).get('X-CSRF-TOKEN'); return json(null, 204) } })
  renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  const status = await screen.findByRole('status')
  expect(status).toHaveTextContent('Konto zostało usunięte.')
  expect(status).toHaveFocus()
  expect(body).toEqual({ token: 'abc-123_token' })
  expect(csrfHeader).toBe('csrf')
  const keys = fetcher.mock.calls.map(([url, options]) => `${(options as RequestInit | undefined)?.method ?? 'GET'} ${new URL(String(url), window.location.origin).pathname}`)
  expect(keys.indexOf('GET /api/auth/csrf')).toBeLessThan(keys.indexOf('POST /api/account/delete/confirm'))
  expect(confirmCalls(fetcher)).toHaveLength(1)
  expect(screen.queryByRole('button', { name: 'Usuń konto na stałe' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Przejdź do logowania' })).toHaveAttribute('href', '/login')
})

it('signs out and clears the browser data of the deleted account when it was the one signed in', async () => {
  window.localStorage.setItem(`hourforge:timer-draft:${user.id}:entry`, 'Secret draft of the work')
  window.localStorage.setItem('hourforge:timer-draft:somebody-else:entry', 'Not mine')
  let deleted = false
  mockApi({ 'GET /api/me': () => deleted ? json(null, 401) : json(user),
    'POST /api/account/delete/confirm': () => { deleted = true; return json(null, 204) } })
  const { client } = renderApp(<App />, path)
  client.setQueryData(['time-entries', user.id], { private: true })
  await userEvent.click(await deleteButton())
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte.')
  await waitFor(() => expect(client.getQueryData(['session'])).toBeNull())
  expect(client.getQueryData(['time-entries', user.id])).toBeUndefined()
  expect(Object.keys(window.localStorage).filter(key => key.includes(`:${user.id}`))).toEqual([])
  expect(window.localStorage.getItem('hourforge:timer-draft:somebody-else:entry')).toBe('Not mine')
})

it('keeps the session and data of somebody else who opened the link while signed in', async () => {
  window.localStorage.setItem(`hourforge:timer-draft:${user.id}:entry`, 'My draft')
  mockApi({ 'POST /api/account/delete/confirm': () => json(null, 204) })
  const { client } = renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte. Wszystkie urządzenia tego konta zostały wylogowane.')
  expect(client.getQueryData(['session'])).toEqual(user)
  expect(window.localStorage.getItem(`hourforge:timer-draft:${user.id}:entry`)).toBe('My draft')
  expect(screen.getByText(`Ta przeglądarka pozostaje zalogowana na konto ${user.email}, bo link dotyczył innego konta.`)).toBeVisible()
})

it('tells a signed-in visitor that the link means the account the e-mail went to', async () => {
  mockApi()
  renderApp(<App />, path)
  await deleteButton()
  expect(screen.getByText(`Ta przeglądarka jest zalogowana na konto ${user.email}. Link usuwa konto, na które wysłaliśmy wiadomość z tym linkiem.`)).toBeVisible()
})

it('does not read the session after deleting when nobody was signed in, so a failing server cannot hide the result', async () => {
  let deleted = false
  const fetcher = mockApi({ 'GET /api/me': () => json(null, deleted ? 500 : 401),
    'POST /api/account/delete/confirm': () => { deleted = true; return json(null, 204) } })
  renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte.')
  await new Promise(resolve => setTimeout(resolve, 50))
  const keys = fetcher.mock.calls.map(([url]) => new URL(String(url), window.location.origin).pathname)
  expect(keys.slice(keys.indexOf('/api/account/delete/confirm'))).not.toContain('/api/me')
  expect(screen.queryByText('Nie udało się wczytać konta')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Usuń konto na stałe' })).not.toBeInTheDocument()
  expect(confirmCalls(fetcher)).toHaveLength(1)
})

it('treats a failed session read after deleting the signed-in account as signed out and keeps the result', async () => {
  window.localStorage.setItem(`hourforge:timer-draft:${user.id}:entry`, 'Secret draft of the work')
  let deleted = false
  const fetcher = mockApi({ 'GET /api/me': () => deleted ? json({}, 500) : json(user),
    'POST /api/account/delete/confirm': () => { deleted = true; return json(null, 204) } })
  const { client } = renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte.')
  await waitFor(() => expect(client.getQueryData(['session'])).toBeNull())
  await new Promise(resolve => setTimeout(resolve, 50))
  expect(screen.getByRole('status')).toHaveTextContent('Konto zostało usunięte.')
  expect(screen.queryByText('Nie udało się wczytać konta')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Usuń konto na stałe' })).not.toBeInTheDocument()
  expect(Object.keys(window.localStorage).filter(key => key.includes(`:${user.id}`))).toEqual([])
  expect(confirmCalls(fetcher)).toHaveLength(1)
})

it('explains a used, expired or unknown link and points to the account settings', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/account/delete/confirm': () => json({}, 400) })
  renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('Link jest nieprawidłowy, został już użyty albo wygasł. Konto nie zostało usunięte.')
  expect(alert).toHaveTextContent('wyślij nowy link w ustawieniach konta')
  expect(alert).toHaveFocus()
  expect(screen.queryByRole('button', { name: 'Usuń konto na stałe' })).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Przejdź do ustawień konta' })).toHaveAttribute('href', '/account')
})

it('asks a team owner to hand over the teams first and lets the same link work afterwards', async () => {
  let owner = true
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/account/delete/confirm': () => json({}, owner ? 409 : 204) })
  renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('Najpierw przekaż własność lub usuń zespoły, których jesteś właścicielem. Link pozostaje ważny')
  expect(alert).toHaveFocus()
  expect(screen.getByRole('link', { name: 'Przejdź do zespołów' })).toHaveAttribute('href', '/teams')
  owner = false
  await userEvent.click(screen.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte.')
  expect(confirmCalls(fetcher)).toHaveLength(2)
})

it.each([
  ['too many attempts', () => new Response(null, { status: 429, headers: { 'Retry-After': '300' } }), 'Zbyt wiele prób. Spróbuj ponownie za 5 min.'],
  ['a lost connection', () => { throw new TypeError('Failed to fetch') }, 'Nie udało się połączyć. Sprawdź połączenie i spróbuj ponownie.'],
])('lets the person try again after %s', async (_case, failure, text) => {
  let failing = true
  mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/account/delete/confirm': () => failing ? failure() : json(null, 204) })
  renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  expect(await screen.findByRole('alert')).toHaveTextContent(text)
  failing = false
  await userEvent.click(screen.getByRole('button', { name: 'Usuń konto na stałe' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte.')
})

it('blocks a second press while the deletion is on its way', async () => {
  let finish: (response: Response) => void = () => undefined
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401),
    'POST /api/account/delete/confirm': () => new Promise<Response>(resolve => { finish = resolve }) })
  renderApp(<App />, path)
  await userEvent.click(await deleteButton())
  const busy = await screen.findByRole('button', { name: 'Usuwanie…' })
  expect(busy).toBeDisabled()
  await userEvent.click(busy)
  finish(json(null, 204))
  expect(await screen.findByRole('status')).toHaveTextContent('Konto zostało usunięte.')
  expect(confirmCalls(fetcher)).toHaveLength(1)
})
