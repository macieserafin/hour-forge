export const HOME = '/app/work'
const legacyPages = new Set(['/dashboard', '/teams', '/projects', '/time', '/integrations', '/whats-new', '/account', '/operator'])
const appPage = /^\/app(?:\/[A-Za-z0-9_-]{1,64}){1,4}$/
const invitation = /^\/invite\/[A-Za-z0-9_-]{1,255}$/

/** A same-origin page of the app to return to after signing in; anything else falls back to My work. */
export function safeReturnTo(value: string | null): string {
  if (!value) return HOME
  try {
    const url = new URL(value, window.location.origin)
    if (url.origin !== window.location.origin) return HOME
    if (appPage.test(url.pathname) || legacyPages.has(url.pathname) || invitation.test(url.pathname)) return url.pathname + url.search
  } catch { /* Ignore malformed or external return addresses. */ }
  return HOME
}

export const loginUrl = (returnTo: string) => `/login?returnTo=${encodeURIComponent(returnTo)}`
export const registrationUrl = (returnTo: string) => `/register?returnTo=${encodeURIComponent(returnTo)}`

/** Where the browser goes to sign in or sign up with Google. A full page load, so it is a plain link and not a fetch. */
export function googleSignInUrl({ rememberMe = false, returnTo = null }: { rememberMe?: boolean; returnTo?: string | null } = {}) {
  const params = new URLSearchParams({ mode: 'login' })
  if (rememberMe) params.set('rememberMe', 'true')
  if (returnTo) params.set('returnTo', safeReturnTo(returnTo))
  return `/api/auth/oauth2/authorization/google?${params}`
}

const googleAuthorizationPath = '/api/auth/oauth2/authorization/google'

/** The only place that leaves the app for Google; accepts only the server's own start address. `assign` is separate so tests can watch it. */
export const googleNavigation = {
  go(url: string) {
    const target = new URL(url, window.location.origin)
    if (target.origin !== window.location.origin || target.pathname !== googleAuthorizationPath) throw new Error('Nieprawidłowy adres logowania przez Google.')
    googleNavigation.assign(target.pathname + target.search)
  },
  assign(path: string) {
    window.location.assign(path)
  },
}
