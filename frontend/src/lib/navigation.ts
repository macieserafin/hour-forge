export function safeReturnTo(value: string | null): string {
  if (!value) return '/dashboard'
  try {
    const url = new URL(value, window.location.origin)
    if (url.origin !== window.location.origin) return '/dashboard'
    if (url.pathname === '/dashboard' || url.pathname === '/teams' || url.pathname === '/projects' || url.pathname === '/time' || url.pathname === '/integrations' || url.pathname === '/whats-new' || /^\/invite\/[A-Za-z0-9_-]{1,255}$/.test(url.pathname)) {
      return url.pathname + url.search
    }
  } catch { /* Ignore malformed or external return addresses. */ }
  return '/dashboard'
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
