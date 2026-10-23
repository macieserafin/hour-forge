import { test as base, expect, type Page, type APIRequestContext } from '@playwright/test'
import { execFileSync } from 'node:child_process'

export { expect }
export const owner = 'owner@hourforge-e2e.test'
export const member = 'member@hourforge-e2e.test'
export const password = 'E2E-owner-password-2026!'
export const memberPassword = 'E2E-member-password-2026!'

export const test = base.extend<{ isolatedDatabase: void }>({
  isolatedDatabase: [async ({ request }, use) => {
    const container = process.env.HOUR_FORGE_E2E_CONTAINER
    if (!container?.startsWith('hour-forge-e2e-')) throw new Error('Managed E2E database was not started. Run tests through playwright.config.ts.')
    const reset = () => execFileSync('docker', ['exec', container, 'psql', '-U', 'hourforge_e2e', '-d', 'hourforge_e2e',
      '-v', 'ON_ERROR_STOP=1', '-c', 'TRUNCATE TABLE teams CASCADE; DELETE FROM email_tokens; DELETE FROM jira_user_authorizations; DELETE FROM oauth_states; DELETE FROM user_preferences; DELETE FROM login_events; DELETE FROM spring_session; DELETE FROM user_credentials; DELETE FROM users;'], { stdio: 'pipe' })
    reset()
    await request.delete(`${mailApi()}/api/v1/messages`)
    try {
      for (const account of [
        { displayName: 'User One', email: owner, password },
        { displayName: 'User Two', email: member, password: memberPassword },
      ]) {
        const response = await post(request, '/api/auth/register', account)
        expect(response.status()).toBe(201)
      }
      // Sign-in needs a confirmed address; the seeded accounts skip the mailbox round trip.
      execFileSync('docker', ['exec', container, 'psql', '-U', 'hourforge_e2e', '-d', 'hourforge_e2e', '-v', 'ON_ERROR_STOP=1',
        '-c', 'UPDATE users SET email_verified_at = now();'], { stdio: 'pipe' })
      await use()
    } finally { reset() }
  }, { auto: true }],
})

const mailApi = () => process.env.HOUR_FORGE_E2E_MAIL_API ?? (() => { throw new Error('Managed E2E mail server was not started.') })()

/** Path of the newest link `/<kind>/<token>` that the mail server received for the address. */
export async function mailLink(request: APIRequestContext, to: string, kind: string): Promise<string> {
  const deadline = Date.now() + 15000
  while (Date.now() < deadline) {
    const found = await (await request.get(`${mailApi()}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}`)).json() as { messages?: { ID: string }[] }
    for (const { ID } of found.messages ?? []) {
      const message = await (await request.get(`${mailApi()}/api/v1/message/${ID}`)).json() as { Text: string }
      const match = new RegExp(`/${kind}/([A-Za-z0-9_-]+)`).exec(message.Text)
      if (match) return `/${kind}/${match[1]}`
    }
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error(`No ${kind} link was mailed to ${to}`)
}

export async function submitLogin(page: Page, email = owner, secret = email === member ? memberPassword : password) {
  await page.getByLabel('Adres e-mail', { exact: true }).fill(email)
  await page.getByLabel('Hasło', { exact: true }).fill(secret)
  await page.getByRole('button', { name: 'Zaloguj się', exact: true }).click()
}

export async function fillRegistration(page: Page, email: string, secret: string, displayName = 'Registration User') {
  await page.getByLabel('Nazwa wyświetlana').fill(displayName)
  await page.getByLabel('Adres e-mail', { exact: true }).fill(email)
  await page.getByLabel('Hasło', { exact: true }).fill(secret)
  await page.getByLabel('Powtórz hasło').fill(secret)
}

export async function login(page: Page, email = owner, secret = email === member ? memberPassword : password) {
  await page.goto('/login')
  await submitLogin(page, email, secret)
  await expect(page).toHaveURL(/\/dashboard$/)
}

export async function signOut(page: Page) {
  const menu = page.getByRole('button', { name: 'Otwórz menu' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('button', { name: 'Wyloguj się', exact: true }).filter({ visible: true }).click()
  await expect(page).toHaveURL(/\/login(?:\?|$)/)
}

export async function post(request: APIRequestContext, path: string, data: unknown) {
  const tokenResponse = await request.get('/api/auth/csrf')
  expect(tokenResponse.ok()).toBe(true)
  const token = await tokenResponse.json()
  return request.post(path, { data, headers: { [token.headerName]: token.token } })
}

export async function create(request: APIRequestContext, path: string, data: unknown) {
  const response = await post(request, path, data)
  expect(response.ok(), `${path}: ${response.status()} ${await response.text()}`).toBe(true)
  return response.status() === 204 ? null : response.json()
}
export const write = create

/** Work date (YYYY-MM-DD) a number of days before today in the team time zone; stays inside the 2-year work date window. */
export function teamDate(daysAgo = 0, timezone = 'Europe/Warsaw') {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(Date.now() - daysAgo * 86_400_000))
}

export async function setupTeam(page: Page, suffix: string) {
  await login(page)
  return createTeamProject(page, suffix)
}

export async function createTeamProject(page: Page, suffix: string) {
  const team = await create(page.request, '/api/teams', { name: `Team ${suffix}`, timezone: 'Europe/Warsaw' })
  const project = await create(page.request, `/api/teams/${team.id}/projects`, { name: `Project ${suffix}`, description: null })
  return { team, project }
}
