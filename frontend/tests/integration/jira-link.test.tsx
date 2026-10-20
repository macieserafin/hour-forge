import { expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { TimeEntryHistory } from '../../src/pages/TimeEntryHistory'
import { renderApp } from '../support/render'
import { entry, history, json, mockApi } from '../support/api'

const connected = { available: true, siteConnected: true, siteName: 'Studio', siteUrl: 'https://studio.atlassian.net', userConnected: true, canManage: false }
const issue = (n: number) => ({ id: String(1000 + n), key: `PROJ-${n}`, summary: `Task number ${n}`, status: 'To Do', issueType: 'Task', projectKey: 'PROJ' })
const issues = () => json({ issues: [issue(1), issue(2)], nextPageToken: null })

it('links a manual entry to the chosen Jira issue', async () => {
  let posted: Record<string, unknown> = {}
  mockApi({
    'GET /api/integrations/jira/status': () => json(connected),
    'GET /api/integrations/jira/issues': issues,
    'POST /api/time-entries': (_url, options) => { posted = JSON.parse(String(options.body)); return json({ ...entry, userId: 'actor', entryType: 'MANUAL', createdAt: '', updatedAt: '', pausedAt: null, pausedDurationSeconds: 0 }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.selectOptions(await modal.findByLabelText('Projekt'), 'project')
  await userEvent.type(modal.getByLabelText('Minuty'), '15')
  await userEvent.click(await modal.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  await userEvent.click(await modal.findByRole('button', { name: /PROJ-2 Task number 2/ }))
  expect(modal.getByText('PROJ-2')).toBeVisible()
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  await waitFor(() => expect(posted.jiraIssueId).toBe('1002'))
})

it('sends no link when none was chosen', async () => {
  let posted: Record<string, unknown> = {}
  mockApi({
    'GET /api/integrations/jira/status': () => json(connected),
    'POST /api/time-entries': (_url, options) => { posted = JSON.parse(String(options.body)); return json({ ...entry, userId: 'actor', entryType: 'MANUAL', createdAt: '', updatedAt: '', pausedAt: null, pausedDurationSeconds: 0 }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.selectOptions(await modal.findByLabelText('Projekt'), 'project')
  await userEvent.type(modal.getByLabelText('Minuty'), '5')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  await waitFor(() => expect(posted.durationSeconds).toBe(300))
  expect(posted.jiraIssueId).toBeNull()
})

it('starts a timer with the chosen issue', async () => {
  let started: Record<string, unknown> = {}
  mockApi({
    'GET /api/integrations/jira/status': () => json(connected),
    'GET /api/integrations/jira/issues': issues,
    'POST /api/timer/start': (_url, options) => { started = JSON.parse(String(options.body)); return json({ id: 't', userId: 'actor', teamId: 'team', projectId: 'project', entryType: 'TIMER', workDate: '2026-10-19',
      startedAt: '2026-10-19T10:00:00Z', endedAt: null, durationSeconds: null, description: null, createdAt: '', updatedAt: '', pausedAt: null, pausedDurationSeconds: 0 }, 201) },
  })
  renderApp(<App />, '/time?team=team')
  await userEvent.selectOptions(await screen.findByLabelText('Projekt'), 'project')
  await userEvent.click(await screen.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  await userEvent.click(await screen.findByRole('button', { name: /PROJ-1 Task number 1/ }))
  await userEvent.click(screen.getByRole('button', { name: 'Uruchom timer' }))
  await waitFor(() => expect(started.jiraIssueId).toBe('1001'))
})

it('shows the key of a linked entry as a link to the issue and keeps the link when editing', async () => {
  const linked = { ...entry, jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' }
  let patched: Record<string, unknown> = {}
  mockApi({
    'GET /api/integrations/jira/status': () => json(connected),
    'GET /api/time-entries': () => json(history([linked])),
    'PATCH /api/time-entries/entry': (_url, options) => { patched = JSON.parse(String(options.body)); return json(linked) },
  })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  const link = await screen.findByRole('link', { name: /PROJ-1/ })
  expect(link).toHaveAttribute('href', 'https://studio.atlassian.net/browse/PROJ-1')
  expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  await userEvent.click(screen.getByRole('button', { name: 'Edytuj' }))
  const modal = within(screen.getByRole('dialog'))
  expect(await modal.findByText('PROJ-1')).toBeVisible()
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await waitFor(() => expect(patched.jiraIssueId).toBe('1001'))
})

it('removes the link when the person removes it in the edit form', async () => {
  const linked = { ...entry, jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' }
  let patched: Record<string, unknown> = {}
  mockApi({
    'GET /api/integrations/jira/status': () => json(connected),
    'GET /api/time-entries': () => json(history([linked])),
    'PATCH /api/time-entries/entry': (_url, options) => { patched = JSON.parse(String(options.body)); return json(entry) },
  })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Edytuj' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.click(await modal.findByRole('button', { name: 'Usuń powiązanie z zadaniem PROJ-1' }))
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await waitFor(() => expect(patched).toHaveProperty('jiraIssueId', null))
})

it('keeps a linked entry editable when Jira is switched off, and shows the key as plain text', async () => {
  const linked = { ...entry, jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' }
  let patched: Record<string, unknown> = {}
  mockApi({
    'GET /api/time-entries': () => json(history([linked])),
    'PATCH /api/time-entries/entry': (_url, options) => { patched = JSON.parse(String(options.body)); return json(linked) },
  })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  expect(await screen.findByText('PROJ-1')).toBeVisible()
  expect(screen.queryByRole('link', { name: /PROJ-1/ })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Edytuj' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Zapisz zmiany' }))
  await waitFor(() => expect(patched.jiraIssueId).toBe('1001'))
})

it('never turns an unsafe site address into a link', async () => {
  const linked = { ...entry, jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' }
  mockApi({
    'GET /api/integrations/jira/status': () => json({ ...connected, siteUrl: 'javascript:alert(1)' }),
    'GET /api/time-entries': () => json(history([linked])),
  })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  expect(await screen.findByText('PROJ-1')).toBeVisible()
  expect(screen.queryByRole('link', { name: /PROJ-1/ })).not.toBeInTheDocument()
})

it('shows the Jira key of an entry in the team report', async () => {
  const { OwnerDashboard } = await import('../../src/pages/OwnerDashboard')
  const { reportEntry, reportHandlers, reportPage } = await import('../support/reports')
  const { team } = await import('../support/api')
  mockApi({
    ...reportHandlers(),
    'GET /api/integrations/jira/status': () => json(connected),
    'GET /api/teams/team/reports/entries': () => json(reportPage([{ ...reportEntry, jiraIssueKey: 'PROJ-7' }])),
  })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const link = await screen.findByRole('link', { name: /PROJ-7/ })
  expect(link).toHaveAttribute('href', 'https://studio.atlassian.net/browse/PROJ-7')
})
