export interface User {
  id: string
  email: string
  displayName: string
}

export interface Team {
  id: string
  name: string
  timezone: string
  createdAt: string
  role: 'OWNER' | 'MEMBER'
}

export interface Invitation {
  id: string
  teamId: string
  invitationUrl: string
  token: string
  expiresAt: string
  maxUses: number | null
}

interface CsrfToken {
  token: string
  headerName: string
  parameterName: string
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request(path: string, options?: RequestInit): Promise<Response> {
  try {
    return await fetch(path, { ...options, credentials: 'include', cache: 'no-store' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Nie udało się połączyć. Sprawdź połączenie i spróbuj ponownie.')
  }
}

export async function getUser(signal?: AbortSignal): Promise<User | null> {
  const response = await request('/api/me', { signal })
  if (response.status === 401) return null
  if (!response.ok) throw new ApiError(response.status, 'Nie możemy teraz wczytać Twojego konta.')
  return response.json() as Promise<User>
}

async function getCsrf(): Promise<CsrfToken> {
  const response = await request('/api/auth/csrf')
  if (!response.ok) throw new ApiError(response.status, 'Nie udało się przygotować bezpiecznej sesji. Spróbuj ponownie.')
  return response.json() as Promise<CsrfToken>
}

export async function login({ email, password }: { email: string; password: string }): Promise<void> {
  const csrf = await getCsrf()
  const response = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token },
    body: new URLSearchParams({ email: email.trim(), password }),
  })
  if (response.status === 401) throw new ApiError(401, 'Nieprawidłowy adres e-mail lub hasło.')
  if (response.status === 403) throw new ApiError(403, 'Sesja formularza wygasła. Spróbuj zalogować się ponownie.')
  if (!response.ok) throw new ApiError(response.status, 'Logowanie jest chwilowo niedostępne. Spróbuj ponownie.')
}

export async function logout(): Promise<void> {
  // Authentication rotates the CSRF token: always obtain a fresh token for logout.
  const csrf = await getCsrf()
  const response = await request('/api/auth/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } })
  if (!response.ok && response.status !== 401) {
    throw new ApiError(response.status, 'Nie udało się wylogować. Spróbuj ponownie.')
  }
}

async function teamRequest<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const csrf = body === undefined ? null : await getCsrf()
  const response = await request(path, {
    method: body === undefined ? 'GET' : 'POST', signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: path.endsWith('/invites') ? 'Sprawdź datę wygaśnięcia i limit użyć. Data musi być w przyszłości.' : 'Sprawdź nazwę zespołu i strefę czasową.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: path.startsWith('/api/invites/') ? 'Nie znaleziono zaproszenia. Sprawdź, czy link jest kompletny.' : 'Nie znaleziono zespołu lub nie masz już do niego dostępu.',
      409: 'Limit użyć tego zaproszenia został wyczerpany.',
      410: 'Zaproszenie wygasło lub zostało anulowane. Poproś właściciela o nowy link.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wykonać operacji. Spróbuj ponownie.')
  }
  return response.json() as Promise<T>
}

export const getTeams = (signal?: AbortSignal) => teamRequest<Team[]>('/api/teams', undefined, signal)
export const createTeam = (body: { name: string; timezone: string }) => teamRequest<Team>('/api/teams', body)
export const createInvitation = ({ teamId, ...body }: { teamId: string; expiresAt: string; maxUses: number | null }) =>
  teamRequest<Invitation>(`/api/teams/${encodeURIComponent(teamId)}/invites`, body)
export const acceptInvitation = (token: string) => teamRequest<Team>(`/api/invites/${encodeURIComponent(token)}/accept`, {})
