import { beforeEach, expect, it, vi } from 'vitest'
import { EVENT, markSeen, releases, unseenCount } from '../../src/content/releases'

beforeEach(() => window.localStorage.clear())

it('counts everything as new for a person who never opened the page', () => {
  expect(unseenCount('u1')).toBe(releases.length)
})

it('counts only the entries newer than the last seen one', () => {
  window.localStorage.setItem('hourforge:whats-new-seen:u1', releases[1]!.id)
  expect(unseenCount('u1')).toBe(1)
  markSeen('u1')
  expect(unseenCount('u1')).toBe(0)
})

it('treats an unknown remembered id as unseen and keeps people apart', () => {
  window.localStorage.setItem('hourforge:whats-new-seen:u1', 'something-else')
  expect(unseenCount('u1')).toBe(releases.length)
  markSeen('u2')
  expect(unseenCount('u1')).toBe(releases.length)
  expect(unseenCount('u2')).toBe(0)
})

it('tells the open app that the news were seen', () => {
  const listener = vi.fn()
  window.addEventListener(EVENT, listener)
  markSeen('u1')
  window.removeEventListener(EVENT, listener)
  expect(listener).toHaveBeenCalledOnce()
})

it('keeps the news newest first with unique ids', () => {
  const ids = releases.map(release => release.id)
  expect(new Set(ids).size).toBe(ids.length)
  expect([...ids].sort().reverse()).toEqual(ids)
})
