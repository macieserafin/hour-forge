import { expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import type { EntryTemplate } from '../../src/lib/api'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { addDays, teamToday } from '../../src/lib/time'
import { renderApp } from '../support/render'
import { entry, history, json, mockApi, project, team } from '../support/api'
import { reportEntry, reportHandlers, reportPage } from '../support/reports'

const today = teamToday(team.timezone)
const yesterday = addDays(today, -1)
const second = { ...project, id: 'second', name: 'Second project' }
const template: EntryTemplate = { id: 'tpl', name: 'Standup', projectId: 'second', description: 'Daily standup', jiraIssueId: null, jiraIssueKey: null }

it('marks an edited entry in the history and in the team report', async () => {
  mockApi({ ...reportHandlers(), 'GET /api/time-entries': () => json(history([{ ...entry, editedAt: '2026-10-20T08:30:00Z' }, { ...entry, id: 'plain', description: 'Untouched' }])),
    [`GET /api/teams/team/reports/entries`]: () => json(reportPage([{ ...reportEntry, editedAt: '2026-10-20T08:30:00Z' }])) })
  const view = renderApp(<App />, '/time?team=team')
  expect(await screen.findAllByText('edytowano')).toHaveLength(1)
  view.unmount()
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  expect(await screen.findByText('edytowano')).toBeVisible()
})

it('copies an earlier entry into a new manual entry for today', async () => {
  let posted: Record<string, unknown> = {}
  mockApi({
    'GET /api/time-entries': () => json(history([{ ...entry, durationSeconds: 900, description: 'Original work', jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' }])),
    'POST /api/time-entries': (_url, options) => { posted = JSON.parse(String(options.body)); return json({ ...entry, userId: 'actor', entryType: 'MANUAL', createdAt: '', updatedAt: '', pausedAt: null, pausedDurationSeconds: 0 }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: /Kopiuj wpis: Website/ }))
  const modal = within(screen.getByRole('dialog', { name: 'Kopiuj wpis' }))
  expect(modal.getByLabelText('Projekt')).toHaveValue('project')
  expect(modal.getByLabelText('Minuty')).toHaveValue(15)
  expect(modal.getByLabelText('Opis pracy', { exact: false })).toHaveValue('Original work')
  expect(modal.getByLabelText('Data pracy')).toHaveValue(today)
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  await waitFor(() => expect(posted.durationSeconds).toBe(900))
  expect(posted).toMatchObject({ teamId: 'team', projectId: 'project', workDate: today, description: 'Original work', jiraIssueId: '1001' })
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})

it('offers no copying or repeating in an archived team', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, archivedAt: '2026-10-19T12:00:00Z' }]) })
  renderApp(<App />, '/time?team=team')
  await screen.findByText(/jest zarchiwizowany/)
  expect(screen.queryByRole('button', { name: 'Powtórz wczorajszy dzień' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Kopiuj wpis/ })).not.toBeInTheDocument()
})

it('repeats yesterday after showing what will be copied', async () => {
  let posted: Record<string, unknown> | undefined
  mockApi({
    'GET /api/time-entries': url => json(history(url.searchParams.get('from') === yesterday ? [{ ...entry, id: 'a', durationSeconds: 3600, description: 'Morning' }, { ...entry, id: 'b', durationSeconds: 1800, description: 'Afternoon' }] : [])),
    'POST /api/time-entries/copy-day': (_url, options) => { posted = JSON.parse(String(options.body)); return json({ copied: 2, totalSeconds: 5400 }) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Powtórz wczorajszy dzień' }))
  const modal = within(screen.getByRole('dialog', { name: 'Powtórz wczorajszy dzień' }))
  expect(await modal.findByText(/Morning/)).toBeVisible()
  expect(modal.getByText(/Afternoon/)).toBeVisible()
  expect(modal.getByText(/Łącznie: 01:30:00/)).toBeVisible()
  expect(posted).toBeUndefined()
  await userEvent.click(modal.getByRole('button', { name: 'Skopiuj wpisy' }))
  expect(await modal.findByRole('status')).toHaveTextContent('Skopiowano wpisy: 2')
  expect(posted).toEqual({ teamId: 'team', fromDate: yesterday, toDate: today, requestId: expect.stringMatching(/^[0-9a-f-]{36}$/) })
  expect(modal.queryByRole('button', { name: 'Skopiuj wpisy' })).not.toBeInTheDocument()
})

it('repeats a lost request with the same id, so the day is not copied twice, and a new attempt gets a new id', async () => {
  const posted: Array<Record<string, unknown>> = []
  let lostResponse = true
  mockApi({
    'GET /api/time-entries': url => json(history(url.searchParams.get('from') === yesterday ? [{ ...entry, id: 'a', durationSeconds: 3600, description: 'Morning' }] : [])),
    'POST /api/time-entries/copy-day': (_url, options) => {
      posted.push(JSON.parse(String(options.body)))
      if (lostResponse) { lostResponse = false; throw new TypeError('network down') }
      return json({ copied: 1, totalSeconds: 3600 })
    },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Powtórz wczorajszy dzień' }))
  const modal = within(screen.getByRole('dialog', { name: 'Powtórz wczorajszy dzień' }))
  await userEvent.click(await modal.findByRole('button', { name: 'Skopiuj wpisy' }))
  await modal.findByRole('alert')
  await userEvent.click(modal.getByRole('button', { name: 'Skopiuj wpisy' }))
  await modal.findByRole('status')
  expect(posted).toHaveLength(2)
  expect(posted[1]!.requestId).toBe(posted[0]!.requestId)

  await userEvent.click(modal.getByRole('button', { name: 'Zamknij' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Powtórz wczorajszy dzień' }))
  await userEvent.click(await within(screen.getByRole('dialog')).findByRole('button', { name: 'Skopiuj wpisy' }))
  await waitFor(() => expect(posted).toHaveLength(3))
  expect(posted[2]!.requestId).not.toBe(posted[0]!.requestId)
})

it('says there is nothing to repeat when yesterday is empty and explains a refusal', async () => {
  mockApi({ 'GET /api/time-entries': () => json(history([])) })
  const view = renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Powtórz wczorajszy dzień' }))
  const modal = within(screen.getByRole('dialog', { name: 'Powtórz wczorajszy dzień' }))
  expect(await modal.findByText(/nie masz zapisanych wpisów/)).toBeVisible()
  expect(modal.queryByRole('button', { name: 'Skopiuj wpisy' })).not.toBeInTheDocument()
  view.unmount()

  mockApi({ 'GET /api/time-entries': () => json(history([entry])), 'POST /api/time-entries/copy-day': () => json({}, 404) })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Powtórz wczorajszy dzień' }))
  await userEvent.click(await within(screen.getByRole('dialog')).findByRole('button', { name: 'Skopiuj wpisy' }))
  expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('Nie ma wpisów do skopiowania')
})

it('fills the timer form from a template and starts the timer with it', async () => {
  let started: Record<string, unknown> = {}
  mockApi({
    'GET /api/teams/team/projects': () => json([project, second]),
    'GET /api/teams/team/templates': () => json([template]),
    'POST /api/timer/start': (_url, options) => { started = JSON.parse(String(options.body)); return json({ id: 't', userId: 'actor', teamId: 'team', projectId: 'second', entryType: 'TIMER', workDate: today,
      startedAt: new Date().toISOString(), endedAt: null, durationSeconds: null, description: 'Daily standup', createdAt: '', updatedAt: new Date().toISOString(), pausedAt: null, pausedDurationSeconds: 0 }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await screen.findByRole('option', { name: 'Standup' })
  await userEvent.selectOptions(screen.getByLabelText('Szablon', { exact: false }), 'tpl')
  expect(screen.getByLabelText('Projekt', { selector: '#time-project' })).toHaveValue('second')
  expect(screen.getByLabelText('Opis pracy', { selector: '#time-description', exact: false })).toHaveValue('Daily standup')
  await userEvent.click(screen.getByRole('button', { name: 'Uruchom timer' }))
  await waitFor(() => expect(started).toMatchObject({ projectId: 'second', description: 'Daily standup' }))
})

it('saves the current form as a template and deletes a template', async () => {
  let templates = [template]
  let created: Record<string, unknown> = {}
  let deleted = ''
  mockApi({
    'GET /api/teams/team/projects': () => json([project, second]),
    'GET /api/teams/team/templates': () => json(templates),
    'POST /api/teams/team/templates': (_url, options) => { created = JSON.parse(String(options.body)); const made = { ...template, id: 'new', name: String(created.name), projectId: String(created.projectId), description: created.description as string | null }; templates = [...templates, made]; return json(made, 201) },
    'DELETE /api/teams/team/templates/new': () => { deleted = 'new'; templates = templates.filter(item => item.id !== 'new'); return json(null, 204) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.selectOptions(await screen.findByLabelText('Projekt', { selector: '#time-project' }), 'project')
  await userEvent.type(screen.getByLabelText('Opis pracy', { selector: '#time-description', exact: false }), 'Code review')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz bieżące jako szablon' }))
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz szablon' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Wpisz nazwę szablonu')
  await userEvent.type(screen.getByLabelText('Nazwa szablonu'), 'Reviews')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz szablon' }))
  await waitFor(() => expect(created).toEqual({ name: 'Reviews', projectId: 'project', description: 'Code review', jiraIssueId: null, tagId: null }))
  expect(await screen.findByRole('button', { name: 'Usuń szablon „Reviews”' })).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Usuń szablon „Reviews”' }))
  await waitFor(() => expect(deleted).toBe('new'))
})

const reviewTag = { id: 'review', teamId: 'team', name: 'Przegląd kodu', color: 'TEAL', archivedAt: null }

it('sets the tag of a template on the form and starts the timer with it', async () => {
  let started: Record<string, unknown> = {}
  mockApi({
    'GET /api/teams/team/projects': () => json([project, second]),
    'GET /api/teams/team/tags': () => json([reviewTag]),
    'GET /api/teams/team/templates': () => json([{ ...template, tagId: 'review', tagName: 'Przegląd kodu', tagArchived: false }]),
    'POST /api/timer/start': (_url, options) => { started = JSON.parse(String(options.body)); return json({ id: 't', userId: 'actor', teamId: 'team', projectId: 'second', entryType: 'TIMER', workDate: today,
      startedAt: new Date().toISOString(), endedAt: null, durationSeconds: null, description: 'Daily standup', createdAt: '', updatedAt: new Date().toISOString(), pausedAt: null, pausedDurationSeconds: 0 }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await screen.findByRole('option', { name: 'Standup' })
  await userEvent.selectOptions(screen.getByLabelText('Szablon', { exact: false }), 'tpl')
  expect(screen.getByLabelText('Tag', { exact: false })).toHaveValue('review')
  await userEvent.click(screen.getByRole('button', { name: 'Uruchom timer' }))
  await waitFor(() => expect(started).toMatchObject({ projectId: 'second', tagId: 'review' }))
})

it('does not put an archived tag of a template on the form', async () => {
  mockApi({
    'GET /api/teams/team/projects': () => json([project, second]),
    'GET /api/teams/team/tags': () => json([reviewTag]),
    'GET /api/teams/team/templates': () => json([{ ...template, tagId: 'gone', tagName: 'Stary', tagArchived: true }]),
  })
  renderApp(<App />, '/time?team=team')
  await screen.findByRole('option', { name: 'Standup' })
  await userEvent.selectOptions(screen.getByLabelText('Szablon', { exact: false }), 'tpl')
  expect(screen.getByLabelText('Tag', { exact: false })).toHaveValue('')
})

it('saves the chosen tag in a new template', async () => {
  let created: Record<string, unknown> = {}
  mockApi({
    'GET /api/teams/team/projects': () => json([project, second]),
    'GET /api/teams/team/tags': () => json([reviewTag]),
    'GET /api/teams/team/templates': () => json([]),
    'POST /api/teams/team/templates': (_url, options) => { created = JSON.parse(String(options.body)); return json({ ...template, id: 'new', ...created }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.selectOptions(await screen.findByLabelText('Projekt', { selector: '#time-project' }), 'project')
  await userEvent.selectOptions(await screen.findByLabelText('Tag', { exact: false }), 'review')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz bieżące jako szablon' }))
  await userEvent.type(screen.getByLabelText('Nazwa szablonu'), 'Reviews')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz szablon' }))
  await waitFor(() => expect(created).toMatchObject({ name: 'Reviews', projectId: 'project', tagId: 'review' }))
})

it('explains a template name that is already taken', async () => {
  mockApi({ 'GET /api/teams/team/templates': () => json([template]), 'POST /api/teams/team/templates': () => json({}, 409) })
  renderApp(<App />, '/time?team=team')
  await userEvent.selectOptions(await screen.findByLabelText('Projekt', { selector: '#time-project' }), 'project')
  await userEvent.click(await screen.findByRole('button', { name: 'Zapisz bieżące jako szablon' }))
  await userEvent.type(screen.getByLabelText('Nazwa szablonu'), 'Standup')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz szablon' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Szablon o takiej nazwie już istnieje')
})
