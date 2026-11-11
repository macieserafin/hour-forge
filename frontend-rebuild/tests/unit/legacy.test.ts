import { describe, expect, it } from 'vitest'
import { legacyTarget } from '../../src/app/App'

/** R11: addresses of the previous frontend (bookmarks, e-mails, Google and Jira returns) keep leading to the same place. */
describe('old addresses', () => {
  it.each([
    ['/dashboard', '', '/app/work'],
    ['/dashboard', 'team=t1', '/app/work?team=t1'],
    ['/dashboard', 'view=team&team=t1&from=2026-11-01&to=2026-11-07&person=u1&project=p1&tag=g1&issue=PROJ-2&type=MANUAL&q=x&undated=true',
      '/app/teams/t1/reports?from=2026-11-01&to=2026-11-07&person=u1&project=p1&tag=g1&issue=PROJ-2&type=MANUAL&q=x&undated=true'],
    ['/time', 'team=t1', '/app/work?team=t1'],
    ['/projects', 'team=t1', '/app/projects?team=t1'],
    ['/teams', 'team=t1', '/app/teams/t1'],
    ['/teams', '', '/app/teams'],
    ['/integrations', 'team=t1&jira=connected', '/app/teams/t1/integrations?jira=connected'],
    ['/integrations', '', '/app/teams'],
    ['/account', '', '/app/account'],
    ['/account', 'google=LINKED', '/app/account/security?google=LINKED'],
    ['/whats-new', '', '/app/whats-new'],
    ['/operator', '', '/app/operator'],
  ])('%s?%s leads to %s', (path, query, target) => { expect(legacyTarget(path, new URLSearchParams(query))).toBe(target) })

  it('encodes a team id taken from the address', () => {
    expect(legacyTarget('/teams', new URLSearchParams('team=a/b'))).toBe('/app/teams/a%2Fb')
  })
})
