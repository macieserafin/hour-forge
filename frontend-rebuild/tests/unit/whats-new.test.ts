import { beforeEach, expect, it } from 'vitest'
import { latestReleaseId, markReleasesSeen, readSeenRelease, releases, unseenReleases } from '../../src/lib/whatsNew'

beforeEach(() => window.localStorage.clear())

it('counts everything as new for a person who never opened the page', () => {
  expect(unseenReleases('u1')).toHaveLength(releases.length)
  expect(readSeenRelease('u1')).toBeNull()
})

it('counts only the entries newer than the last seen one', () => {
  window.localStorage.setItem('hourforge:whats-new-seen:u1', releases[1]!.id)
  expect(unseenReleases('u1').map(release => release.id)).toEqual([releases[0]!.id])
  markReleasesSeen('u1')
  expect(readSeenRelease('u1')).toBe(latestReleaseId)
  expect(unseenReleases('u1')).toEqual([])
})

it('treats an unknown remembered id as unseen and keeps people apart', () => {
  window.localStorage.setItem('hourforge:whats-new-seen:u1', 'something-else')
  expect(unseenReleases('u1')).toHaveLength(releases.length)
  markReleasesSeen('u2')
  expect(unseenReleases('u1')).toHaveLength(releases.length)
  expect(unseenReleases('u2')).toEqual([])
})

it('keeps the news newest first with unique ids', () => {
  const ids = releases.map(release => release.id)
  expect(new Set(ids).size).toBe(ids.length)
  expect([...ids].sort().reverse()).toEqual(ids)
})
