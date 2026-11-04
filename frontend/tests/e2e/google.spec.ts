import type { Page } from '@playwright/test'
import { test, expect, owner, password, login, signOut, submitLogin, mailLink, expectMail, create, totp, setGoogleAccount } from '../support/e2e'

// Fictitious Google accounts. A hosted domain makes Google authoritative for the address (A13-D4), so it counts as confirmed.
const domain = 'google-e2e.example.test'
const newcomer = { sub: 'e2e-google-newcomer', email: `nowa.osoba@${domain}`, emailVerified: true, hd: domain, name: 'Nowa Osoba' }
const ownersGoogle = { sub: 'e2e-google-owner', email: `wlasciciel@${domain}`, emailVerified: true, hd: domain, name: 'Owner Google' }

async function continueWithGoogle(page: Page, from: '/login' | '/register' = '/login') {
  await page.goto(from)
  await page.getByRole('link', { name: from === '/login' ? 'Zaloguj się przez Google' : 'Załóż konto przez Google' }).click()
}

/**
 * The dashboard has read the session before the scenario asks the server itself. Workaround for the known bug HF-W-012:
 * parallel first requests of a new session fail with 500 (ActivityFilter), so scenarios do not race the page.
 * After the fix, the heading wait can go and this can become a plain URL check.
 */
async function onDashboard(page: Page) {
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { name: /^Cześć, / })).toBeVisible()
}

async function me(page: Page) {
  const response = await page.request.get('/api/me')
  return response.status() === 200 ? await response.json() as { id: string; email: string; displayName: string; hasPassword: boolean; google: { linked: boolean; email: string | null } | null } : null
}

async function linkGoogle(page: Page) {
  await setGoogleAccount(page.request, { account: ownersGoogle })
  await page.goto('/account')
  const section = page.getByRole('region', { name: 'Logowanie przez Google' })
  await section.getByRole('button', { name: 'Połącz z Google' }).click()
  const dialog = page.getByRole('dialog', { name: 'Połącz z Google' })
  await dialog.getByLabel('Obecne hasło').fill(password)
  await dialog.getByRole('button', { name: 'Przejdź do Google' }).click()
  await expect(page).toHaveURL(/\/account\?google=LINKED#google$/)
  await expect(section.getByRole('status')).toHaveText('Konto Google zostało połączone.')
  await expect(section).toContainText(`Połączone z kontem Google ${ownersGoogle.email}`)
  await expectMail(page.request, owner, 'Połączono konto Google z HourForge')
}

test('a new person signs up with Google, comes back with Google and cannot remove the only way to sign in', async ({ page }) => {
  await setGoogleAccount(page.request, { account: newcomer })
  await continueWithGoogle(page, '/register')
  await onDashboard(page)
  expect(await me(page)).toMatchObject({ email: newcomer.email, displayName: 'Nowa Osoba', hasPassword: false, google: { linked: true, email: newcomer.email } })

  await page.goto('/account')
  const section = page.getByRole('region', { name: 'Logowanie przez Google' })
  await expect(section).toContainText('Nie możesz odłączyć Google, dopóki nie ustawisz hasła.')
  await expect(section.getByRole('button', { name: 'Odłącz Google' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Ustaw hasło' })).toHaveCount(3)

  await signOut(page)
  await continueWithGoogle(page)
  await onDashboard(page)
  expect((await me(page))?.email).toBe(newcomer.email)
})

test('Google with the address of an existing password account neither signs in nor takes the account over', async ({ page }) => {
  await setGoogleAccount(page.request, { account: { sub: 'e2e-google-intruder', email: owner, emailVerified: true, hd: 'hourforge-e2e.test', name: 'Someone' } })
  await continueWithGoogle(page)
  await expect(page).toHaveURL(/\/login\?google=GOOGLE_ACCOUNT_EXISTS$/)
  await expect(page.getByRole('alert')).toHaveText('Konto z tym adresem już istnieje. Zaloguj się hasłem, a potem połącz Google w ustawieniach konta.')
  expect(await me(page)).toBeNull()
  await login(page)
  expect((await me(page))?.google ?? null).toBeNull()
})

test('cancelling at Google returns to the sign-in page with a message', async ({ page }) => {
  await setGoogleAccount(page.request, { account: newcomer, cancel: true })
  await continueWithGoogle(page)
  await expect(page).toHaveURL(/\/login\?google=GOOGLE_CANCELLED$/)
  await expect(page.getByRole('alert')).toHaveText('Logowanie przez Google zostało anulowane.')
  expect(await me(page)).toBeNull()
})

test('an account connects Google in the settings, signs in with it and disconnects it again', async ({ page }) => {
  await login(page)
  await linkGoogle(page)

  await signOut(page)
  await continueWithGoogle(page)
  await onDashboard(page)
  expect((await me(page))?.email).toBe(owner)

  await page.goto('/account')
  const section = page.getByRole('region', { name: 'Logowanie przez Google' })
  await section.getByRole('button', { name: 'Odłącz Google' }).click()
  const dialog = page.getByRole('dialog', { name: 'Odłącz Google' })
  await dialog.getByLabel('Obecne hasło').fill(password)
  await dialog.getByRole('button', { name: 'Odłącz' }).click()
  await expect(section.getByRole('status')).toHaveText('Konto Google zostało odłączone.')
  await expect(section.getByRole('button', { name: 'Połącz z Google' })).toBeVisible()
  expect((await me(page))?.google ?? null).toBeNull()
  await expectMail(page.request, owner, 'Odłączono konto Google od HourForge')
})

test('Google still asks for the two-step code of an account that has it on', async ({ page }) => {
  await login(page)
  await linkGoogle(page)
  const { secret } = await create(page.request, '/api/account/two-factor/setup', {}) as { secret: string }
  await create(page.request, '/api/account/two-factor/enable', { code: totp(secret), currentPassword: password })

  await signOut(page)
  await continueWithGoogle(page)
  await expect(page).toHaveURL(/\/login\?step=two-factor$/)
  expect(await me(page)).toBeNull()
  // The code that turned two-step sign-in on cannot be used again; the next period's code is accepted.
  await page.getByLabel('Kod z aplikacji lub kod zapasowy').fill(totp(secret, 1))
  await page.getByRole('button', { name: 'Potwierdź' }).click()
  await onDashboard(page)
  expect((await me(page))?.email).toBe(owner)
})

test('an account made with Google sets its first password with the reset link and then signs in with it', async ({ page }) => {
  const firstPassword = 'Google-first-password-2026!'
  await setGoogleAccount(page.request, { account: newcomer })
  await continueWithGoogle(page, '/register')
  await onDashboard(page)

  await page.goto('/account')
  await page.getByRole('region', { name: 'Zmiana hasła' }).getByRole('button', { name: 'Ustaw hasło' }).click()
  await expect(page.getByText('Wysłaliśmy link do ustawienia hasła. Po ustawieniu hasła zaloguj się ponownie.')).toBeVisible()
  await page.goto(await mailLink(page.request, newcomer.email, 'reset-password'))
  await page.getByLabel('Nowe hasło', { exact: true }).fill(firstPassword)
  await page.getByLabel('Powtórz nowe hasło').fill(firstPassword)
  await page.getByRole('button', { name: 'Ustaw hasło' }).click()
  await expect(page.getByText(/Hasło zostało zmienione/)).toBeVisible()

  await page.goto('/login')
  await submitLogin(page, newcomer.email, firstPassword)
  await onDashboard(page)
  expect(await me(page)).toMatchObject({ email: newcomer.email, hasPassword: true })
})

test('an account made with Google deletes itself with the e-mailed link, only after pressing the button', async ({ page }) => {
  await setGoogleAccount(page.request, { account: newcomer })
  await continueWithGoogle(page, '/register')
  await onDashboard(page)
  const deletedId = (await me(page))!.id

  await page.goto('/account')
  await page.getByRole('button', { name: 'Usuń konto', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Usuń konto' })
  await dialog.getByRole('button', { name: 'Wyślij link potwierdzający' }).click()
  await expect(dialog.getByRole('status')).toHaveText('Wysłaliśmy link potwierdzający. Otwórz go, aby usunąć konto.')

  await page.goto(await mailLink(page.request, newcomer.email, 'confirm-account-deletion'))
  await expect(page.getByRole('button', { name: 'Usuń konto na stałe' })).toBeVisible()
  // Opening the link alone, as a mail scanner would, leaves the account in place.
  expect((await me(page))?.email).toBe(newcomer.email)
  await page.getByRole('button', { name: 'Usuń konto na stałe' }).click()
  await expect(page.getByRole('status')).toHaveText(/Konto zostało usunięte\. Wszystkie urządzenia tego konta zostały wylogowane\./)
  expect(await me(page)).toBeNull()
  await expectMail(page.request, newcomer.email, 'Konto w HourForge zostało usunięte')

  // The link to Google went with the account: the same Google account now starts a new one (F1), never the old one.
  await continueWithGoogle(page)
  await onDashboard(page)
  const again = await me(page)
  expect(again).toMatchObject({ email: newcomer.email, hasPassword: false, google: { linked: true } })
  expect(again!.id).not.toBe(deletedId)
})
