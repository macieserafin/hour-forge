import { it, expect } from 'vitest'
import { getDashboardSummary } from '../../src/lib/api'
import { mockApi, json } from '../support/api'

it('encodes the selected team and forwards cancellation with session cookies', async () => {
  const fetcher = mockApi()
  const controller = new AbortController()
  const result = await getDashboardSummary('team /', controller.signal)
  expect(result.teamId).toBe('team /')
  expect(fetcher.mock.calls[0]).toEqual(['/api/dashboard/summary?teamId=team+%2F&weekStart=MONDAY',
    expect.objectContaining({ signal: controller.signal, credentials: 'include', cache: 'no-store' })])
})
it.each([401, 404, 500])('handles summary HTTP %s', async status => {
  mockApi({ 'GET /api/dashboard/summary': () => json({}, status) })
  await expect(getDashboardSummary('team')).rejects.toMatchObject({ status })
})
