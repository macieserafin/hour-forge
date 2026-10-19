import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PersonalDashboard } from '../../src/pages/PersonalDashboard'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'
import { reportBucket, reportEntry, reportHandlers, reportMember, reportPage, reportProject } from '../support/reports'

const base = 'GET /api/teams/team/reports'
const ownerView = () => renderApp(<OwnerDashboard userId="actor" team={team} />)

it('opens the owner report from the personal dashboard and shows read-only saved work', async () => {
  mockApi(reportHandlers())
  renderApp(<PersonalDashboard userId="actor" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Zespół' }))
  const total = await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  expect(total).toHaveTextContent('00:15:00')
  expect(screen.getByRole('region', { name: 'Liczba wpisów zespołu' })).toHaveTextContent('1')
  expect(await screen.findByRole('region', { name: 'Historia wpisów zespołu' })).toHaveTextContent('Original work')
  expect(screen.getByRole('option', { name: 'Anna Kowalska (były członek)' })).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Website (archiwalny)' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Edytuj' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Usuń' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Mój czas' }))
  expect(await screen.findByRole('region', { name: 'Czas dzisiaj' })).toBeVisible()
  expect(screen.queryByRole('region', { name: 'Raport zespołu' })).not.toBeInTheDocument()
})

it('never fetches reports for a member even if the team view is requested in the URL', async () => {
  const fetcher = mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]) })
  renderApp(<PersonalDashboard userId="actor" />, '/dashboard?team=team&view=team')
  expect(await screen.findByRole('region', { name: 'Czas dzisiaj' })).toBeVisible()
  expect(screen.queryByRole('group', { name: 'Widok pulpitu' })).not.toBeInTheDocument()
  expect(fetcher.mock.calls.every(([path]) => !String(path).includes('/reports/'))).toBe(true)
})

it('applies the same filters to totals, both breakdowns and entries only after submission', async () => {
  const fetcher = mockApi(reportHandlers())
  ownerView()
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  await userEvent.clear(screen.getByLabelText('Data od'))
  await userEvent.type(screen.getByLabelText('Data od'), '2026-01-01')
  await userEvent.clear(screen.getByLabelText('Data do'))
  await userEvent.type(screen.getByLabelText('Data do'), '2026-01-31')
  await userEvent.selectOptions(screen.getByLabelText('Osoba'), reportMember.userId)
  await userEvent.selectOptions(screen.getByLabelText('Projekt w raporcie'), reportProject.projectId)
  await userEvent.selectOptions(screen.getByLabelText('Rodzaj wpisu'), 'MANUAL')
  expect(fetcher.mock.calls.some(([path]) => String(path).includes('userIds='))).toBe(false)
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  await waitFor(() => {
    const calls = fetcher.mock.calls.map(([path]) => new URL(String(path), window.location.origin)).filter(url => url.searchParams.has('userIds'))
    expect(calls).toHaveLength(4)
    for (const url of calls) {
      expect(Object.fromEntries(url.searchParams)).toMatchObject({ from: '2026-01-01', to: '2026-01-31', userIds: reportMember.userId, projectIds: reportProject.projectId, entryType: 'MANUAL' })
    }
  })
  await userEvent.click(screen.getByRole('button', { name: 'Wyczyść filtry' }))
  expect(screen.getByLabelText('Osoba')).toHaveValue('')
  expect(screen.getByLabelText('Projekt w raporcie')).toHaveValue('')
  expect(screen.getByLabelText('Rodzaj wpisu')).toHaveValue('')
})

it('rejects reversed dates without requesting an invalid report', async () => {
  const fetcher = mockApi(reportHandlers())
  ownerView()
  await screen.findByRole('region', { name: 'Łączny czas zespołu' })
  await userEvent.clear(screen.getByLabelText('Data od'))
  await userEvent.type(screen.getByLabelText('Data od'), '2027-01-01')
  await userEvent.clear(screen.getByLabelText('Data do'))
  await userEvent.type(screen.getByLabelText('Data do'), '2026-01-01')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Data „od” nie może być późniejsza')
  expect(fetcher.mock.calls.some(([path]) => String(path).includes('from=2027'))).toBe(false)
})

it('paginates each table independently and resets entry pagination when sorting changes', async () => {
  const fetcher = mockApi({ ...reportHandlers(),
    [`${base}/entries`]: url => json(reportPage([{ ...reportEntry, description: `Page ${url.searchParams.get('page')}` }], Number(url.searchParams.get('page')), true)),
    [`${base}/breakdown`]: url => json({ ...reportPage([{ ...reportBucket, userId: 'worker', displayName: `Person page ${url.searchParams.get('page')}`, projectId: 'project', projectName: 'Website' }], Number(url.searchParams.get('page')), true), groupBy: url.searchParams.get('groupBy') }),
  })
  ownerView()
  const people = await screen.findByRole('region', { name: 'Czas według osób' })
  await userEvent.click(await within(people).findByRole('button', { name: 'Następna' }))
  expect(await within(people).findByText('Person page 1')).toBeVisible()
  expect(within(screen.getByRole('region', { name: 'Czas według projektów' })).getByText('Strona 1')).toBeVisible()
  const history = screen.getByRole('region', { name: 'Wpisy zespołu' })
  await userEvent.click(within(history).getByRole('button', { name: 'Następna' }))
  expect(await within(history).findByText('Page 1')).toBeVisible()
  await userEvent.selectOptions(screen.getByLabelText('Kolejność wpisów'), 'DURATION:ASC')
  expect(await within(history).findByText('Page 0')).toBeVisible()
  expect(fetcher.mock.calls.some(([path]) => String(path).includes('sort=DURATION&direction=ASC'))).toBe(true)
  expect(within(people).getByText('Strona 2')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  expect(await within(people).findByText('Strona 1')).toBeVisible()
})

it('loads subsequent filter-option pages without discarding previous people and projects', async () => {
  mockApi({ ...reportHandlers(),
    [`${base}/members`]: url => json(reportPage([{ ...reportMember, userId: `person-${url.searchParams.get('page')}`, displayName: `Person ${url.searchParams.get('page')}` }], Number(url.searchParams.get('page')), url.searchParams.get('page') === '0')),
    [`${base}/projects`]: url => json(reportPage([{ ...reportProject, projectId: `project-${url.searchParams.get('page')}`, name: `Project ${url.searchParams.get('page')}` }], Number(url.searchParams.get('page')), url.searchParams.get('page') === '0')),
  })
  ownerView()
  await userEvent.click(await screen.findByRole('button', { name: 'Wczytaj kolejne osoby' }))
  expect(await screen.findByRole('option', { name: 'Person 1 (były członek)' })).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Person 0 (były członek)' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Wczytaj kolejne projekty' }))
  expect(await screen.findByRole('option', { name: 'Project 1 (archiwalny)' })).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Project 0 (archiwalny)' })).toBeInTheDocument()
})

it.each([
  [401, 'Sesja wygasła'],
  [403, 'wyłącznie dla jego właściciela'],
  [404, 'Zespół lub wybrane dane są niedostępne'],
])('hides all cached report data when a background refresh returns HTTP %s', async (status, message) => {
  let denied = false
  mockApi({ ...reportHandlers(), [`${base}/entries`]: () => denied ? json({}, status as number) : json(reportPage([reportEntry])) })
  const { client } = ownerView()
  expect(await screen.findByText('Original work')).toBeVisible()
  denied = true
  await userEvent.click(screen.getByRole('button', { name: 'Odśwież raport' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(message as string)
  expect(screen.queryByText('Original work')).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Łączny czas zespołu' })).not.toBeInTheDocument()
  expect(screen.queryByRole('option', { name: /Anna Kowalska/ })).not.toBeInTheDocument()
  if (status === 401) expect(client.getQueryData(['session'])).toBeNull()
})

it('returns to personal time when the refreshed team membership no longer has the owner role', async () => {
  let demoted = false
  mockApi({ ...reportHandlers(), 'GET /api/teams': () => json([{ ...team, role: demoted ? 'MEMBER' : 'OWNER' }]),
    [`${base}/summary`]: url => demoted ? json({}, 403) : reportHandlers()[`${base}/summary`]!(url) })
  renderApp(<PersonalDashboard userId="actor" />, '/dashboard?team=team&view=team')
  expect(await screen.findByRole('region', { name: 'Łączny czas zespołu' })).toBeVisible()
  demoted = true
  await userEvent.click(screen.getByRole('button', { name: 'Odśwież raport' }))
  expect(await screen.findByRole('region', { name: 'Czas dzisiaj' })).toBeVisible()
  expect(screen.queryByRole('group', { name: 'Widok pulpitu' })).not.toBeInTheDocument()
  expect(screen.queryByText('Original work')).not.toBeInTheDocument()
})

it('shows empty results and lets a failed report be retried', async () => {
  let failed = true
  mockApi({ ...reportHandlers(), [`${base}/entries`]: () => failed ? json({}, 400) : json(reportPage([])) })
  ownerView()
  expect(await screen.findByRole('alert')).toHaveTextContent('Sprawdź zakres dat')
  failed = false
  await userEvent.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }))
  expect(await screen.findByText('Brak wpisów dla wybranych filtrów.')).toBeVisible()
})

it('resets filters and report pages when switching teams without showing the previous team data', async () => {
  const other = { ...team, id: 'other', name: 'Other team' }
  mockApi({ ...reportHandlers(), ...reportHandlers(other), 'GET /api/teams': () => json([team, other]),
    'GET /api/teams/other/reports/entries': () => json(reportPage([{ ...reportEntry, description: 'Other team work' }])) })
  renderApp(<PersonalDashboard userId="actor" />, '/dashboard?team=team&view=team')
  expect(await screen.findByText('Original work')).toBeVisible()
  await userEvent.selectOptions(screen.getByLabelText('Osoba'), reportMember.userId)
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  await userEvent.selectOptions(screen.getByLabelText('Zespół na pulpicie'), other.id)
  expect(await screen.findByText('Other team work')).toBeVisible()
  expect(screen.queryByText('Original work')).not.toBeInTheDocument()
  expect(screen.getByLabelText('Osoba')).toHaveValue('')
})
