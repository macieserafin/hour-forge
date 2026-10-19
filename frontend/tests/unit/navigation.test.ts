import { describe, it, expect } from 'vitest'
import { safeReturnTo, loginUrl } from '../../src/lib/navigation'

describe('safe return navigation', () => {
  it.each([null, '', 'https://evil.test/time', '//evil.test/time', '/login', '/settings', '/invite/../login', 'javascript:alert(1)'])
    ('rejects unsafe or unsupported return address %s', value => { expect(safeReturnTo(value)).toBe('/dashboard') })
  it.each(['/dashboard', '/time?team=abc', '/teams', '/projects?team=abc', '/invite/abc_DEF-123'])
    ('preserves supported local destination %s', value => { expect(safeReturnTo(value)).toBe(value) })
  it('encodes the full return address as a single parameter', () => {
    expect(loginUrl('/time?team=abc&project=p')).toBe('/login?returnTo=%2Ftime%3Fteam%3Dabc%26project%3Dp')
  })
})
