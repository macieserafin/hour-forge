import { it, expect, vi, afterEach } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user, deviceSession } from '../support/api'
import { googleNavigation } from '../../src/lib/navigation'
import { publicConfigKey } from '../../src/lib/operator'

afterEach(() => vi.restoreAllMocks())

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

it('lists the signed-in devices and signs out another one without touching this one', async () => {
  let devices = [deviceSession, { ...deviceSession, id: 'phone', browser: 'Safari', system: 'iOS', address: null, current: false, remembered: true }]
  let ended = ''
  mockApi({ 'GET /api/account/sessions': () => json(devices),
    'DELETE /api/account/sessions/phone': (_url, options) => {
      ended = new Headers(options.headers).get('X-CSRF-TOKEN') ?? ''
      devices = devices.filter(device => device.id !== 'phone'); return json(null, 204) } })
  renderApp(<App />, '/account')
  const list = await screen.findByRole('list', { name: 'Zalogowane urządzenia' })
  const items = within(list).getAllByRole('listitem')
  expect(items).toHaveLength(2)
  expect(items[0]).toHaveTextContent('Firefox · Windows · to urządzenie')
  expect(list).not.toHaveTextContent('83.24.x.x')
  expect(within(items[0]).queryByRole('button')).toBeNull()
  expect(items[1]).toHaveTextContent('Safari · iOS')
  expect(items[1]).toHaveTextContent('zapamiętane')

  await userEvent.click(within(items[1]).getByRole('button', { name: /^Wyloguj Safari · iOS/ }))
  expect(await screen.findByText('Urządzenie zostało wylogowane.')).toBeVisible()
  expect(ended).toBe('csrf')
  await waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(1))
})

it('names an unknown browser plainly and explains a device that is already gone', async () => {
  mockApi({ 'GET /api/account/sessions': () => json([deviceSession, { ...deviceSession, id: 'gone', browser: null, system: null, current: false }]),
    'DELETE /api/account/sessions/gone': () => json(null, 404) })
  renderApp(<App />, '/account')
  const list = await screen.findByRole('list', { name: 'Zalogowane urządzenia' })
  const unknown = within(list).getAllByRole('listitem')[1]
  expect(unknown).toHaveTextContent('Nieznana przeglądarka')
  await userEvent.click(within(unknown).getByRole('button'))
  expect(await screen.findByText('To urządzenie jest już wylogowane. Odśwież listę.')).toBeVisible()
})

it('shows the sign-in history with failed attempts, places and the credit for the location data', async () => {
  mockApi({ 'GET /api/account/logins': () => json({ locationSource: 'IP Geolocation by DB-IP (https://db-ip.com)', entries: [
    { occurredAt: '2026-10-19T10:00:00Z', successful: true, browser: 'Firefox', system: 'Windows', address: '83.24.x.x', location: 'Warsaw, Poland' },
    { occurredAt: '2026-10-19T09:58:00Z', successful: false, browser: null, system: null, address: null, location: null }] }) })
  renderApp(<App />, '/account')
  const table = await screen.findByRole('table', { name: 'Logowania od najnowszego' })
  const rows = within(table).getAllByRole('row').slice(1)
  expect(rows[0]).toHaveTextContent('Udane')
  expect(rows[0]).toHaveTextContent('Firefox · Windows')
  expect(rows[0]).toHaveTextContent('Warsaw, Poland')
  expect(table).not.toHaveTextContent('83.24.x.x')
  expect(rows[1]).toHaveTextContent('Nieudane')
  expect(rows[1]).toHaveTextContent('Nieznana przeglądarka')
  expect(screen.getByText(/Dane lokalizacji: IP Geolocation by DB-IP/)).toBeVisible()
})

it('says when no sign-ins are stored and shows no credit without places', async () => {
  mockApi()
  renderApp(<App />, '/account')
  expect(await screen.findByText('Brak zapisanych logowań.')).toBeVisible()
  expect(screen.queryByText(/Dane lokalizacji/)).toBeNull()
})

const googleOn = { 'GET /api/config': () => json({ botCheckSiteKey: null, googleSignIn: true }) }
const googleLinked = { linked: true, email: 'actor@gmail.test', linkedAt: '2026-10-19T10:00:00Z' }

it('shows nothing about Google in the account while it is turned off, even when the account is linked', async () => {
  mockApi({ 'GET /api/me': () => json({ ...user, hasPassword: true, google: googleLinked }),
    'GET /api/account/logins': () => json({ locationSource: null, entries: [
      { occurredAt: '2026-10-19T10:00:00Z', successful: true, browser: 'Firefox', system: 'Windows', address: null, location: null, method: 'GOOGLE' }] }) })
  const { client } = renderApp(<App />, '/account?google=LINKED')
  const table = await screen.findByRole('table', { name: 'Logowania od najnowszego' })
  await waitFor(() => expect(client.getQueryState(publicConfigKey)?.status).toBe('success'))
  expect(screen.queryByRole('heading', { name: 'Logowanie przez Google' })).not.toBeInTheDocument()
  expect(screen.queryByText('Konto Google zostało połączone.')).not.toBeInTheDocument()
  expect(within(table).queryByRole('columnheader', { name: 'Metoda' })).not.toBeInTheDocument()
})

it('connects Google after the password is confirmed and leaves for the address from the server', async () => {
  const go = vi.spyOn(googleNavigation, 'go').mockImplementation(() => undefined)
  let body: unknown
  mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: true, google: null }),
    'POST /api/account/google/link': (_url, options) => { body = JSON.parse(String(options.body)); return json({ authorizationUrl: '/api/auth/oauth2/authorization/google?mode=link' }) } })
  renderApp(<App />, '/account')
  const section = within(await screen.findByRole('region', { name: 'Logowanie przez Google' }))
  await userEvent.click(section.getByRole('button', { name: 'Połącz z Google' }))
  const dialog = within(screen.getByRole('dialog', { name: 'Połącz z Google' }))
  expect(dialog.getByRole('button', { name: 'Przejdź do Google' })).toBeDisabled()
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Przejdź do Google' }))
  await waitFor(() => expect(go).toHaveBeenCalledWith('/api/auth/oauth2/authorization/google?mode=link'))
  expect(body).toEqual({ currentPassword: 'Current-password-2026' })
})

it('keeps the account unconnected when the password for linking Google is wrong', async () => {
  const go = vi.spyOn(googleNavigation, 'go').mockImplementation(() => undefined)
  mockApi({ ...googleOn, 'POST /api/account/google/link': () => json({}, 400) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Połącz z Google' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'wrong-password-value')
  await userEvent.click(dialog.getByRole('button', { name: 'Przejdź do Google' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Obecne hasło jest nieprawidłowe.')
  expect(go).not.toHaveBeenCalled()
})

it('shows the connected Google account and disconnects it with the password', async () => {
  let linked = true
  let body: unknown
  mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: true, google: linked ? googleLinked : null }),
    'POST /api/account/google/unlink': (_url, options) => { body = JSON.parse(String(options.body)); linked = false; return json(null, 204) } })
  renderApp(<App />, '/account')
  const region = await screen.findByRole('region', { name: 'Logowanie przez Google' })
  const section = within(region)
  expect(region).toHaveTextContent('Połączone z kontem Google actor@gmail.test od')
  expect(section.getByText((_text, element) => element?.tagName === 'TIME')).toHaveAttribute('dateTime', googleLinked.linkedAt)
  await userEvent.click(section.getByRole('button', { name: 'Odłącz Google' }))
  const dialog = within(screen.getByRole('dialog', { name: 'Odłącz Google' }))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Odłącz' }))
  expect(await section.findByRole('status')).toHaveTextContent('Konto Google zostało odłączone.')
  expect(await section.findByRole('button', { name: 'Połącz z Google' })).toBeVisible()
  expect(body).toEqual({ currentPassword: 'Current-password-2026' })
})

it('explains that Google is the last way to sign in when the server refuses to disconnect it', async () => {
  mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: true, google: googleLinked }),
    'POST /api/account/google/unlink': () => json({ code: 'LAST_SIGN_IN_METHOD' }, 409) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Odłącz Google' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Odłącz' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Nie możesz odłączyć Google, dopóki nie ustawisz hasła.')
})

it.each([
  ['LINKED', 'status', 'Konto Google zostało połączone.'],
  ['GOOGLE_CANCELLED', 'alert', 'Łączenie z Google zostało anulowane.'],
  ['GOOGLE_ALREADY_LINKED', 'alert', 'To konto Google jest już połączone z innym kontem HourForge.'],
  ['LINK_EXPIRED', 'alert', 'Czas na połączenie z Google minął. Spróbuj ponownie.'],
])('tells the result %s after coming back from Google to the account', async (code, role, text) => {
  mockApi(googleOn)
  renderApp(<App />, `/account?google=${code}`)
  const section = within(await screen.findByRole('region', { name: 'Logowanie przez Google' }))
  expect(section.getByText(text)).toHaveAttribute('role', role)
})

it('lets an account without a password set one by e-mail instead of the password forms', async () => {
  const requests: unknown[] = []
  const fetcher = mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: false, google: googleLinked }),
    'POST /api/auth/password-reset/request': (_url, options) => { requests.push(JSON.parse(String(options.body))); return json(null, 204) } })
  renderApp(<App />, '/account')
  const passwordSection = within(await screen.findByRole('region', { name: 'Zmiana hasła' }))
  expect(screen.getAllByRole('button', { name: 'Ustaw hasło' })).toHaveLength(3)
  expect(screen.queryByLabelText('Obecne hasło')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Nowy adres e-mail')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Włącz logowanie dwuetapowe' })).not.toBeInTheDocument()
  expect(within(await screen.findByRole('region', { name: 'Logowanie przez Google' })).getByText('Nie możesz odłączyć Google, dopóki nie ustawisz hasła.')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Odłącz Google' })).not.toBeInTheDocument()
  await userEvent.click(passwordSection.getByRole('button', { name: 'Ustaw hasło' }))
  expect(await passwordSection.findByRole('status')).toHaveTextContent('Wysłaliśmy link do ustawienia hasła. Po ustawieniu hasła zaloguj się ponownie.')
  expect(requests).toEqual([{ email: user.email }])
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/api/account/two-factor'))).toBe(false)
})

it('treats an account without the password flag as one with a password', async () => {
  mockApi(googleOn)
  renderApp(<App />, '/account')
  await screen.findByRole('region', { name: 'Logowanie przez Google' })
  expect(screen.queryByRole('button', { name: 'Ustaw hasło' })).not.toBeInTheDocument()
  expect(screen.getByLabelText('Obecne hasło')).toBeVisible()
})

it('shows how each sign-in happened when the server reports it', async () => {
  mockApi({ ...googleOn, 'GET /api/account/logins': () => json({ locationSource: null, entries: [
    { occurredAt: '2026-10-19T10:00:00Z', successful: true, browser: 'Firefox', system: 'Windows', address: null, location: null, method: 'GOOGLE' },
    { occurredAt: '2026-10-19T09:58:00Z', successful: false, browser: null, system: null, address: null, location: null, method: 'PASSWORD' }] }) })
  renderApp(<App />, '/account')
  const table = await screen.findByRole('table', { name: 'Logowania od najnowszego' })
  expect(within(table).getByRole('columnheader', { name: 'Metoda' })).toBeVisible()
  const rows = within(table).getAllByRole('row').slice(1)
  expect(rows[0]).toHaveTextContent('Google')
  expect(rows[1]).toHaveTextContent('Hasło')
})

it('keeps the sign-in history without a method column when the server does not report it', async () => {
  mockApi({ ...googleOn, 'GET /api/account/logins': () => json({ locationSource: null, entries: [
    { occurredAt: '2026-10-19T10:00:00Z', successful: true, browser: 'Firefox', system: 'Windows', address: null, location: null }] }) })
  renderApp(<App />, '/account')
  const table = await screen.findByRole('table', { name: 'Logowania od najnowszego' })
  expect(within(table).queryByRole('columnheader', { name: 'Metoda' })).not.toBeInTheDocument()
})

it.each([
  ['link', 'Połącz z Google', 'Przejdź do Google', 'POST /api/account/google/link', null],
  ['unlink', 'Odłącz Google', 'Odłącz', 'POST /api/account/google/unlink', googleLinked],
])('says when to try again after too many attempts to %s Google', async (_case, open, confirm, endpoint, google) => {
  mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: true, google }),
    [endpoint]: () => new Response(null, { status: 429, headers: { 'Retry-After': '120' } }) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: open }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: confirm }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Zbyt wiele prób. Spróbuj ponownie za 2 min.')
})

it('explains that Google was already disconnected elsewhere', async () => {
  mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: true, google: googleLinked }),
    'POST /api/account/google/unlink': () => json({}, 404) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Odłącz Google' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Odłącz' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Konto nie jest już połączone z Google. Odśwież stronę.')
})

it('connects Google for an account without a password without asking for one', async () => {
  const go = vi.spyOn(googleNavigation, 'go').mockImplementation(() => undefined)
  let body: unknown
  mockApi({ ...googleOn, 'GET /api/me': () => json({ ...user, hasPassword: false, google: null }),
    'POST /api/account/google/link': (_url, options) => { body = JSON.parse(String(options.body)); return json({ authorizationUrl: '/api/auth/oauth2/authorization/google?mode=link' }) } })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Połącz z Google' }))
  await waitFor(() => expect(go).toHaveBeenCalledWith('/api/auth/oauth2/authorization/google?mode=link'))
  expect(body).toEqual({})
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('stays in the app when the server answers with an outside address for Google', async () => {
  const assign = vi.spyOn(googleNavigation, 'assign').mockImplementation(() => undefined)
  mockApi({ ...googleOn, 'POST /api/account/google/link': () => json({ authorizationUrl: 'https://evil.test/api/auth/oauth2/authorization/google?mode=link' }) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Połącz z Google' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Przejdź do Google' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Nieprawidłowy adres logowania przez Google.')
  expect(assign).not.toHaveBeenCalled()
})

it('shows "Ustaw hasło" when the server says the account has no password although the page thought it had one', async () => {
  let refreshed = false
  mockApi({ 'GET /api/me': () => json({ ...user, hasPassword: !refreshed }),
    'POST /api/account/password': () => { refreshed = true; return json({ code: 'PASSWORD_NOT_SET' }, 400) } })
  renderApp(<App />, '/account')
  await screen.findByLabelText('Obecne hasło')
  await fill('Obecne hasło', 'Anything-typed-2026')
  await fill('Nowe hasło', 'Brand-new-password-2026')
  await fill('Powtórz nowe hasło', 'Brand-new-password-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Zmień hasło' }))
  await waitFor(() => expect(screen.getAllByRole('button', { name: 'Ustaw hasło' })).toHaveLength(3))
  expect(screen.queryByLabelText('Obecne hasło')).not.toBeInTheDocument()
})

it('explains a missing password on the e-mail change while the account still looks like it has one', async () => {
  mockApi({ 'POST /api/account/email': () => json({ code: 'PASSWORD_NOT_SET' }, 400) })
  renderApp(<App />, '/account')
  await screen.findByLabelText('Nowy adres e-mail')
  await fill('Nowy adres e-mail', 'new@example.test')
  await fill('Hasło do potwierdzenia', 'Anything-typed-2026')
  await userEvent.click(screen.getByRole('button', { name: 'Wyślij link' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Twoje konto nie ma jeszcze hasła. Użyj przycisku „Ustaw hasło”')
})

it('explains a missing password when turning off two-step sign-in', async () => {
  mockApi({ 'GET /api/account/two-factor': () => json({ available: true, enabled: true, backupCodesLeft: 8 }),
    'POST /api/account/two-factor/disable': () => json({ code: 'PASSWORD_NOT_SET' }, 400) })
  renderApp(<App />, '/account')
  await userEvent.click(await screen.findByRole('button', { name: 'Wyłącz logowanie dwuetapowe' }))
  const dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Obecne hasło'), 'Anything-typed-2026')
  await userEvent.click(dialog.getByRole('button', { name: 'Wyłącz' }))
  expect(await dialog.findByRole('alert')).toHaveTextContent('Twoje konto nie ma jeszcze hasła.')
})
