import { createHash, generateKeyPairSync, randomUUID, sign, type KeyObject } from 'node:crypto'
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'

export const googleClientId = 'e2e-google-client.apps.example.test'
export const googleClientSecret = 'e2e-google-client-secret-only'

/** The Google account the next consent signs in; scenarios set it through {@link setGoogleAccount}. */
export interface GoogleAccount {
  sub: string
  email: string
  emailVerified?: boolean
  hd?: string | null
  name?: string
}

export interface FakeGoogle {
  url: string
  close: () => Promise<void>
}

interface Grant { account: GoogleAccount; nonce: string; codeChallenge: string; redirectUri: string }

const base64url = (value: Buffer | string) => Buffer.from(value).toString('base64url')

/**
 * A stand-in for Google's OpenID Connect provider for browser scenarios, following the backend's FakeGoogle: discovery,
 * JWKS, an authorization endpoint that consents at once (or cancels when told to), and a token endpoint that checks the
 * client secret, the redirect address, PKCE and a single-use code, and returns an RS256 ID token with the request's
 * nonce. Only fictitious values; it listens on 127.0.0.1 and must start before the backend, which reads discovery at start.
 */
export async function startFakeGoogle(): Promise<FakeGoogle> {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
  const keyId = `e2e-google-${randomUUID()}`
  const grants = new Map<string, Grant>()
  let account: GoogleAccount = { sub: 'e2e-google-default', email: 'default@google-e2e.example.test', emailVerified: true, hd: 'google-e2e.example.test', name: 'Google Person' }
  let cancel = false
  let counter = 0
  let issuer = ''

  const send = (response: ServerResponse, status: number, body: unknown) => {
    response.writeHead(status, { 'Content-Type': 'application/json' }).end(JSON.stringify(body))
  }
  const readBody = (request: IncomingMessage) => new Promise<string>(resolve => {
    const chunks: Buffer[] = []
    request.on('data', chunk => chunks.push(chunk))
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
  })
  const idToken = (grant: Grant, key: KeyObject) => {
    const now = Math.floor(Date.now() / 1000)
    const claims: Record<string, unknown> = { iss: issuer, aud: googleClientId, azp: googleClientId, sub: grant.account.sub, email: grant.account.email,
      email_verified: grant.account.emailVerified ?? true, name: grant.account.name ?? 'Google Person', iat: now, exp: now + 3600 }
    if (grant.account.hd) claims.hd = grant.account.hd
    claims.nonce = grant.nonce
    const signingInput = `${base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: keyId }))}.${base64url(JSON.stringify(claims))}`
    return `${signingInput}.${base64url(sign('sha256', Buffer.from(signingInput), key))}`
  }

  const server = createServer(async (request, response) => {
    const url = new URL(request.url ?? '/', 'http://fake')
    try {
      if (request.method === 'GET' && url.pathname === '/.well-known/openid-configuration') {
        send(response, 200, { issuer, authorization_endpoint: `${issuer}/authorize`, token_endpoint: `${issuer}/token`, jwks_uri: `${issuer}/jwks`,
          response_types_supported: ['code'], subject_types_supported: ['public'], id_token_signing_alg_values_supported: ['RS256'],
          scopes_supported: ['openid', 'email', 'profile'], token_endpoint_auth_methods_supported: ['client_secret_basic', 'client_secret_post'],
          code_challenge_methods_supported: ['plain', 'S256'], claims_supported: ['aud', 'email', 'email_verified', 'exp', 'hd', 'iat', 'iss', 'name', 'sub'] })
      } else if (request.method === 'GET' && url.pathname === '/jwks') {
        send(response, 200, { keys: [{ ...publicKey.export({ format: 'jwk' }), kid: keyId, alg: 'RS256', use: 'sig' }] })
      } else if (request.method === 'GET' && url.pathname === '/authorize') {
        const redirectUri = url.searchParams.get('redirect_uri')
        const state = url.searchParams.get('state')
        const nonce = url.searchParams.get('nonce')
        const codeChallenge = url.searchParams.get('code_challenge')
        // What the contract makes mandatory for the backend: PKCE with S256, a nonce and the openid scope.
        if (url.searchParams.get('client_id') !== googleClientId || url.searchParams.get('response_type') !== 'code' || !redirectUri || !state
          || !codeChallenge || url.searchParams.get('code_challenge_method') !== 'S256' || !nonce
          || !(url.searchParams.get('scope') ?? '').split(' ').includes('openid')) {
          send(response, 400, { error: 'invalid_request' }); return
        }
        const back = new URL(redirectUri)
        if (cancel) {
          back.searchParams.set('error', 'access_denied')
        } else {
          counter += 1
          const code = `e2e-google-code-${counter}`
          grants.set(code, { account, nonce, codeChallenge, redirectUri })
          back.searchParams.set('code', code)
        }
        back.searchParams.set('state', state)
        response.writeHead(302, { Location: back.toString() }).end()
      } else if (request.method === 'POST' && url.pathname === '/token') {
        const form = new URLSearchParams(await readBody(request))
        const expected = `Basic ${Buffer.from(`${encodeURIComponent(googleClientId)}:${encodeURIComponent(googleClientSecret)}`).toString('base64')}`
        if (request.headers.authorization !== expected) { send(response, 401, { error: 'invalid_client' }); return }
        const code = form.get('code') ?? ''
        const grant = grants.get(code)
        grants.delete(code)
        if (form.get('grant_type') !== 'authorization_code' || !grant || grant.redirectUri !== form.get('redirect_uri')) {
          send(response, 400, { error: 'invalid_grant' }); return
        }
        const verifier = form.get('code_verifier')
        if (!verifier || createHash('sha256').update(verifier, 'ascii').digest('base64url') !== grant.codeChallenge) {
          send(response, 400, { error: 'invalid_grant', error_description: 'code_verifier' }); return
        }
        counter += 1
        send(response, 200, { access_token: `e2e-google-access-${counter}`, token_type: 'Bearer', expires_in: 3599, scope: 'openid email profile',
          id_token: idToken(grant, privateKey) })
      } else if (request.method === 'POST' && url.pathname === '/e2e/next') {
        // Scenario control: which Google account consents next, or whether the person cancels.
        const next = JSON.parse(await readBody(request)) as { account?: GoogleAccount; cancel?: boolean }
        if (next.account) account = next.account
        cancel = next.cancel === true
        response.writeHead(204).end()
      } else {
        send(response, 404, {})
      }
    } catch {
      send(response, 500, {})
    }
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  issuer = `http://127.0.0.1:${(server.address() as { port: number }).port}`
  return { url: issuer, close: () => new Promise<void>(resolve => { server.close(() => resolve()); server.closeAllConnections() }) }
}
