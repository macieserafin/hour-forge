import { it, expect } from 'vitest'
import { getTeamMembers, leaveTeam, removeTeamMember } from '../../src/lib/api'
import { json, mockApi } from '../support/api'

it('encodes membership filters and pagination and forwards cancellation and session cookies', async () => {
  const fetcher = mockApi({ 'GET /api/teams/team%20%2F/members': () => json({ content: [], totalElements: 0, totalPages: 0, page: 2, size: 20 }) })
  const controller = new AbortController()
  const result = await getTeamMembers('team /', true, 2, controller.signal)
  const [path, options] = fetcher.mock.calls[0]!
  const url = new URL(String(path), window.location.origin)
  expect(Object.fromEntries(url.searchParams)).toEqual({ includeFormer: 'true', page: '2', size: '20' })
  expect(options).toMatchObject({ credentials: 'include', cache: 'no-store', signal: controller.signal })
  expect(result.page).toBe(2)
})

it.each([
  ['DELETE /api/teams/team%20%2F/members/worker%20%2F', () => removeTeamMember('team /', 'worker /')],
  ['POST /api/teams/team%20%2F/leave', () => leaveTeam('team /')],
] as const)('protects %s using a fresh CSRF token', async (path, action) => {
  const fetcher = mockApi({ [path]: () => json(null, 204) })
  await action()
  expect(fetcher).toHaveBeenCalledTimes(2)
  expect(fetcher.mock.calls[1]![1]).toMatchObject({ credentials: 'include', headers: { 'X-CSRF-TOKEN': 'csrf' } })
})

it.each([400, 401, 403, 404, 409, 500])('maps membership HTTP %s into a readable typed error', async status => {
  mockApi({ 'POST /api/teams/team/leave': () => json({}, status) })
  await expect(leaveTeam('team')).rejects.toMatchObject({ name: 'ApiError', status })
})
