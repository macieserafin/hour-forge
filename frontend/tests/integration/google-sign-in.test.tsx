import { it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'
import { publicConfigKey } from '../../src/lib/operator'
import type { QueryClient } from '@tanstack/react-query'

const googleOn = { 'GET /api/config': () => json({ botCheckSiteKey: null, googleSignIn: true }) }
const signedOut = { 'GET /api/me': () => json(null, 401) }
const start = '/api/auth/oauth2/authorization/google'
/** Checks for missing Google controls mean something only after the page has read the server settings. */
const configRead = (client: QueryClient) => waitFor(() => expect(client.getQueryState(publicConfigKey)?.status).toBe('success'))

it.each([
  ['the server says nothing about Google', { botCheckSiteKey: null }],
  ['Google is turned off', { botCheckSiteKey: null, googleSignIn: false }],
])('shows no Google sign-in, message or code step when %s', async (_case, config) => {
  mockApi({ ...signedOut, 'GET /api/config': () => json(config) })
  const { client } = renderApp(<App />, '/login?google=GOOGLE_ACCOUNT_EXISTS&step=two-factor')
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
  await configRead(client)
  expect(screen.getByLabelText('Hasło', { exact: true })).toBeVisible()
  expect(screen.queryByRole('link', { name: 'Zaloguj się przez Google' })).not.toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Logowanie dwuetapowe' })).not.toBeInTheDocument()
})

it('hides Google on the registration page while it is turned off', async () => {
  mockApi(signedOut)
  const { client } = renderApp(<App />, '/register')
  expect(await screen.findByRole('heading', { name: 'Utwórz konto' })).toBeVisible()
  await configRead(client)
  expect(screen.queryByRole('link', { name: 'Załóż konto przez Google' })).not.toBeInTheDocument()
})

it('starts Google sign-in with a full page link that carries "remember me" and the return address', async () => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, '/login?returnTo=%2Finvite%2Fabc_DEF-1')
  const link = await screen.findByRole('link', { name: 'Zaloguj się przez Google' })
  expect(link).toHaveAttribute('href', `${start}?mode=login&returnTo=%2Finvite%2Fabc_DEF-1`)
  await userEvent.click(screen.getByLabelText('Zapamiętaj mnie na tym urządzeniu (30 dni)'))
  expect(link).toHaveAttribute('href', `${start}?mode=login&rememberMe=true&returnTo=%2Finvite%2Fabc_DEF-1`)
})

it('never passes an outside return address to Google', async () => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, '/login?returnTo=https%3A%2F%2Fevil.test%2Fdashboard')
  expect(await screen.findByRole('link', { name: 'Zaloguj się przez Google' })).toHaveAttribute('href', `${start}?mode=login&returnTo=%2Fdashboard`)
})

it('leaves out the return address when there is none, so the server picks the start page', async () => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, '/login')
  expect(await screen.findByRole('link', { name: 'Zaloguj się przez Google' })).toHaveAttribute('href', `${start}?mode=login`)
})

it('offers sign-up with Google that returns to the invitation', async () => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, '/register?returnTo=%2Finvite%2Fabc')
  expect(await screen.findByRole('link', { name: 'Załóż konto przez Google' })).toHaveAttribute('href', `${start}?mode=login&returnTo=%2Finvite%2Fabc`)
})

it.each([
  ['GOOGLE_CANCELLED', 'Logowanie przez Google zostało anulowane.'],
  ['GOOGLE_FAILED', 'Nie udało się zalogować przez Google. Spróbuj ponownie.'],
  ['GOOGLE_DISABLED', 'Logowanie przez Google jest niedostępne na tym serwerze.'],
  ['GOOGLE_EMAIL_UNVERIFIED', 'Google nie potwierdził adresu e-mail tego konta.'],
  ['GOOGLE_ACCOUNT_EXISTS', 'Konto z tym adresem już istnieje. Zaloguj się hasłem, a potem połącz Google w ustawieniach konta.'],
  ['GOOGLE_ALREADY_LINKED', 'To konto Google jest już połączone z innym kontem HourForge.'],
  ['GOOGLE_ALREADY_LINKED_TO_YOU', 'Twoje konto jest już połączone z innym kontem Google.'],
  ['LINK_EXPIRED', 'Czas na połączenie z Google minął.'],
  ['ACCOUNT_BLOCKED', 'To konto zostało zablokowane przez operatora serwisu.'],
  ['EMAIL_NOT_VERIFIED', 'Potwierdź adres e-mail, aby się zalogować.'],
])('explains the Google answer %s on the sign-in page', async (code, text) => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, `/login?google=${code}`)
  expect(await screen.findByRole('alert')).toHaveTextContent(text)
})

it('announces a connected Google account as a status, not an error', async () => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, '/login?google=LINKED')
  expect(await screen.findByText('Konto Google zostało połączone.')).toHaveAttribute('role', 'status')
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it.each(['SOMETHING_NEW', 'constructor', 'toString', '__proto__', 'hasOwnProperty'])('ignores the unknown Google answer %s instead of guessing a message', async code => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, `/login?google=${code}`)
  await screen.findByRole('link', { name: 'Zaloguj się przez Google' })
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('hides the Google message once the password form is sent, so only the new answer shows', async () => {
  mockApi({ ...signedOut, ...googleOn, 'POST /api/auth/login': () => json(null, 401) })
  renderApp(<App />, '/login?google=GOOGLE_ACCOUNT_EXISTS')
  expect(await screen.findByRole('alert')).toHaveTextContent('Konto z tym adresem już istnieje.')
  await userEvent.type(screen.getByLabelText('Adres e-mail'), user.email)
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), 'secret')
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Nieprawidłowy adres e-mail lub hasło.')
  expect(screen.getAllByRole('alert')).toHaveLength(1)
})

it('opens the code step when Google sign-in still needs two-step verification', async () => {
  let authenticated = false
  mockApi({ ...googleOn, 'GET /api/me': () => json(user, authenticated ? 200 : 401),
    'POST /api/auth/two-factor': () => { authenticated = true; return json(null, 204) } })
  renderApp(<App />, '/login?step=two-factor')
  const field = await screen.findByLabelText('Kod z aplikacji lub kod zapasowy')
  expect(screen.getByRole('heading', { name: 'Logowanie dwuetapowe' })).toBeVisible()
  await userEvent.type(field, '123456')
  await userEvent.click(screen.getByRole('button', { name: 'Potwierdź' }))
  expect(await screen.findByRole('heading', { name: 'Cześć, Test.' })).toBeVisible()
})

it('goes back to the password from the Google code step and stays there', async () => {
  mockApi({ ...signedOut, ...googleOn })
  renderApp(<App />, '/login?step=two-factor')
  await screen.findByLabelText('Kod z aplikacji lub kod zapasowy')
  await userEvent.click(screen.getByRole('button', { name: 'Wróć do logowania' }))
  expect(await screen.findByLabelText('Hasło', { exact: true })).toBeVisible()
  expect(screen.queryByLabelText('Kod z aplikacji lub kod zapasowy')).not.toBeInTheDocument()
})

it('asks for a new sign-in when the code step from Google has expired', async () => {
  mockApi({ ...signedOut, ...googleOn, 'POST /api/auth/two-factor': () => json(null, 401) })
  renderApp(<App />, '/login?step=two-factor')
  await userEvent.type(await screen.findByLabelText('Kod z aplikacji lub kod zapasowy'), '123456')
  await userEvent.click(screen.getByRole('button', { name: 'Potwierdź' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Czas na podanie kodu minął')
  expect(screen.getByLabelText('Hasło', { exact: true })).toBeVisible()
})
