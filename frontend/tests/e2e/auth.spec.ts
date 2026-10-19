import { test, expect, owner, password, submitLogin, login, signOut } from '../support/e2e'

test('rejects invalid credentials and revokes access after logout', async ({ page }) => {
  await page.goto('/login')
  await submitLogin(page, owner, 'incorrect-password')
  await expect(page.getByRole('alert')).toHaveText('Nieprawidłowy adres e-mail lub hasło.')
  expect((await page.request.get('/api/me')).status()).toBe(401)
  await page.getByLabel('Hasło', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Zaloguj się', exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  expect((await (await page.request.get('/api/me')).json()).email).toBe(owner)
  await signOut(page)
  expect((await page.request.get('/api/me')).status()).toBe(401)
  await page.goto('/time')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
})

test('starts with clean team data and persists the session across reloads', async ({ page }) => {
  await login(page)
  expect(await (await page.request.get('/api/teams')).json()).toEqual([])
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Cześć, User.' })).toBeVisible()
  await page.getByRole('link', { name: 'Przejdź do zespołów' }).click()
  await expect(page.getByText('Nie należysz jeszcze do żadnego zespołu.')).toBeVisible()
})
