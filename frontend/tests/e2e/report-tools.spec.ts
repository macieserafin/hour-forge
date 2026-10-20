import { readFileSync } from 'node:fs'
import { test, expect, setupTeam, create, teamDate } from '../support/e2e'

test('owner sees who is working, shares a filtered report by address, downloads CSV and PDF and can hide the live view', async ({ page }, info) => {
  const { team, project } = await setupTeam(page, `Tools ${info.project.name} ${Date.now()}`)
  const today = teamDate(0)
  await create(page.request, '/api/time-entries', { teamId: team.id, projectId: project.id, workDate: today, durationSeconds: 3600, description: 'Zażółć faktura' })
  await create(page.request, '/api/time-entries', { teamId: team.id, projectId: project.id, workDate: today, durationSeconds: 1800, description: 'Spotkanie' })
  await create(page.request, '/api/timer/start', { teamId: team.id, projectId: project.id, description: 'Running now' })

  await page.goto(`/dashboard?team=${team.id}&view=team`)
  const live = page.getByRole('region', { name: 'Kto teraz pracuje', exact: true })
  await expect(live).toContainText('User One')
  await expect(live).toContainText('Pracuje')
  await expect(page.getByRole('figure', { name: 'Czas w kolejnych dniach' })).toBeVisible()
  await expect(page.getByRole('figure', { name: 'Podział czasu na projekty' })).toBeVisible()
  const total = page.getByRole('region', { name: 'Łączny czas zespołu', exact: true })
  await expect(total).toContainText('01:30:00')

  await page.getByLabel('Szukaj w opisach').fill('faktura')
  await page.getByRole('button', { name: 'Filtruj raport' }).click()
  await expect(page).toHaveURL(/q=faktura/)
  await expect(total).toContainText('01:00:00')
  await page.reload()
  await expect(page.getByLabel('Szukaj w opisach')).toHaveValue('faktura')
  await expect(total).toContainText('01:00:00')

  await page.getByRole('button', { name: 'Poprzedni tydzień' }).click()
  await expect(page).toHaveURL(/from=\d{4}-\d{2}-\d{2}/)
  await expect(total).toContainText('00:00:00')
  await page.getByRole('button', { name: 'Ten tydzień' }).click()
  await expect(total).toContainText('01:00:00')

  const [csv] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Pobierz CSV' }).click()])
  expect(csv.suggestedFilename()).toMatch(/^hourforge-raport-\d{4}-\d{2}-\d{2}_\d{4}-\d{2}-\d{2}\.csv$/)
  const csvText = readFileSync(await csv.path(), 'utf8')
  expect(csvText.startsWith('﻿Data pracy;Osoba;')).toBe(true)
  expect(csvText).toContain('Zażółć faktura')
  expect(csvText).not.toContain('Spotkanie')

  const [pdf] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Pobierz PDF' }).click()])
  expect(pdf.suggestedFilename()).toMatch(/\.pdf$/)
  expect(readFileSync(await pdf.path()).subarray(0, 5).toString()).toBe('%PDF-')

  const token = await (await page.request.get('/api/auth/csrf')).json()
  const hidden = await page.request.patch(`/api/teams/${team.id}`, { data: { name: team.name, timezone: team.timezone, activeTimersVisible: false },
    headers: { [token.headerName]: token.token } })
  expect(hidden.ok()).toBe(true)
  expect((await page.request.get(`/api/teams/${team.id}/reports/active-timers`)).status()).toBe(403)
  await page.reload()
  await expect(page.getByText('Podgląd aktywnych timerów jest wyłączony w ustawieniach zespołu.')).toBeVisible()
})
