import { test, expect, type Page } from '@playwright/test'

const owner = process.env.E2E_EMAIL
const member = process.env.E2E_MEMBER_EMAIL
const password = process.env.E2E_PASSWORD

async function signIn(page: Page, email: string) {
  await page.getByLabel('Adres e-mail', { exact: true }).fill(email)
  await page.getByLabel('Hasło', { exact: true }).fill(password!)
  await page.getByRole('button', { name: 'Zaloguj się', exact: true }).click()
}

async function signOut(page: Page) {
  const menu = page.getByRole('button', { name: 'Otwórz menu' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('button', { name: 'Wyloguj się', exact: true }).filter({ visible: true }).click()
  await expect(page).toHaveURL(/\/login(?:\?|$)/)
}

test('create and select a team, share invitation and join after login', async ({ page, context }, testInfo) => {
  test.skip(!owner || !member || !password, 'Set credentials for two real test accounts.')
  await context.grantPermissions(['clipboard-read', 'clipboard-write'])
  const name = `Zespół ${testInfo.project.name} ${Date.now()}`
  await page.goto('/login')
  await signIn(page, owner!)
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.getByRole('link', { name: 'Przejdź do zespołów' }).click()
  await page.getByRole('button', { name: 'Utwórz zespół', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Utwórz zespół' })
  await dialog.getByLabel('Nazwa zespołu').fill(name)
  await dialog.getByLabel('Strefa czasowa').selectOption('UTC')
  await dialog.getByRole('button', { name: 'Utwórz zespół', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  const selected = page.getByRole('region', { name: 'Wybrany zespół' })
  await expect(selected.getByRole('heading')).toHaveText(name)
  await expect(selected).toContainText('Właściciel')
  const teamId = new URL(page.url()).searchParams.get('team')!
  await page.reload()
  await expect(selected.getByRole('heading')).toHaveText(name)
  await selected.getByRole('button', { name: 'Zaproś do zespołu' }).click()
  const invite = page.getByRole('dialog', { name: 'Zaproś do zespołu' })
  const expiry = new Date(Date.now() + 3_600_000)
  await invite.getByLabel('Data wygaśnięcia').fill(new Date(expiry.getTime() - expiry.getTimezoneOffset() * 60_000).toISOString().slice(0, 16))
  await invite.getByLabel('Limit użyć', { exact: false }).fill('1')
  await invite.getByRole('button', { name: 'Generuj link' }).click()
  const link = await invite.getByLabel('Link zaproszeniowy').inputValue()
  expect(link).toMatch(/\/invite\/[A-Za-z0-9_-]{43}$/)
  await invite.getByRole('button', { name: 'Kopiuj link' }).click()
  await expect(invite.getByRole('status')).toHaveText('Skopiowano link do schowka.')
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(link)
  await invite.getByRole('button', { name: 'Skopiowano link', exact: true }).focus()
  await page.keyboard.press('Tab')
  expect(await invite.evaluate(dialog => dialog.contains(document.activeElement))).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('invitation-modal.png'), fullPage: false, animations: 'disabled' })
  await invite.getByRole('button', { name: 'Zamknij', exact: true }).click()
  await signOut(page)

  await page.goto(link)
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe(new URL(link).pathname)
  await page.reload()
  await signIn(page, member!)
  await expect(page.getByRole('heading', { name: 'Zaproszenie do zespołu' })).toBeVisible()
  const before = await page.request.get('/api/teams')
  expect((await before.json()).some((team: { id: string }) => team.id === teamId)).toBe(false)
  await page.getByRole('button', { name: 'Dołącz do zespołu' }).click()
  await expect(page.getByRole('heading', { name: 'Masz dostęp do zespołu' })).toBeVisible()
  await page.getByRole('link', { name: 'Przejdź do zespołu', exact: true }).click()
  await expect(selected.getByRole('heading')).toHaveText(name)
  await expect(selected).toContainText('Członek')
  await expect(selected.getByRole('button', { name: 'Zaproś do zespołu' })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('teams-member.png'), fullPage: true, animations: 'disabled' })
  await page.goto(link)
  await page.getByRole('button', { name: 'Dołącz do zespołu' }).click()
  await expect(page.getByRole('heading', { name: 'Masz dostęp do zespołu' })).toBeVisible()
  const after = await page.request.get('/api/teams')
  expect((await after.json()).filter((team: { id: string }) => team.id === teamId)).toHaveLength(1)
})

test('invalid invitation shows an error and return addresses stay local', async ({ page }) => {
  test.skip(!owner || !password, 'Set a real test account.')
  await page.goto('/login?returnTo=https%3A%2F%2Fexample.com')
  await signIn(page, owner!)
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto(`/invite/${'a'.repeat(43)}`)
  await page.getByRole('button', { name: 'Dołącz do zespołu' }).click()
  await expect(page.getByRole('alert')).toHaveText('Nie znaleziono zaproszenia. Sprawdź, czy link jest kompletny.')
  await page.getByRole('link', { name: 'Wróć do swoich zespołów' }).click()
  await expect(page.getByRole('heading', { name: 'Zespoły', exact: true })).toBeVisible()
})
