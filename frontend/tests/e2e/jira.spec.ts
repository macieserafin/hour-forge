import { test, expect, setupTeam, teamDate } from '../support/e2e'

test('connects the team to Jira, links an entry to an issue and removes the links when the site is disconnected', async ({ page }) => {
  const { team, project } = await setupTeam(page, `jira ${Date.now()}`)

  await page.goto(`/integrations?team=${team.id}`)
  await expect(page.getByRole('heading', { name: 'Jira Cloud' })).toBeVisible()
  await page.getByRole('button', { name: 'Połącz zespół z Jira Cloud' }).click()
  // The stand-in consent screen approves at once; the browser comes back through the backend callback.
  await expect(page).toHaveURL(/\/integrations\?.*jira=connected/)
  await expect(page.getByRole('status').filter({ hasText: 'Konto Jira zostało połączone.' })).toBeVisible()
  await expect(page.getByText('E2E Jira', { exact: false })).toBeVisible()
  await expect(page.getByText('Połączone', { exact: true })).toBeVisible()

  await page.goto(`/time?team=${team.id}`)
  await page.getByRole('button', { name: 'Dodaj wpis ręczny' }).click()
  const manual = page.getByRole('dialog', { name: 'Dodaj wpis ręczny' })
  await manual.getByLabel('Projekt', { exact: true }).selectOption(project.id)
  await manual.getByLabel('Data pracy').fill(teamDate(0))
  await manual.getByLabel('Minuty').fill('15')
  await manual.getByRole('button', { name: 'Wybierz zadanie z Jiry' }).click()
  await manual.getByRole('searchbox', { name: 'Szukaj zadania' }).fill('second')
  await manual.getByRole('button', { name: /PROJ-2 Second task/ }).click()
  await expect(manual.getByText('PROJ-2', { exact: true })).toBeVisible()
  const saved = page.waitForResponse(response => response.url().endsWith('/api/time-entries') && response.request().method() === 'POST')
  await manual.getByRole('button', { name: 'Zapisz wpis' }).click()
  expect((await saved).status()).toBe(201)
  await expect(manual).not.toBeVisible()

  const link = page.getByRole('link', { name: /PROJ-2/ })
  await expect(link).toHaveAttribute('href', 'https://e2e.atlassian.net/browse/PROJ-2')
  await page.reload()
  await expect(page.getByRole('link', { name: /PROJ-2/ })).toBeVisible()

  // A replayed or forged callback is refused and explained, without touching the connection.
  await page.goto('/api/integrations/jira/callback?code=e2e-code&state=forged-state')
  await expect(page).toHaveURL(/\/integrations\?jira=invalid-state/)
  await expect(page.getByRole('alert')).toContainText('wygasło lub zostało już użyte')

  await page.goto(`/integrations?team=${team.id}`)
  await page.getByRole('button', { name: 'Odłącz witrynę' }).click()
  const confirm = page.getByRole('dialog', { name: 'Odłącz witrynę Jira' })
  await confirm.getByRole('button', { name: 'Odłącz witrynę' }).click()
  await expect(page.getByRole('button', { name: 'Połącz zespół z Jira Cloud' })).toBeVisible()
  await page.goto(`/time?team=${team.id}`)
  await expect(page.getByText('00:15:00').first()).toBeVisible()
  await expect(page.getByRole('link', { name: /PROJ-2/ })).toHaveCount(0)
  await expect(page.getByText('PROJ-2')).toHaveCount(0)
})

test('offers the owner of a team without a Jira site to connect one', async ({ page }) => {
  const { team } = await setupTeam(page, `jira-empty ${Date.now()}`)
  await page.goto(`/integrations?team=${team.id}`)
  await expect(page.getByText('Zespół nie ma jeszcze połączonej witryny Jira.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Połącz zespół z Jira Cloud' })).toBeEnabled()
})
