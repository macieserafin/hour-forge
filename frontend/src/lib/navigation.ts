export function safeReturnTo(value: string | null): string {
  if (!value) return '/dashboard'
  try {
    const url = new URL(value, window.location.origin)
    if (url.origin !== window.location.origin) return '/dashboard'
    if (url.pathname === '/dashboard' || url.pathname === '/teams' || url.pathname === '/projects' || url.pathname === '/time' || url.pathname === '/integrations' || /^\/invite\/[A-Za-z0-9_-]{1,255}$/.test(url.pathname)) {
      return url.pathname + url.search
    }
  } catch { /* Ignore malformed or external return addresses. */ }
  return '/dashboard'
}

export const loginUrl = (returnTo: string) => `/login?returnTo=${encodeURIComponent(returnTo)}`
export const registrationUrl = (returnTo: string) => `/register?returnTo=${encodeURIComponent(returnTo)}`
