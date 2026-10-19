import { it, expect, vi } from 'vitest'
import { ApiError, getUser, getTeams, getTimeEntryHistory, login, logout, startTimer, stopTimer, changeTimerState,
  createManualTimeEntry, updateTimeEntry, deleteTimeEntry, createTeam, createProject, updateProject, archiveProject, createInvitation, acceptInvitation } from '../../src/lib/api'
import { mockApi, json, team, project, timer, entry, history } from '../support/api'

it('treats an unauthenticated session as signed out', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401) })
  expect(await getUser()).toBeNull()
})
it('normalizes network failures and preserves cancellation', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
  await expect(getTeams()).rejects.toMatchObject({ status: 0 })
  const abort = new DOMException('cancelled', 'AbortError')
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abort))
  await expect(getTeams()).rejects.toBe(abort)
})
it('sends history filters, pagination and cancellation to the server', async () => {
  const fetcher = mockApi()
  const controller = new AbortController()
  await getTimeEntryHistory('team /', { projectId: 'p /', from: '2026-01-01', to: '' }, 2, controller.signal)
  const [path, options] = fetcher.mock.calls[0]!
  const url = new URL(String(path), window.location.origin)
  expect(Object.fromEntries(url.searchParams)).toEqual({ teamId: 'team /', projectId: 'p /', from: '2026-01-01', page: '2', size: '20' })
  expect(options).toMatchObject({ signal: controller.signal, credentials: 'include', cache: 'no-store' })
})
it('logs in using a fresh CSRF token and trimmed email', async () => {
  const fetcher = mockApi({ 'POST /api/auth/login': () => json(null, 204) })
  await login({ email: ' actor@example.test ', password: 'secret' })
  const [, options] = fetcher.mock.calls[1]!
  expect(options?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf' })
  expect(String(options?.body)).toBe('email=actor%40example.test&password=secret')
})
it.each([400, 401, 403, 404, 500])('maps history HTTP %s into a typed error', async status => {
  mockApi({ 'GET /api/time-entries': () => json({}, status) })
  await expect(getTimeEntryHistory(team.id, { projectId: '', from: '', to: '' }, 0)).rejects.toBeInstanceOf(ApiError)
})
const writes = [
  ['POST /api/teams', () => createTeam({ name: 'Team', timezone: 'UTC' }), team],
  ['POST /api/teams/team/projects', () => createProject(team.id, { name: 'Work', description: null }), project],
  ['PUT /api/teams/team/projects/project', () => updateProject(team.id, project.id, { name: 'Work', description: null }), project],
  ['POST /api/teams/team/projects/project/archive', () => archiveProject(team.id, project.id), project],
  ['POST /api/teams/team/invites', () => createInvitation({ teamId: team.id, expiresAt: '2026-12-01T00:00:00Z', maxUses: 1 }), {}],
  ['POST /api/invites/token/accept', () => acceptInvitation('token'), team],
  ['POST /api/timer/start', () => startTimer({ teamId: team.id, projectId: project.id, description: null }), timer],
  ['POST /api/timer/stop', () => stopTimer({ entryId: entry.id, description: null }), timer],
  ['POST /api/timer/pause', () => changeTimerState({ action: 'pause', entryId: entry.id, description: null }), timer],
  ['POST /api/time-entries', () => createManualTimeEntry({ teamId: team.id, projectId: project.id, workDate: entry.workDate, durationSeconds: 60, description: null }), timer],
  ['PATCH /api/time-entries/entry', () => updateTimeEntry({ id: entry.id, projectId: project.id, workDate: entry.workDate, durationSeconds: 60, description: null }), timer],
  ['DELETE /api/time-entries/entry', () => deleteTimeEntry(entry.id), null],
  ['POST /api/auth/logout', () => logout(), null],
] as const
it.each(writes)('protects %s with CSRF and session cookies', async (key, operation, result) => {
  const fetcher = mockApi({ [key]: () => json(result, result === null ? 204 : 200) })
  await operation()
  expect(fetcher).toHaveBeenCalledTimes(2)
  expect(fetcher.mock.calls[1]![1]).toMatchObject({ credentials: 'include', headers: { 'X-CSRF-TOKEN': 'csrf' } })
})
it('returns history response metadata without discarding pagination', async () => {
  mockApi()
  expect(await getTimeEntryHistory(team.id, { projectId: '', from: '', to: '' }, 0)).toEqual(history())
})
