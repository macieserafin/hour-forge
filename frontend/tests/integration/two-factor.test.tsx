import { it, expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'

async function signInWithPassword() {
  await screen.findByRole('heading', { name: 'Zaloguj się' })
  await userEvent.type(screen.getByLabelText('Adres e-mail'), user.email)
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), 'secret')
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
}

it('asks for the code after the password and signs in only when it is right', async () => {
  let authenticated = false
  const answers: string[] = []
  mockApi({ 'GET /api/me': () => json(user, authenticated ? 200 : 401),
    'POST /api/auth/login': () => json({ code: 'TWO_FACTOR_REQUIRED' }, 202),
    'POST /api/auth/two-factor': (_url, options) => {
      const { code } = JSON.parse(String(options.body)) as { code: string }
      answers.push(code)
      authenticated = code === '123456'
      return json(authenticated ? null : { code: 'INVALID_CODE' }, authenticated ? 204 : 400)
    } })
  renderApp(<App />, '/time?team=team')
  await signInWithPassword()

  const field = await screen.findByLabelText('Kod z aplikacji lub kod zapasowy')
  expect(screen.getByRole('heading', { name: 'Logowanie dwuetapowe' })).toBeVisible()
  await userEvent.type(field, '654321')
  await userEvent.click(screen.getByRole('button', { name: 'Potwierdź' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Nieprawidłowy kod.')
  await userEvent.clear(field)
  await userEvent.type(field, ' 123456 ')
  await userEvent.click(screen.getByRole('button', { name: 'Potwierdź' }))
  expect(await screen.findByRole('heading', { name: 'Rejestr czasu' })).toBeVisible()
  expect(answers).toEqual(['654321', '123456'])
})

it('goes back to the password when the code came too late', async () => {
  mockApi({ 'GET /api/me': () => json(user, 401),
    'POST /api/auth/login': () => json({ code: 'TWO_FACTOR_REQUIRED' }, 202),
    'POST /api/auth/two-factor': () => json({ code: 'SIGN_IN_AGAIN' }, 401) })
  renderApp(<App />, '/login')
  await signInWithPassword()
  await userEvent.type(await screen.findByLabelText('Kod z aplikacji lub kod zapasowy'), '123456')
  await userEvent.click(screen.getByRole('button', { name: 'Potwierdź' }))
  expect(await screen.findByRole('heading', { name: 'Zaloguj się' })).toBeVisible()
  expect(screen.getByRole('alert')).toHaveTextContent('Czas na podanie kodu minął')
})

it('sets up two-step sign-in with a QR code, the code and the password, then shows the backup codes once', async () => {
  let enabled = false
  let sent: Record<string, string> | undefined
  mockApi({ 'GET /api/account/two-factor': () => json({ available: true, enabled, backupCodesLeft: enabled ? 10 : 0 }),
    'POST /api/account/two-factor/setup': () => json({ secret: 'JBSWY3DPEHPK3PXP', uri: 'otpauth://totp/HourForge:actor%40example.test?secret=JBSWY3DPEHPK3PXP' }),
    'POST /api/account/two-factor/enable': (_url, options) => {
      sent = JSON.parse(String(options.body)) as Record<string, string>
      enabled = true
      return json({ codes: ['abcde-fghij', 'kmnpq-rstuv'] })
    } })
  renderApp(<App />, '/account')
  const section = await screen.findByRole('region', { name: 'Logowanie dwuetapowe' })
  await userEvent.click(await within(section).findByRole('button', { name: 'Włącz logowanie dwuetapowe' }))

  expect(await within(section).findByRole('img', { name: /Kod QR/ })).toBeVisible()
  expect(section).toHaveTextContent('JBSW Y3DP EHPK 3PXP')
  await userEvent.type(within(section).getByLabelText('Kod z aplikacji'), '123 456')
  await userEvent.type(within(section).getByLabelText('Obecne hasło'), 'Current-password-2026')
  await userEvent.click(within(section).getByRole('button', { name: 'Włącz' }))

  const codes = await within(section).findByRole('region', { name: 'Kody zapasowe' })
  expect(within(codes).getAllByRole('listitem').map(item => item.textContent)).toEqual(['abcde-fghij', 'kmnpq-rstuv'])
  expect(sent).toEqual({ code: '123456', currentPassword: 'Current-password-2026' })
  await userEvent.click(within(codes).getByRole('button', { name: 'Zapisałem kody' }))
  expect(await within(section).findByText(/Pozostałe kody zapasowe/)).toHaveTextContent('10')
  expect(within(section).queryByText('abcde-fghij')).toBeNull()
})

it('turns two-step sign-in off only with the right password', async () => {
  let enabled = true
  mockApi({ 'GET /api/account/two-factor': () => json({ available: true, enabled, backupCodesLeft: 7 }),
    'POST /api/account/two-factor/disable': (_url, options) => {
      const { currentPassword } = JSON.parse(String(options.body)) as { currentPassword: string }
      if (currentPassword !== 'Right-password-2026') return json({}, 400)
      enabled = false
      return json(null, 204)
    } })
  renderApp(<App />, '/account')
  const section = await screen.findByRole('region', { name: 'Logowanie dwuetapowe' })
  await userEvent.click(await within(section).findByRole('button', { name: 'Wyłącz logowanie dwuetapowe' }))
  const dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText('Obecne hasło'), 'Wrong-password-2026')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Wyłącz' }))
  expect(await within(dialog).findByRole('alert')).toHaveTextContent('Obecne hasło jest nieprawidłowe.')
  await userEvent.clear(within(dialog).getByLabelText('Obecne hasło'))
  await userEvent.type(within(dialog).getByLabelText('Obecne hasło'), 'Right-password-2026')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Wyłącz' }))
  expect(await within(section).findByRole('button', { name: 'Włącz logowanie dwuetapowe' })).toBeVisible()
})

it('explains when the server cannot offer two-step sign-in', async () => {
  mockApi({ 'GET /api/account/two-factor': () => json({ available: false, enabled: false, backupCodesLeft: 0 }) })
  renderApp(<App />, '/account')
  expect(await screen.findByText(/nie jest włączone na tym serwerze/)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Włącz logowanie dwuetapowe' })).toBeNull()
})
