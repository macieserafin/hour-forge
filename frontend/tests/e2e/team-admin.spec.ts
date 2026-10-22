import { readFileSync } from 'node:fs'
import { inflateRawSync } from 'node:zlib'
import { test, expect, setupTeam, create, login, member, teamDate } from '../support/e2e'

/** Text of one file inside a zip archive (an .xlsx workbook), read through the central directory. */
function zipEntry(archive: Buffer, name: string) {
  const end = archive.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]))
  let at = archive.readUInt32LE(end + 16)
  for (let left = archive.readUInt16LE(end + 10); left > 0; left--) {
    const nameLength = archive.readUInt16LE(at + 28)
    const next = at + 46 + nameLength + archive.readUInt16LE(at + 30) + archive.readUInt16LE(at + 32)
    if (archive.toString('utf8', at + 46, at + 46 + nameLength) === name) {
      const local = archive.readUInt32LE(at + 42)
      const start = local + 30 + archive.readUInt16LE(local + 26) + archive.readUInt16LE(local + 28)
      const data = archive.subarray(start, start + archive.readUInt32LE(at + 20))
      return (archive.readUInt16LE(at + 10) === 8 ? inflateRawSync(data) : data).toString('utf8')
    }
    at = next
  }
  throw new Error(`${name} is missing from the archive`)
}

test('owner names an admin who tags and corrects a member\'s time and reads the timesheet and Excel report', async ({ page, browser }, info) => {
  const { team, project } = await setupTeam(page, `Admin ${info.project.name} ${Date.now()}`)
  const today = teamDate(0)
  const invite = await create(page.request, `/api/teams/${team.id}/invites`, { maxUses: 1 })
  const ownerEntry = await create(page.request, '/api/time-entries', { teamId: team.id, projectId: project.id, workDate: today, durationSeconds: 1800, description: 'Owner work' })
  const adminContext = await browser.newContext({ baseURL: new URL(page.url()).origin })
  try {
    const admin = await adminContext.newPage()
    await login(admin, member)
    await create(admin.request, `/api/invites/${invite.token}/accept`, {})
    await create(admin.request, '/api/time-entries', { teamId: team.id, projectId: project.id, workDate: today, durationSeconds: 3600, description: 'Member work' })

    await page.goto(`/teams?team=${team.id}`)
    await page.getByRole('button', { name: 'Nadaj administratora: User Two', exact: true }).click()
    await expect(page.getByText('Osoba User Two jest administratorem zespołu.')).toBeVisible()
    const ownerList = page.getByRole('region', { name: 'Lista członków zespołu' })
    await expect(ownerList.getByRole('row', { name: /User Two/ })).toContainText('Administrator')

    await admin.goto(`/teams?team=${team.id}`)
    const tags = admin.getByRole('region', { name: 'Tagi pracy' })
    await tags.getByRole('button', { name: 'Dodaj tag' }).click()
    const tagForm = admin.getByRole('dialog', { name: 'Dodaj tag' })
    await tagForm.getByLabel('Nazwa tagu').fill('Wsparcie')
    await tagForm.getByRole('button', { name: 'Zapisz tag' }).click()
    await expect(tagForm).not.toBeVisible()
    await expect(tags.getByRole('list', { name: 'Lista tagów' })).toContainText('Wsparcie')
    await expect(admin.getByRole('button', { name: /^Nadaj administratora:/ })).toHaveCount(0)
    await expect(admin.getByRole('button', { name: /^Przekaż własność:/ })).toHaveCount(0)

    await admin.getByRole('button', { name: 'Korekty czasu: User One', exact: true }).click()
    const corrections = admin.getByRole('dialog', { name: 'Korekty czasu: User One' })
    await corrections.getByRole('button', { name: `Popraw wpis: ${project.name} ${today}`, exact: true }).click()
    const form = admin.getByRole('dialog', { name: 'Popraw wpis czasu' })
    await form.getByLabel('Minuty').fill('45')
    await form.getByRole('combobox', { name: /^Tag/ }).selectOption({ label: 'Wsparcie' })
    await form.getByRole('button', { name: 'Zapisz korektę' }).click()
    const entries = admin.getByRole('list', { name: 'Wpisy osoby User One' })
    await expect(entries).toContainText('00:45:00')
    await expect(entries).toContainText('Wsparcie')
    await expect(entries).toContainText('poprawił(a): User Two')
    await admin.getByRole('dialog', { name: 'Korekty czasu: User One' }).getByRole('button', { name: 'Zamknij', exact: true }).click()
    const corrected = await (await page.request.get(`/api/teams/${team.id}/reports/entries?q=Owner`)).json()
    expect(corrected.data.content.find((item: { id: string }) => item.id === ownerEntry.id).durationSeconds).toBe(2700)

    await admin.goto(`/dashboard?team=${team.id}&view=team`)
    const sheet = admin.getByRole('region', { name: 'Tabela karty czasu' })
    await expect(sheet.getByRole('row', { name: /User One/ })).toContainText('0:45')
    await expect(sheet.getByRole('row', { name: /User Two/ })).toContainText('1:00')
    await expect(sheet.locator('tfoot')).toContainText('1:45')

    await admin.getByLabel('Tag', { exact: true }).selectOption({ label: 'Wsparcie' })
    await admin.getByRole('button', { name: 'Filtruj raport' }).click()
    await expect(admin).toHaveURL(/tag/)
    await expect(admin.getByRole('region', { name: 'Łączny czas zespołu', exact: true })).toContainText('00:45:00')
    const [xlsx] = await Promise.all([admin.waitForEvent('download'), admin.getByRole('button', { name: 'Pobierz Excel' }).click()])
    expect(xlsx.suggestedFilename()).toMatch(/^hourforge-raport-.+\.xlsx$/)
    const workbook = readFileSync(await xlsx.path())
    expect(workbook.subarray(0, 2).toString()).toBe('PK')
    const rows = zipEntry(workbook, 'xl/worksheets/sheet1.xml')
    expect(rows).toContain('Owner work')
    expect(rows).toContain('Wsparcie')
    expect(rows).not.toContain('Member work')
  } finally {
    await adminContext.close()
  }
})
