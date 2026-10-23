import { it, expect, afterEach, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json } from '../support/api'

afterEach(() => { delete window.turnstile })

async function fillRegistration() {
  await screen.findByRole('heading', { name: 'Utwórz konto' })
  await userEvent.type(screen.getByLabelText('Nazwa wyświetlana'), 'New User')
  await userEvent.type(screen.getByLabelText('Adres e-mail'), 'new@example.test')
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), 'Valid-password-2026!')
  await userEvent.type(screen.getByLabelText('Powtórz hasło'), 'Valid-password-2026!')
}

/** Stands in for Cloudflare's script: every rendered widget is solved at once with a numbered token. */
function solvingTurnstile() {
  let solved = 0
  const render = vi.fn((_element: HTMLElement, options: { sitekey: string; callback: (token: string) => void }) => {
    queueMicrotask(() => options.callback(`token-${++solved}`))
    return `widget-${solved}`
  })
  window.turnstile = { render, remove: vi.fn() }
  return render
}

it('sends the token of the widget with the registration and renews the widget after a refusal', async () => {
  const render = solvingTurnstile()
  const tokens: unknown[] = []
  let refuse = true
  mockApi({ 'GET /api/me': () => json(null, 401), 'GET /api/config': () => json({ botCheckSiteKey: 'site-key' }),
    'POST /api/auth/register': (_url, options) => {
      tokens.push((JSON.parse(String(options.body)) as { botCheckToken?: string }).botCheckToken)
      if (refuse) { refuse = false; return json({ code: 'BOT_CHECK_FAILED' }, 400) }
      return json(null, 201)
    } })
  renderApp(<App />, '/register')
  await fillRegistration()
  await waitFor(() => expect(render).toHaveBeenCalledWith(expect.any(HTMLElement), expect.objectContaining({ sitekey: 'site-key' })))

  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Nie udało się potwierdzić, że formularz wypełnia człowiek')
  await waitFor(() => expect(render).toHaveBeenCalledTimes(2))

  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
  expect(tokens).toEqual(['token-1', 'token-2'])
})

it('waits for the check before sending the form', async () => {
  window.turnstile = { render: vi.fn(() => 'widget'), remove: vi.fn() }
  const fetcher = mockApi({ 'GET /api/me': () => json(null, 401), 'GET /api/config': () => json({ botCheckSiteKey: 'site-key' }) })
  renderApp(<App />, '/register')
  await fillRegistration()
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Poczekaj, aż weryfikacja')
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/auth/register'))).toBe(false)
})

it('loads nothing from Cloudflare when the server has no keys', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/register': () => json(null, 201) })
  renderApp(<App />, '/register')
  await fillRegistration()
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz konto' }))
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
  expect(document.querySelector('script[src*="challenges.cloudflare.com"]')).toBeNull()
})
