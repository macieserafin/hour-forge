import { describe, it, expect, vi, afterEach } from 'vitest'
import { googleNavigation, googleSignInUrl, safeReturnTo, loginUrl } from '../../src/lib/navigation'

describe('safe return navigation', () => {
  it.each([null, '', 'https://evil.test/time', '//evil.test/time', '/login', '/register', '/register?returnTo=/login', '/settings', '/invite/../login', 'javascript:alert(1)', '/app', '/app/../login', '/app/a/b/c/d/e'])
    ('rejects unsafe or unsupported return address %s', value => { expect(safeReturnTo(value)).toBe('/app/work') })
  it.each(['/dashboard', '/time?team=abc', '/teams', '/projects?team=abc', '/integrations?team=abc&jira=connected', '/invite/abc_DEF-123', '/account', '/operator', '/app/work?view=week', '/app/teams/abc/reports'])
    ('preserves supported local destination %s', value => { expect(safeReturnTo(value)).toBe(value) })
  it('encodes the full return address as a single parameter', () => {
    expect(loginUrl('/time?team=abc&project=p')).toBe('/login?returnTo=%2Ftime%3Fteam%3Dabc%26project%3Dp')
  })
})

describe('Google sign-in address', () => {
  afterEach(() => vi.restoreAllMocks())
  it('starts with only the mode by default', () => {
    expect(googleSignInUrl()).toBe('/api/auth/oauth2/authorization/google?mode=login')
  })
  it('adds "remember me" and an encoded safe return address', () => {
    expect(googleSignInUrl({ rememberMe: true, returnTo: '/time?team=abc&project=p' }))
      .toBe('/api/auth/oauth2/authorization/google?mode=login&rememberMe=true&returnTo=%2Ftime%3Fteam%3Dabc%26project%3Dp')
  })
  it.each(['https://evil.test/time', '//evil.test/time', '/login', 'javascript:alert(1)'])
    ('replaces the unsafe return address %s with My work', value => {
      expect(googleSignInUrl({ returnTo: value })).toBe('/api/auth/oauth2/authorization/google?mode=login&returnTo=%2Fapp%2Fwork')
    })
  it.each(['https://evil.test/api/auth/oauth2/authorization/google?mode=link', '/api/auth/login', 'javascript:alert(1)'])
    ('refuses to leave for %s instead of the server start address', value => {
      const assign = vi.spyOn(googleNavigation, 'assign').mockImplementation(() => undefined)
      expect(() => googleNavigation.go(value)).toThrow('Nieprawidłowy adres logowania przez Google.')
      expect(assign).not.toHaveBeenCalled()
    })
  it.each([
    ['/api/auth/oauth2/authorization/google?mode=link', '/api/auth/oauth2/authorization/google?mode=link'],
    [`${window.location.origin}/api/auth/oauth2/authorization/google?mode=link&x=1`, '/api/auth/oauth2/authorization/google?mode=link&x=1'],
  ])('leaves for the server start address %s', (value, expected) => {
    const assign = vi.spyOn(googleNavigation, 'assign').mockImplementation(() => undefined)
    googleNavigation.go(value)
    expect(assign).toHaveBeenCalledWith(expected)
  })
})
