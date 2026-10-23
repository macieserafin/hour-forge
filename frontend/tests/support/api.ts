import { vi } from 'vitest'
import type { DeviceSession, Project, Team, TimeEntry, TimeEntryHistoryItem, User } from '../../src/lib/api'

export const user: User = { id: 'actor', email: 'actor@example.test', displayName: 'Test Actor' }
export const team: Team = { id: 'team', name: 'Studio', timezone: 'Europe/Warsaw', role: 'OWNER', createdAt: '2026-10-19T10:00:00Z', archivedAt: null, activeTimersVisible: true, requireDescription: false, requireTagOrIssue: false, weekStart: 'MONDAY', weeklyReport: false }
export const project: Project = { id: 'project', teamId: team.id, name: 'Website', description: null, createdAt: team.createdAt, archivedAt: null }
export const entry: TimeEntryHistoryItem = { id: 'entry', teamId: team.id, projectId: project.id, projectName: project.name,
  entryType: 'MANUAL', workDate: '2026-10-19', durationSeconds: 900, description: 'Original work', startedAt: null, endedAt: null }
export const timer: TimeEntry = { ...entry, userId: user.id, entryType: 'TIMER', startedAt: '2026-10-19T10:00:00Z', endedAt: null,
  durationSeconds: null, createdAt: team.createdAt, updatedAt: team.createdAt, pausedAt: null, pausedDurationSeconds: 0 }
export const deviceSession: DeviceSession = { id: 'this-device', browser: 'Firefox', system: 'Windows', address: '83.24.x.x',
  signedInAt: '2026-10-19T08:00:00Z', lastActiveAt: '2026-10-19T10:00:00Z', current: true, remembered: false }
export const history = (content = [entry]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })
export const json = (data: unknown, status = 200) => new Response(status === 204 ? null : JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } })
type Handler = (url: URL, options: RequestInit) => Response | Promise<Response>

export function mockApi(overrides: Record<string, Handler> = {}) {
  const handlers: Record<string, Handler> = {
    'GET /api/me': () => json(user),
    'GET /api/auth/csrf': () => json({ token: 'csrf', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' }),
    'GET /api/teams': () => json([team]),
    [`GET /api/teams/${team.id}/members`]: () => json({ content: [{ userId: user.id, displayName: user.displayName,
      role: team.role, joinedAt: team.createdAt, leftAt: null }], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    [`GET /api/teams/${team.id}/invites`]: () => json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    [`GET /api/teams/${team.id}/templates`]: () => json([]),
    [`GET /api/teams/${team.id}/tags`]: () => json([]),
    [`GET /api/teams/${team.id}/projects`]: () => json([project]),
    [`GET /api/teams/${team.id}/projects/${project.id}`]: () => json(project),
    'GET /api/integrations/jira/status': () => json({ available: false, siteConnected: false, siteName: null, siteUrl: null, userConnected: false, canManage: false }),
    'GET /api/account/preferences': () => json({ defaultTeamId: null, startPage: 'DASHBOARD', timeFormat: 'CLOCK', weekStart: 'MONDAY', longTimerEmail: true,
      browserNotifications: false, longTimerHours: 10, titleTimer: true }),
    'GET /api/account/sessions': () => json([deviceSession]),
    'GET /api/account/logins': () => json({ entries: [], locationSource: null }),
    'GET /api/dashboard/overview': () => json({ todaySeconds: 900, weekSeconds: 3600, teams: [{ teamId: 'team', teamName: 'Studio', archived: false, todaySeconds: 900, weekSeconds: 3600 }] }),
    'GET /api/dashboard/activity': () => json({ from: '2026-08-03', to: '2026-10-25', today: '2026-10-19', maxSeconds: 0, days: Array.from({ length: 84 }, (_, index) => ({ workDate: new Date(Date.UTC(2026, 7, 3 + index)).toISOString().slice(0, 10), seconds: 0 })) }),
    'GET /api/timer/active': () => json(null, 204),
    'GET /api/time-entries': () => json(history()),
    'GET /api/dashboard/summary': url => json({ teamId: url.searchParams.get('teamId'), timezone: 'Europe/Warsaw',
      today: '2026-10-19', weekStart: '2026-10-19', weekEnd: '2026-10-25', todaySeconds: 900, weekSeconds: 3600,
      days: ['19', '20', '21', '22', '23', '24', '25'].map((day, index) => ({ date: `2026-10-${day}`, seconds: index === 0 ? 900 : index === 2 ? 2700 : 0 })) }),
    ...overrides,
  }
  const fetcher = vi.fn(async (input: string | URL | Request, options: RequestInit = {}) => {
    const url = new URL(String(input), window.location.origin)
    const key = `${options.method ?? 'GET'} ${url.pathname}`
    const handler = handlers[key]
    if (!handler) throw new Error(`Unexpected test HTTP request: ${key}`)
    return handler(url, options)
  })
  vi.stubGlobal('fetch', fetcher)
  return fetcher
}
