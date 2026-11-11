import { execFileSync } from 'node:child_process'
import { create, expect, login, member, memberPassword, owner, password, teamDate } from './e2e'
import type { Project, Team, User } from '../../src/lib/api'

/**
 * Demo data sets A–E for the frontend rebuild (document 06, section 2). Each set starts from the empty database the
 * managed E2E fixture gives every test, with two accounts: Maja Nowak (`owner`, also the instance operator) and
 * Tomasz Kowalczyk (`member`). Light data goes through the public API; history and the large team are written to the
 * disposable test database directly, with checksums computed here so a test can prove where every total comes from.
 */

type Page = Parameters<typeof login>[0]
type Request = Page['request']
type Session = { request: Request; close(): Promise<void> }
export type Browser = { newContext(options: { baseURL?: string }): Promise<Session> }

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5186'
const WARSAW = 'Europe/Warsaw'
const DAY = 86_400

// ---------- helpers ----------

/** Runs SQL in the disposable E2E database and returns the unaligned output. */
export function sql(query: string): string {
  const container = process.env.HOUR_FORGE_E2E_CONTAINER
  if (!container?.startsWith('hour-forge-e2e-')) throw new Error('Demo data needs the managed E2E database.')
  return execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'hourforge_e2e', '-d', 'hourforge_e2e', '-v', 'ON_ERROR_STOP=1', '-tA'],
    { input: query, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trim()
}

const quote = (value: string | null) => value === null ? 'NULL' : `'${value.replace(/'/g, "''")}'`

async function send(request: Request, method: 'PATCH' | 'PUT' | 'DELETE', path: string, data?: unknown) {
  const csrf = await (await request.get('/api/auth/csrf')).json()
  const response = await request.fetch(path, { method, data, headers: { [csrf.headerName]: csrf.token } })
  expect(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`).toBe(true)
  return response.status() === 204 ? null : response.json()
}

/** A separate signed-in API session, e.g. the second person of a team. */
export async function signIn(browser: Browser, email: string, secret: string): Promise<Session> {
  const context = await browser.newContext({ baseURL })
  const csrf = await (await context.request.get('/api/auth/csrf')).json()
  const response = await context.request.post('/api/auth/login', { form: { email, password: secret }, headers: { [csrf.headerName]: csrf.token } })
  expect(response.ok(), `login ${email}: ${response.status()}`).toBe(true)
  return context
}

const rename = (request: Request, displayName: string) => send(request, 'PATCH', '/api/account/profile', { displayName })
const me = async (request: Request): Promise<User> => (await request.get('/api/me')).json()
const newTeam = (request: Request, name: string, timezone = WARSAW): Promise<Team> => create(request, '/api/teams', { name, timezone })
const newProject = (request: Request, team: Team, name: string, color: string, description: string | null = null): Promise<Project> =>
  create(request, `/api/teams/${team.id}/projects`, { name, color, description })
const updateTeam = (request: Request, team: Team, changes: Record<string, unknown>) =>
  send(request, 'PATCH', `/api/teams/${team.id}`, { name: team.name, timezone: team.timezone, ...changes })
const invite = (request: Request, team: Team, maxUses: number | null = 1, days = 7): Promise<{ id: string; token: string; invitationUrl: string }> =>
  create(request, `/api/teams/${team.id}/invites`, { expiresAt: new Date(Date.now() + days * DAY * 1000).toISOString(), maxUses })
const accept = (request: Request, token: string): Promise<Team> => create(request, `/api/invites/${token}/accept`, {})
const setRole = (request: Request, team: Team, userId: string, role: 'ADMIN' | 'MEMBER') =>
  send(request, 'PUT', `/api/teams/${team.id}/members/${userId}/role`, { role })
const manual = (request: Request, team: Team, project: Project, workDate: string | null, durationSeconds: number, description: string, tagId?: string) =>
  create(request, '/api/time-entries', { teamId: team.id, projectId: project.id, workDate, durationSeconds, description, ...(tagId ? { tagId } : {}) })

/** One historical entry written straight to the database. Timer entries start at the given local hour of their work date. */
export interface Row { userId: string; team: Team; project: Project; workDate: string | null; seconds: number; type: 'TIMER' | 'MANUAL'; description: string; startHour?: number; tagId?: string }

function insertRows(rows: Row[]) {
  for (let offset = 0; offset < rows.length; offset += 500) {
    const values = rows.slice(offset, offset + 500).map(row => {
      if (row.type === 'TIMER' && !row.workDate) throw new Error('A timer entry needs a work date.')
      const start = row.type === 'TIMER'
        ? `((${quote(row.workDate)}::date + time '00:00' + interval '${row.startHour ?? 9} hours') AT TIME ZONE ${quote(row.team.timezone)})`
        : 'NULL'
      const end = row.type === 'TIMER' ? `(${start} + interval '${row.seconds} seconds')` : 'NULL'
      return `(gen_random_uuid(), ${quote(row.userId)}, ${quote(row.team.id)}, ${quote(row.project.id)}, '${row.type}', ${row.workDate ? `${quote(row.workDate)}::date` : 'NULL'}, ${start}, ${end}, ${row.seconds}, ${quote(row.description)}, ${row.tagId ? quote(row.tagId) : 'NULL'})`
    })
    sql(`INSERT INTO time_entries (id, user_id, team_id, project_id, entry_type, work_date, started_at, ended_at, duration_seconds, description, tag_id) VALUES\n${values.join(',\n')};`)
  }
}

const weekday = (date: string) => new Date(`${date}T12:00:00Z`).getUTCDay()
const isWorkday = (date: string) => weekday(date) !== 0 && weekday(date) !== 6
const sum = (rows: { seconds: number }[]) => rows.reduce((total, row) => total + row.seconds, 0)

/** Total and count the team report gives for a period (or for undated work), as one person sees it. */
export async function reportTotals(request: Request, team: Team, filter: { from?: string; to?: string; undated?: boolean; userId?: string }) {
  const params = new URLSearchParams()
  if (filter.undated) params.set('undated', 'true')
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  if (filter.userId) params.set('userIds', filter.userId)
  const response = await request.get(`/api/teams/${team.id}/reports/summary?${params}`)
  expect(response.ok(), `summary ${team.name}: ${response.status()} ${await response.text()}`).toBe(true)
  return (await response.json()).totals as { durationSeconds: number; entryCount: number }
}

const descriptions = ['Przegląd wymagań', 'Spotkanie z klientem', 'Implementacja formularza', 'Poprawki po testach', 'Dokumentacja zmian',
  'Analiza zgłoszenia', 'Przygotowanie prezentacji', 'Konsultacje w zespole', 'Testy na telefonie', 'Porządkowanie zadań']

// ---------- A. Nowy użytkownik ----------

/**
 * Tomasz has an account and no team yet. Maja's team offers invitations in every state. The page ends signed in as Tomasz.
 * Google sign-up is not prepared here: the managed fixture already runs a Google stand-in (see e2e/google.spec.ts).
 */
export async function prepareNewcomer(page: Page) {
  await login(page)
  await rename(page.request, 'Maja Nowak')
  const team = await newTeam(page.request, 'Studio Atlas')
  await newProject(page.request, team, 'Portal klienta', 'TEAL')
  const valid = await invite(page.request, team, 5, 14)
  const expired = await invite(page.request, team)
  sql(`UPDATE team_invites SET expires_at = now() - interval '1 day' WHERE id = ${quote(expired.id)};`)
  const used = await invite(page.request, team, 1)
  sql(`UPDATE team_invites SET used_count = 1 WHERE id = ${quote(used.id)};`)
  const revoked = await invite(page.request, team)
  await send(page.request, 'DELETE', `/api/teams/${team.id}/invites/${revoked.id}`)

  await page.context().clearCookies()
  await login(page, member, memberPassword)
  await rename(page.request, 'Tomasz Kowalczyk')
  return { team, invitations: { valid: `/invite/${valid.token}`, expired: `/invite/${expired.token}`, used: `/invite/${used.token}`, revoked: `/invite/${revoked.token}` } }
}

// ---------- B. Praca indywidualna ----------

/**
 * Maja works alone: a one-person team named like the proposed personal space (O01), three active projects and one
 * archived, eight weeks of timer and manual work on weekdays, undated entries, a tag and templates. Today stays empty.
 */
export async function prepareSolo(page: Page) {
  await login(page)
  await rename(page.request, 'Maja Nowak')
  const user = await me(page.request)
  const team = await newTeam(page.request, 'Przestrzeń osobista')
  const projects = [
    await newProject(page.request, team, 'Strona dla piekarni', 'AMBER', 'Nowa strona i sklep internetowy'),
    await newProject(page.request, team, 'Aplikacja treningowa', 'TEAL'),
    await newProject(page.request, team, 'Praca dyplomowa', 'VIOLET'),
  ]
  const old = await newProject(page.request, team, 'Logo dla kawiarni', 'BROWN')
  const tag = await create(page.request, `/api/teams/${team.id}/tags`, { name: 'Spotkania', color: 'BLUE' })
  await create(page.request, `/api/teams/${team.id}/templates`, { name: 'Spotkanie z klientem', projectId: projects[0]!.id, description: 'Spotkanie z klientem', tagId: tag.id })
  await create(page.request, `/api/teams/${team.id}/templates`, { name: 'Pisanie pracy', projectId: projects[2]!.id, description: 'Pisanie rozdziału' })

  const rows: Row[] = []
  for (let daysAgo = 1; daysAgo <= 56; daysAgo++) {
    const workDate = teamDate(daysAgo, WARSAW)
    if (!isWorkday(workDate)) continue
    let hour = 8
    for (let k = 0; k <= daysAgo % 3; k++) {
      const seconds = 1800 * (1 + ((daysAgo * 3 + k * 5) % 6))
      const project = daysAgo > 42 && k === 0 ? old : projects[(daysAgo + k) % 3]!
      rows.push({ userId: user.id, team, project, workDate, seconds, type: (daysAgo + k) % 2 === 0 ? 'TIMER' : 'MANUAL',
        description: descriptions[(daysAgo + k) % descriptions.length]!, startHour: hour, tagId: k === 1 ? tag.id : undefined })
      hour += Math.ceil(seconds / 3600) + 1
    }
  }
  insertRows(rows)
  await create(page.request, `/api/teams/${team.id}/projects/${old.id}/archive`, {})
  const undated = [
    await manual(page.request, team, projects[2]!, null, 7200, 'Czytanie literatury do pracy'),
    await manual(page.request, team, projects[0]!, null, 3600, 'Poprawki zgłoszone telefonicznie'),
    await manual(page.request, team, projects[1]!, null, 5400, 'Projekt ekranów, data nieznana'),
  ]

  return {
    team, projects, archivedProject: old, tag, undated,
    expected: {
      from: teamDate(56, WARSAW), to: teamDate(1, WARSAW),
      datedSeconds: sum(rows), datedEntries: rows.length,
      timerEntries: rows.filter(row => row.type === 'TIMER').length,
      undatedSeconds: 7200 + 3600 + 5400, undatedEntries: 3,
    },
  }
}

// ---------- C. Kilka zespołów ----------

/**
 * Maja owns Studio Atlas (Warsaw) and joins Tomasz's Agencja Nord (London, as ADMIN) and Koło naukowe (New York, as
 * MEMBER). Atlas and Nord both have a project called "Strona WWW". Nord requires a description, Koło a tag or Jira
 * issue. Atlas is connected to the Jira stand-in. Two weeks of history for both people. The page ends signed in as Maja.
 */
export async function prepareTeams(page: Page, browser: Browser) {
  await login(page)
  await rename(page.request, 'Maja Nowak')
  const maja = await me(page.request)
  const tomaszSession = await signIn(browser, member, memberPassword)
  try {
    const tomaszRequest = tomaszSession.request
    await rename(tomaszRequest, 'Tomasz Kowalczyk')
    const tomasz = await me(tomaszRequest)

    const atlas = await newTeam(page.request, 'Studio Atlas')
    const atlasWeb = await newProject(page.request, atlas, 'Strona WWW', 'TEAL')
    const atlasApp = await newProject(page.request, atlas, 'Aplikacja mobilna', 'BLUE')
    const nord = await newTeam(tomaszRequest, 'Agencja Nord', 'Europe/London')
    const nordWeb = await newProject(tomaszRequest, nord, 'Strona WWW', 'ORANGE')
    const nordCampaign = await newProject(tomaszRequest, nord, 'Kampania jesienna', 'RED')
    await updateTeam(tomaszRequest, nord, { requireDescription: true })
    const club = await newTeam(tomaszRequest, 'Koło naukowe', 'America/New_York')
    const clubResearch = await newProject(tomaszRequest, club, 'Projekt badawczy', 'GREEN')
    const clubTag = await create(tomaszRequest, `/api/teams/${club.id}/tags`, { name: 'Analiza', color: 'VIOLET' })
    await updateTeam(tomaszRequest, club, { requireTagOrIssue: true })

    await accept(page.request, (await invite(tomaszRequest, nord)).token)
    await accept(page.request, (await invite(tomaszRequest, club)).token)
    await accept(tomaszRequest, (await invite(page.request, atlas)).token)
    await setRole(tomaszRequest, nord, maja.id, 'ADMIN')

    // Through the consent flow of the Atlassian stand-in, independent of any screen of the app.
    const { url } = await create(page.request, '/api/integrations/jira/authorize', { teamId: atlas.id, intent: 'SITE' })
    await page.goto(url)
    await expect.poll(async () => (await (await page.request.get(`/api/integrations/jira/status?teamId=${atlas.id}`)).json()).siteConnected).toBe(true)

    const rows: Row[] = []
    for (let daysAgo = 1; daysAgo <= 14; daysAgo++) {
      for (const [index, [userId, team, project, tagId]] of ([
        [maja.id, atlas, daysAgo % 2 ? atlasWeb : atlasApp, undefined],
        [maja.id, nord, daysAgo % 3 ? nordWeb : nordCampaign, undefined],
        [maja.id, club, clubResearch, clubTag.id],
        [tomasz.id, nord, nordWeb, undefined],
        [tomasz.id, atlas, atlasApp, undefined],
      ] as const).entries()) {
        const workDate = teamDate(daysAgo, team.timezone)
        if (!isWorkday(workDate) || (index === 2 && daysAgo % 4 !== 0)) continue
        rows.push({ userId, team, project, workDate, seconds: 900 * (2 + ((daysAgo + index) % 7)), type: (daysAgo + index) % 2 ? 'TIMER' : 'MANUAL',
          description: descriptions[(daysAgo * 2 + index) % descriptions.length]!, startHour: 9 + index, tagId })
      }
    }
    insertRows(rows)
    const own = (team: Team) => rows.filter(row => row.userId === maja.id && row.team.id === team.id)
    return {
      maja, tomasz, teams: { atlas, nord, club }, projects: { atlasWeb, atlasApp, nordWeb, nordCampaign, clubResearch }, clubTag,
      expected: {
        // Each team counts its own work dates; the range is the widest one the rows can use.
        from: teamDate(14, 'America/New_York') < teamDate(14, WARSAW) ? teamDate(14, 'America/New_York') : teamDate(14, WARSAW),
        to: teamDate(1, WARSAW),
        majaSeconds: { atlas: sum(own(atlas)), nord: sum(own(nord)), club: sum(own(club)) },
        teamSeconds: { atlas: sum(rows.filter(row => row.team.id === atlas.id)), nord: sum(rows.filter(row => row.team.id === nord.id)) },
      },
    }
  } finally {
    await tomaszSession.close()
  }
}

// ---------- D. Większy zespół ----------

const firstNames = ['Anna', 'Piotr', 'Katarzyna', 'Michał', 'Agnieszka', 'Krzysztof', 'Magdalena', 'Tomasz', 'Joanna', 'Paweł',
  'Aleksandra', 'Łukasz', 'Ewa', 'Grzegorz', 'Zofia', 'Jakub', 'Natalia', 'Wojciech', 'Julia', 'Bartosz']
const lastNames = ['Nowak', 'Wiśniewska', 'Wójcik', 'Kowalczyk', 'Kamińska', 'Lewandowski', 'Zielińska', 'Szymański', 'Woźniak', 'Dąbrowski',
  'Kozłowska', 'Jankowski', 'Mazur', 'Kwiatkowska', 'Krawczyk', 'Piotrowski', 'Grabowska', 'Nowakowski', 'Pawłowska', 'Michalski',
  'Król', 'Wieczorek', 'Jabłońska', 'Wróbel', 'Majewski']
const areas = ['Portal', 'Aplikacja', 'Integracja', 'Kampania', 'Audyt', 'Migracja', 'Raport', 'Szkolenie', 'Wsparcie', 'Badanie']
const clients = ['Atlas', 'Borealis', 'Cyfrowa Wieś', 'Delta', 'Echo Media', 'Fala', 'Granit', 'Horyzont', 'Iskra', 'Jantar',
  'Kompas', 'Lumen', 'Mosty', 'Nadir', 'Orbita', 'Puls', 'Kwarc', 'Rubin', 'Sowa', 'Tęcza']
const colors = ['VIOLET', 'BLUE', 'TEAL', 'GREEN', 'AMBER', 'ORANGE', 'RED', 'PINK', 'SLATE', 'BROWN']

/** Duration of large-team entry `i` (1-based), shared by the SQL below and the checksum. */
const largeSeconds = (i: number) => 900 * (1 + (i % 16))
const largeUndated = (i: number) => i % 3 !== 0 && i % 20 === 1

/**
 * Kuźnia Software: Maja and 99 more people (five ADMINs), 200 projects (20 archived), 50,000 entries over the 90 days
 * before today, a third of them timers and some manual entries without a date. Only Maja can sign in; the others are data.
 * This is a test profile from document 06, not a promise of unlimited scale.
 */
export async function prepareLargeTeam(page: Page, entries = 50_000) {
  await login(page)
  await rename(page.request, 'Maja Nowak')
  const team = await newTeam(page.request, 'Kuźnia Software')
  const array = (values: string[]) => `ARRAY[${values.map(quote).join(',')}]`
  const t = quote(team.id)
  sql(`
WITH people AS (
  INSERT INTO users (id, email, display_name, email_verified_at, last_active_at)
  SELECT gen_random_uuid(), format('osoba%s@kuznia.test', lpad(i::text, 3, '0')),
         (${array(firstNames)})[1 + i % 20] || ' ' || (${array(lastNames)})[1 + (i * 7) % 25], now(), now()
  FROM generate_series(1, 99) i RETURNING id, email)
INSERT INTO team_members (id, team_id, user_id, role, joined_at)
SELECT gen_random_uuid(), ${t}, id, CASE WHEN email <= 'osoba005@kuznia.test' THEN 'ADMIN' ELSE 'MEMBER' END, now() - interval '120 days' FROM people;

INSERT INTO projects (id, team_id, name, color, archived_at, created_at)
SELECT gen_random_uuid(), ${t}, (${array(areas)})[1 + i % 10] || ' ' || (${array(clients)})[1 + i / 10],
       (${array(colors)})[1 + i % 10], CASE WHEN i % 10 = 9 THEN now() - interval '10 days' END, now() - interval '120 days'
FROM generate_series(0, 199) i;

WITH m AS (SELECT array_agg(tm.user_id ORDER BY u.email) ids FROM team_members tm JOIN users u ON u.id = tm.user_id WHERE tm.team_id = ${t}),
     p AS (SELECT array_agg(id ORDER BY name) ids FROM projects WHERE team_id = ${t}),
     s AS (SELECT i, (now() AT TIME ZONE 'Europe/Warsaw')::date - 1 - (i % 90) AS day, 900 * (1 + i % 16) AS seconds,
                  CASE WHEN i % 3 = 0 THEN 'TIMER' ELSE 'MANUAL' END AS kind FROM generate_series(1, ${entries}) i)
INSERT INTO time_entries (id, user_id, team_id, project_id, entry_type, work_date, started_at, ended_at, duration_seconds, description)
SELECT gen_random_uuid(), m.ids[1 + s.i % 100], ${t}, p.ids[1 + (s.i * 7) % 200], s.kind,
       CASE WHEN s.kind = 'MANUAL' AND s.i % 20 = 1 THEN NULL ELSE s.day END,
       CASE WHEN s.kind = 'TIMER' THEN (s.day + time '08:00' + ((s.i / 100) % 8) * interval '1 hour') AT TIME ZONE 'Europe/Warsaw' END,
       CASE WHEN s.kind = 'TIMER' THEN (s.day + time '08:00' + ((s.i / 100) % 8) * interval '1 hour') AT TIME ZONE 'Europe/Warsaw' + s.seconds * interval '1 second' END,
       s.seconds, (${array(descriptions)})[1 + s.i % 10]
FROM s, m, p;
ANALYZE time_entries;`)

  let datedSeconds = 0, datedEntries = 0, undatedSeconds = 0, undatedEntries = 0
  for (let i = 1; i <= entries; i++) {
    if (largeUndated(i)) { undatedSeconds += largeSeconds(i); undatedEntries++ } else { datedSeconds += largeSeconds(i); datedEntries++ }
  }
  return { team, expected: { from: teamDate(90, WARSAW), to: teamDate(1, WARSAW), datedSeconds, datedEntries, undatedSeconds, undatedEntries, members: 100, projects: 200 } }
}

// ---------- E. Przypadki trudne ----------

const longTeamName = 'Zażółć gęślą jaźń — zespół projektowy o wyjątkowo długiej nazwie, która sprawdza łamanie tekstu w nawigacji, nagłówkach i raportach PDF'
const longProjectName = 'Wdrożenie systemu rezerwacji dla Stowarzyszenia Przyjaciół Ziemi Świętokrzyskiej i Okolicznych Gmin — etap drugi'
const longDescription = ('Szczegółowy opis pracy z polskimi znakami: ąćęłńóśźż ĄĆĘŁŃÓŚŹŻ. ' +
  'Zawiera wiele zdań, aby sprawdzić łamanie wierszy, skracanie w listach i pełny podgląd w szczegółach wpisu. ').repeat(14).slice(0, 2000)

/**
 * Edge cases: long names and descriptions with Polish characters, a project name repeated in an archived team, undated
 * entries, an entry close to the two-year limit, an archived project and team, and Tomasz as ADMIN so a test can take
 * the role away while his browser is open ({@link demote}) or change an entry under an open editor ({@link changeElsewhere}).
 * API failures (401/403/409/429/5xx, lost responses) are simulated per test with `page.route`, not stored as data.
 */
export async function prepareEdgeCases(page: Page, browser: Browser) {
  await login(page)
  await rename(page.request, 'Maja Nowak')
  const maja = await me(page.request)
  const tomaszSession = await signIn(browser, member, memberPassword)
  try {
    await rename(tomaszSession.request, 'Tomasz Kowalczyk-Wiśniewski z bardzo długim nazwiskiem dwuczłonowym')
    const tomasz = await me(tomaszSession.request)
    const team = await newTeam(page.request, longTeamName)
    const long = await newProject(page.request, team, longProjectName, 'PINK', longDescription)
    const plain = await newProject(page.request, team, 'Ćwiczenia', 'SLATE')
    const retired = await newProject(page.request, team, 'Projekt zakończony', 'BROWN')
    await accept(tomaszSession.request, (await invite(page.request, team)).token)
    await setRole(page.request, team, tomasz.id, 'ADMIN')

    const nearLimit = teamDate(700, WARSAW)
    const entries = {
      long: await manual(page.request, team, long, teamDate(1, WARSAW), 5400, longDescription),
      nearLimit: await manual(page.request, team, plain, nearLimit, 3600, 'Wpis sprzed prawie dwóch lat'),
      undatedLong: await manual(page.request, team, long, null, 86_400, 'Zaległa praca z całego dnia, data nieznana'),
      undatedShort: await manual(page.request, team, plain, null, 60, 'Minuta'),
      retired: await manual(page.request, team, retired, teamDate(3, WARSAW), 2700, 'Ostatni wpis przed archiwizacją'),
      tomasz: await manual(tomaszSession.request, team, plain, teamDate(2, WARSAW), 4500, 'Wpis Tomasza do korekty'),
    }
    await create(page.request, `/api/teams/${team.id}/projects/${retired.id}/archive`, {})

    const archivedTeam = await newTeam(page.request, 'Zespół zarchiwizowany')
    const archivedProject = await newProject(page.request, archivedTeam, longProjectName, 'SLATE')
    await manual(page.request, archivedTeam, archivedProject, teamDate(10, WARSAW), 1800, 'Praca w zespole, który później zarchiwizowano')
    await create(page.request, `/api/teams/${archivedTeam.id}/archive`, {})

    return { maja, tomasz, team, archivedTeam, projects: { long, plain, retired, archivedProject }, entries, nearLimit }
  } finally {
    await tomaszSession.close()
  }
}

/** Takes the ADMIN role from Tomasz in another session, e.g. while his browser shows team management. */
export const demote = (owner: Request, team: Team, userId: string) => setRole(owner, team, userId, 'MEMBER')

/** Changes an entry behind an open editor so its next save meets a newer version. */
export function changeElsewhere(entryId: string) {
  sql(`UPDATE time_entries SET description = left(coalesce(description, '') || ' (zmienione w innej sesji)', 2000), version = version + 1, updated_at = now() WHERE id = ${quote(entryId)};`)
}

export { owner, password }
