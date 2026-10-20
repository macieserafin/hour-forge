import { test, expect, fillRegistration, submitLogin, signOut, login, mailLink } from '../support/e2e'

const email = 'cycle@account.example.test'
const secret = 'Account-password-2026!'
const newSecret = 'Account-new-password-2026!'

test('an account can be confirmed, renamed, get a new password, be downloaded and be deleted', async ({ page }) => {
  await page.goto('/register')
  await fillRegistration(page, email, secret, 'Anna Konto')
  await page.getByRole('button', { name: 'Utwórz konto', exact: true }).click()
  await page.goto(await mailLink(page.request, email, 'verify-email'))
  await expect(page.getByText('Adres e-mail został potwierdzony.')).toBeVisible()
  await page.goto('/login')
  await submitLogin(page, email, secret)
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto('/account')
  await page.getByLabel('Nazwa wyświetlana').fill('Anna Nowak')
  await page.getByRole('button', { name: 'Zapisz nazwę' }).click()
  await expect(page.getByText('Nazwa wyświetlana została zapisana.')).toBeVisible()
  expect((await (await page.request.get('/api/me')).json()).displayName).toBe('Anna Nowak')

  await page.getByLabel('Obecne hasło', { exact: true }).fill(secret)
  await page.getByLabel('Nowe hasło', { exact: true }).fill(newSecret)
  await page.getByLabel('Powtórz nowe hasło').fill(newSecret)
  await page.getByRole('button', { name: 'Zmień hasło' }).click()
  await expect(page.getByText(/Hasło zostało zmienione/)).toBeVisible()
  expect((await page.request.get('/api/me')).status()).toBe(200)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Pobierz moje dane' }).click()
  expect((await download).suggestedFilename()).toMatch(/^hourforge-export-\d{4}-\d{2}-\d{2}\.json$/)

  await signOut(page)
  await submitLogin(page, email, secret)
  await expect(page.getByRole('alert')).toContainText('Nieprawidłowy adres e-mail lub hasło')
  await login(page, email, newSecret)

  await page.goto('/account')
  await page.getByRole('button', { name: 'Usuń konto', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Usuń konto' })
  await dialog.getByLabel('Obecne hasło').fill(newSecret)
  await dialog.getByRole('button', { name: 'Usuń konto na stałe' }).click()
  await expect(page).toHaveURL(/\/login(?:\?|$)/)
  expect((await page.request.get('/api/me')).status()).toBe(401)
  await submitLogin(page, email, newSecret)
  await expect(page.getByRole('alert')).toContainText('Nieprawidłowy adres e-mail lub hasło')
})
