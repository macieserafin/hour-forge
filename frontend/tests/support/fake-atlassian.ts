import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'

export interface FakeAtlassian {
  url: string
  close: () => Promise<void>
}

const issues = [
  { id: '10001', key: 'PROJ-1', summary: 'First task' },
  { id: '10002', key: 'PROJ-2', summary: 'Second task' },
]
const site = { id: 'cloud-e2e', url: 'https://e2e.atlassian.net', name: 'E2E Jira' }

/**
 * A stand-in for auth.atlassian.com and api.atlassian.com for browser scenarios: the consent screen approves at once
 * and sends the browser straight back to the application's callback.
 */
export async function startFakeAtlassian(redirectUri: string): Promise<FakeAtlassian> {
  let generation = 0
  const accessTokens = new Set<string>()
  const refreshTokens = new Set<string>()
  const issue = () => {
    generation += 1
    accessTokens.add(`e2e-access-${generation}`)
    refreshTokens.add(`e2e-refresh-${generation}`)
    return { access_token: `e2e-access-${generation}`, refresh_token: `e2e-refresh-${generation}`, expires_in: 3600,
      scope: 'read:jira-work offline_access', token_type: 'Bearer' }
  }
  const send = (response: ServerResponse, status: number, body: unknown) => {
    response.writeHead(status, { 'Content-Type': 'application/json' }).end(JSON.stringify(body))
  }
  const readBody = (request: IncomingMessage) => new Promise<string>(resolve => {
    const chunks: Buffer[] = []
    request.on('data', chunk => chunks.push(chunk))
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
  })
  const authorized = (request: IncomingMessage) => accessTokens.has((request.headers.authorization ?? '').replace('Bearer ', ''))
  const asIssue = (item: (typeof issues)[number]) => ({ id: item.id, key: item.key, fields: { summary: item.summary,
    status: { name: 'To Do' }, issuetype: { name: 'Task' }, project: { key: 'PROJ' } } })

  const server = createServer(async (request, response) => {
    const url = new URL(request.url ?? '/', 'http://fake')
    try {
      if (request.method === 'GET' && url.pathname === '/authorize') {
        const state = url.searchParams.get('state')
        const valid = url.searchParams.get('client_id') === 'e2e-client' && url.searchParams.get('redirect_uri') === redirectUri
          && url.searchParams.get('response_type') === 'code' && url.searchParams.get('audience') === 'api.atlassian.com' && state
        if (!valid) { send(response, 400, { error: 'invalid_request' }); return }
        response.writeHead(302, { Location: `${redirectUri}?${new URLSearchParams({ code: 'e2e-code', state })}` }).end()
      } else if (request.method === 'POST' && url.pathname === '/oauth/token') {
        const body = JSON.parse(await readBody(request)) as Record<string, string>
        if (body.client_secret !== 'e2e-client-secret-only') send(response, 401, { error: 'invalid_client' })
        else if (body.grant_type === 'authorization_code' && body.code === 'e2e-code') send(response, 200, issue())
        else if (body.grant_type === 'refresh_token' && refreshTokens.delete(body.refresh_token ?? '')) send(response, 200, issue())
        else send(response, 403, { error: 'invalid_grant' })
      } else if (url.pathname === '/oauth/token/accessible-resources') {
        if (authorized(request)) send(response, 200, [{ ...site, scopes: ['read:jira-work'] }]); else send(response, 401, {})
      } else if (request.method === 'POST' && url.pathname === `/ex/jira/${site.id}/rest/api/3/search/jql`) {
        if (!authorized(request)) { send(response, 401, {}); return }
        const { jql } = JSON.parse(await readBody(request)) as { jql: string }
        const term = /text ~ "([^*"]+)\*/.exec(jql)?.[1]?.toLowerCase()
        send(response, 200, { issues: issues.filter(item => !term || item.summary.toLowerCase().includes(term) || item.key.toLowerCase() === term).map(asIssue) })
      } else if (request.method === 'GET' && url.pathname.startsWith(`/ex/jira/${site.id}/rest/api/3/issue/`)) {
        const found = issues.find(item => item.id === url.pathname.split('/').at(-1))
        if (!authorized(request)) send(response, 401, {}); else if (!found) send(response, 404, {}); else send(response, 200, asIssue(found))
      } else {
        send(response, 404, {})
      }
    } catch {
      send(response, 500, {})
    }
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  const port = (server.address() as { port: number }).port
  return { url: `http://127.0.0.1:${port}`, close: () => new Promise<void>(resolve => { server.close(() => resolve()); server.closeAllConnections() }) }
}
