import { filenameFrom } from './download'
export interface User {
  id: string
  email: string
  displayName: string
}

export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER'

/** Owners and admins run the team; the server enforces the same rule. */
export const canManage = (role: TeamRole) => role !== 'MEMBER'
export const roleLabel = (role: TeamRole) => ({ OWNER: 'Właściciel', ADMIN: 'Administrator', MEMBER: 'Członek' })[role]

export interface Team {
  id: string
  name: string
  timezone: string
  createdAt: string
  role: TeamRole
  archivedAt: string | null
  activeTimersVisible: boolean
  requireDescription: boolean
  requireTagOrIssue: boolean
  weekStart: 'MONDAY' | 'SUNDAY'
}

export interface TeamSummary {
  activeMembers: number
  projects: number
  timeEntries: number
  activeTimers: boolean
}

export type InvitationStatus = 'ACTIVE' | 'ALREADY_MEMBER' | 'EXPIRED' | 'REVOKED' | 'EXHAUSTED' | 'TEAM_ARCHIVED' | 'NEW_INVITATION_REQUIRED'

export interface InvitationSummary {
  id: string
  createdAt: string
  expiresAt: string
  maxUses: number | null
  usedCount: number
  revokedAt: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'EXHAUSTED'
}

export interface InvitationsPage {
  content: InvitationSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface InvitationPreview {
  teamId: string
  teamName: string
  status: InvitationStatus
  expiresAt: string
}

export interface Invitation {
  id: string
  teamId: string
  invitationUrl: string
  token: string
  expiresAt: string
  maxUses: number | null
}

export interface Project {
  id: string
  teamId: string
  name: string
  description: string | null
  createdAt: string
  archivedAt: string | null
  repositoryUrl?: string | null
  color?: string | null
}

export interface ProjectInput {
  name: string
  description: string | null
  repositoryUrl?: string | null
  color?: string | null
}

export interface TimeEntry {
  id: string
  userId: string
  teamId: string
  projectId: string
  entryType: 'TIMER' | 'MANUAL'
  workDate: string | null
  startedAt: string | null
  endedAt: string | null
  durationSeconds: number | null
  description: string | null
  createdAt: string
  updatedAt: string
  pausedAt: string | null
  pausedDurationSeconds: number
  jiraIssueId?: string | null
  jiraIssueKey?: string | null
  tagId?: string | null
}

export interface StartTimerInput {
  teamId: string
  projectId: string
  description: string | null
  /** The Jira issue to link; null or absent means no link (when editing: remove it). */
  jiraIssueId?: string | null
  /** The work tag; absent means no tag (when editing: remove it). */
  tagId?: string | null
}

export interface DashboardSummary {
  teamId: string
  timezone: string
  today: string
  weekStart: string
  weekEnd: string
  todaySeconds: number
  weekSeconds: number
  /** The seven days of the week, starting with the first day the person chose. */
  days: { date: string; seconds: number }[]
}

export async function getDashboardSummary(teamId: string, signal?: AbortSignal, weekStart: Preferences['weekStart'] = 'MONDAY'): Promise<DashboardSummary> {
  const response = await request(`/api/dashboard/summary?${new URLSearchParams({ teamId, weekStart })}`, { signal })
  if (!response.ok) {
    const messages: Record<number, string> = {
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      404: 'Nie znaleziono zespołu lub nie masz już do niego dostępu.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać podsumowania czasu. Spróbuj ponownie.')
  }
  return response.json() as Promise<DashboardSummary>
}

export interface TimeEntryHistoryItem {
  id: string
  teamId: string
  projectId: string
  projectName: string
  entryType: 'TIMER' | 'MANUAL'
  workDate: string | null
  durationSeconds: number
  description: string | null
  startedAt: string | null
  endedAt: string | null
  jiraIssueId?: string | null
  jiraIssueKey?: string | null
  editedAt?: string | null
  editedByName?: string | null
  projectColor?: string | null
  tagId?: string | null
  tagName?: string | null
  tagColor?: string | null
}

export interface TimeEntryHistory {
  content: TimeEntryHistoryItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TimeEntryHistoryFilters {
  undated?: boolean
  projectId: string
  from: string
  to: string
  q?: string
}

export async function getTimeEntryHistory(teamId: string, filters: TimeEntryHistoryFilters,
  page: number, signal?: AbortSignal): Promise<TimeEntryHistory> {
  const params = new URLSearchParams({ teamId, page: String(page), size: '20' })
  if (filters.undated) params.set('undated', 'true')
  if (filters.projectId) params.set('projectId', filters.projectId)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  const response = await request(`/api/time-entries?${params}`, { signal })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź zakres dat i wybrany projekt.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do przeglądania tej historii.',
      404: 'Nie znaleziono projektu lub zespołu albo nie masz już do niego dostępu.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać historii czasu. Spróbuj ponownie.')
  }
  return response.json() as Promise<TimeEntryHistory>
}

export interface ManualTimeInput extends StartTimerInput {
  workDate: string | null
  durationSeconds: number
}

interface CsrfToken {
  token: string
  headerName: string
  parameterName: string
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export function tooManyRequestsMessage(response: Response) {
  const seconds = Number(response.headers.get('Retry-After'))
  return Number.isFinite(seconds) && seconds > 0
    ? `Zbyt wiele prób. Spróbuj ponownie za ${Math.ceil(seconds / 60)} min.`
    : 'Zbyt wiele prób. Spróbuj ponownie później.'
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

export async function login({ email, password, rememberMe }: { email: string; password: string; rememberMe?: boolean }): Promise<void> {
  const csrf = await getCsrf()
  const response = await request('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token },
    body: new URLSearchParams({ email: email.trim(), password, ...(rememberMe ? { rememberMe: 'true' } : {}) }),
  })
  if (response.status === 429) throw new ApiError(429, tooManyRequestsMessage(response))
  if (response.status === 401) throw new ApiError(401, 'Nieprawidłowy adres e-mail lub hasło.')
  if (response.status === 403) {
    const body = await response.json().catch(() => null) as { code?: string } | null
    if (body?.code === 'EMAIL_NOT_VERIFIED') {
      throw new ApiError(403, 'Potwierdź adres e-mail, aby się zalogować. Link wysłaliśmy po rejestracji.', 'EMAIL_NOT_VERIFIED')
    }
    throw new ApiError(403, 'Sesja formularza wygasła. Spróbuj zalogować się ponownie.')
  }
  if (!response.ok) throw new ApiError(response.status, 'Logowanie jest chwilowo niedostępne. Spróbuj ponownie.')
}

async function publicPost(path: string, body: unknown, messages: Record<number, string>, fallback: string) {
  const csrf = await getCsrf()
  const response = await request(path, { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body) })
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 429 ? tooManyRequestsMessage(response)
      : { 403: 'Sesja formularza wygasła. Spróbuj ponownie.', ...messages }[response.status] ?? fallback)
  }
}

export const confirmEmail = (token: string) => publicPost('/api/auth/email-verification/confirm', { token },
  { 400: 'Link jest nieprawidłowy albo wygasł. Poproś o nowy link.' }, 'Nie udało się potwierdzić adresu. Spróbuj ponownie.')

export const requestPasswordReset = (email: string) => publicPost('/api/auth/password-reset/request', { email: email.trim() },
  {}, 'Nie udało się wysłać wiadomości. Spróbuj ponownie później.')

export const confirmPasswordReset = (input: { token: string; newPassword: string }) => publicPost('/api/auth/password-reset/confirm', input,
  { 400: 'Link jest nieprawidłowy albo wygasł, lub hasło nie spełnia wymagań (co najmniej 12 znaków). Poproś o nowy link.' },
  'Nie udało się ustawić hasła. Spróbuj ponownie.')

export const confirmEmailChange = (token: string) => publicPost('/api/auth/email-change/confirm', { token },
  { 400: 'Link jest nieprawidłowy albo wygasł. Poproś o nowy link w ustawieniach konta.', 409: 'Ten adres jest już używany przez inne konto.' },
  'Nie udało się potwierdzić zmiany adresu. Spróbuj ponownie.')

export const resendVerification =(email: string) => publicPost('/api/auth/email-verification/resend', { email: email.trim() },
  {}, 'Nie udało się wysłać linku. Spróbuj ponownie później.')

export interface RegistrationInput {
  displayName: string
  email: string
  password: string
}

export async function register(input: RegistrationInput): Promise<void> {
  const csrf = await getCsrf()
  const response = await request('/api/auth/register', {
    method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify({ displayName: input.displayName.trim(), email: input.email.trim(), password: input.password }),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź nazwę, adres e-mail i hasło. Hasło musi mieć co najmniej 12 znaków i mieścić się w dozwolonej długości.',
      403: 'Sesja formularza wygasła. Spróbuj ponownie.',
      429: tooManyRequestsMessage(response),
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się utworzyć konta. Jeśli konto mogło już powstać, spróbuj się zalogować.')
  }
}

export async function logout(): Promise<void> {
  // Authentication rotates the CSRF token: always obtain a fresh token for logout.
  const csrf = await getCsrf()
  const response = await request('/api/auth/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } })
  if (!response.ok && response.status !== 401) {
    throw new ApiError(response.status, 'Nie udało się wylogować. Spróbuj ponownie.')
  }
}

async function accountRequest(path: string, method: 'PATCH' | 'POST', body: unknown, messages: Record<number, string>, fallback: string) {
  const csrf = await getCsrf()
  const response = await request(path, { method, headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: JSON.stringify(body) })
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 429 ? tooManyRequestsMessage(response)
      : { 401: 'Sesja wygasła. Zaloguj się ponownie.', 403: 'Sesja formularza wygasła. Spróbuj ponownie.', ...messages }[response.status] ?? fallback)
  }
  return response
}

export async function updateProfile(displayName: string): Promise<User> {
  const response = await accountRequest('/api/account/profile', 'PATCH', { displayName: displayName.trim() },
    { 400: 'Wpisz nazwę wyświetlaną (maksymalnie 255 znaków).' }, 'Nie udało się zapisać nazwy. Spróbuj ponownie.')
  return response.json() as Promise<User>
}

export async function changePassword(input: { currentPassword: string; newPassword: string }): Promise<void> {
  await accountRequest('/api/account/password', 'POST', input,
    { 400: 'Obecne hasło jest nieprawidłowe.' }, 'Nie udało się zmienić hasła. Spróbuj ponownie.')
}

export async function requestEmailChange(input: { newEmail: string; currentPassword: string }): Promise<void> {
  await accountRequest('/api/account/email', 'POST', { newEmail: input.newEmail.trim(), currentPassword: input.currentPassword },
    { 400: 'Sprawdź nowy adres i obecne hasło. Nowy adres musi różnić się od obecnego.', 503: 'Wysyłanie e-maili nie jest skonfigurowane na tym serwerze.' },
    'Nie udało się wysłać linku. Spróbuj ponownie później.')
}

export async function signOutEverywhere(): Promise<void> {
  await accountRequest('/api/account/sign-out-everywhere', 'POST', {}, {}, 'Nie udało się wylogować ze wszystkich urządzeń. Spróbuj ponownie.')
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
      400: path.endsWith('/invites') ? 'Sprawdź datę wygaśnięcia (najwyżej 30 dni w przyszłości), dodatni limit użyć i adres e-mail.' : 'Sprawdź nazwę zespołu i strefę czasową.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: path.startsWith('/api/invites/') ? 'Nie znaleziono zaproszenia. Sprawdź, czy link jest kompletny.' : 'Nie znaleziono zespołu lub nie masz już do niego dostępu.',
      409: 'Limit użyć tego zaproszenia został wyczerpany.',
      410: 'Zaproszenie wygasło lub zostało anulowane. Poproś właściciela o nowy link.',
      429: tooManyRequestsMessage(response),
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wykonać operacji. Spróbuj ponownie.')
  }
  return response.json() as Promise<T>
}

export const getTeams = (signal?: AbortSignal) => teamRequest<Team[]>('/api/teams', undefined, signal)
export const createTeam = (body: { name: string; timezone: string }) => teamRequest<Team>('/api/teams', body)
export const createInvitation = ({ teamId, ...body }: { teamId: string; expiresAt: string; maxUses: number | null; email?: string }) =>
  teamRequest<Invitation>(`/api/teams/${encodeURIComponent(teamId)}/invites`, body)

export async function getInvitations(teamId: string, page: number, signal?: AbortSignal): Promise<InvitationsPage> {
  return invitationManagementRequest(`/api/teams/${encodeURIComponent(teamId)}/invites?page=${page}&size=20`, 'GET', signal) as Promise<InvitationsPage>
}

export const revokeInvitation = (teamId: string, id: string) => invitationManagementRequest(
  `/api/teams/${encodeURIComponent(teamId)}/invites/${encodeURIComponent(id)}`, 'DELETE')

async function invitationManagementRequest(path: string, method: 'GET' | 'DELETE', signal?: AbortSignal): Promise<InvitationsPage | null> {
  const csrf = method === 'DELETE' ? await getCsrf() : null
  const response = await request(path, { method, signal, headers: csrf ? { [csrf.headerName]: csrf.token } : undefined })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Nieprawidłowa strona listy zaproszeń.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Zarządzanie zaproszeniami jest dostępne wyłącznie dla właściciela zespołu lub sesja formularza wygasła.',
      404: 'Zespół lub zaproszenie są niedostępne. Odśwież listę.',
      409: 'Zarchiwizowanego zespołu nie można zmieniać.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać lub unieważnić zaproszenia. Spróbuj ponownie.')
  }
  return response.status === 204 ? null : response.json() as Promise<InvitationsPage>
}
export const acceptInvitation = (token: string) => teamRequest<Team>(`/api/invites/${encodeURIComponent(token)}/accept`, {})

export async function getInvitationPreview(token: string, signal?: AbortSignal): Promise<InvitationPreview> {
  const response = await request(`/api/invites/${encodeURIComponent(token)}`, { signal })
  if (!response.ok) {
    const messages: Record<number, string> = {
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      404: 'Nie znaleziono zaproszenia. Sprawdź, czy link jest kompletny.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się sprawdzić zaproszenia. Spróbuj ponownie.')
  }
  return response.json() as Promise<InvitationPreview>
}

async function teamManagementRequest<T>(path: string, method: 'GET' | 'PATCH' | 'POST' | 'PUT' | 'DELETE', body?: unknown, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(path, {
    method, signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź nazwę zespołu, strefę czasową i wybraną osobę.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Tej operacji nie możesz wykonać w tym zespole lub sesja formularza wygasła.',
      404: 'Zespół lub wybrana osoba są niedostępne. Odśwież listę.',
      409: method === 'PATCH' ? 'Zarchiwizowanego zespołu nie można zmieniać. Reguła wymagająca tagu lub zadania Jira wymaga najpierw dodania tagu albo połączenia z Jira.'
        : 'W zespole działają timery. Poproś członków o ich zatrzymanie i spróbuj ponownie.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wykonać operacji na zespole. Odśwież dane i spróbuj ponownie.')
  }
  return (response.status === 204 ? null : await response.json()) as T
}

const teamPath = (teamId: string) => `/api/teams/${encodeURIComponent(teamId)}`
export const updateTeam = ({ teamId, ...body }: { teamId: string; name: string; timezone: string; activeTimersVisible?: boolean; requireDescription?: boolean; requireTagOrIssue?: boolean; weekStart?: 'MONDAY' | 'SUNDAY' }) => teamManagementRequest<Team>(teamPath(teamId), 'PATCH', body)
export const transferOwnership = (teamId: string, userId: string) => teamManagementRequest<null>(`${teamPath(teamId)}/transfer-ownership`, 'POST', { userId })
export const changeMemberRole = (teamId: string, userId: string, role: Exclude<TeamRole, 'OWNER'>) => teamManagementRequest<null>(`${teamPath(teamId)}/members/${encodeURIComponent(userId)}/role`, 'PUT', { role })
export const updateMemberProfile = (teamId: string, body: { jobTitle: string | null; ftePercent: number | null }) =>
  teamManagementRequest<null>(`${teamPath(teamId)}/members/me/profile`, 'PUT', body)
export const archiveTeam = (teamId: string) => teamManagementRequest<Team>(`${teamPath(teamId)}/archive`, 'POST')
export const getTeamSummary = (teamId: string, signal?: AbortSignal) => teamManagementRequest<TeamSummary>(`${teamPath(teamId)}/summary`, 'GET', undefined, signal)
export const deleteTeam = (teamId: string) => teamManagementRequest<null>(teamPath(teamId), 'DELETE')

async function projectRequest<T>(path: string, method = 'GET', body?: ProjectInput, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(path, {
    method, signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Wpisz nazwę projektu (maksymalnie 255 znaków). Opis może mieć najwyżej 2000 znaków. Link musi wskazywać repozytorium https://github.com/właściciel/repozytorium, bez danych logowania i parametrów (maksymalnie 2048 znaków).',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Nie znaleziono projektu lub zespołu albo nie masz już do niego dostępu.',
      409: path.endsWith('/archive')
        ? 'Projekt ma aktywny timer. Zatrzymaj go przed archiwizacją.'
        : method === 'DELETE' ? 'Można usunąć tylko pusty projekt w aktywnym zespole. Projekt z wpisami lub timerem musi zostać zachowany.'
        : 'Nie można zmienić archiwalnego projektu lub projektu w archiwalnym zespole. Najpierw przywróć projekt, jeśli zespół jest aktywny.',
      429: tooManyRequestsMessage(response),
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać lub zapisać projektu. Spróbuj ponownie.')
  }
  return response.status === 204 ? null as T : response.json() as Promise<T>
}

export const restoreProject = (teamId: string, projectId: string) =>
  projectRequest<Project>(`${projectsPath(teamId)}/${encodeURIComponent(projectId)}/restore`, 'POST')
export const deleteProject = (teamId: string, projectId: string) =>
  projectRequest<null>(`${projectsPath(teamId)}/${encodeURIComponent(projectId)}`, 'DELETE')

const projectsPath = (teamId: string) => `/api/teams/${encodeURIComponent(teamId)}/projects`
export const getProjects = (teamId: string, includeArchived: boolean, signal?: AbortSignal) =>
  projectRequest<Project[]>(`${projectsPath(teamId)}?includeArchived=${includeArchived}`, 'GET', undefined, signal)
export const getProject = (teamId: string, projectId: string, signal?: AbortSignal) =>
  projectRequest<Project>(`${projectsPath(teamId)}/${encodeURIComponent(projectId)}`, 'GET', undefined, signal)
export const createProject = (teamId: string, body: ProjectInput) => projectRequest<Project>(projectsPath(teamId), 'POST', body)
export const updateProject = (teamId: string, projectId: string, body: ProjectInput) =>
  projectRequest<Project>(`${projectsPath(teamId)}/${encodeURIComponent(projectId)}`, 'PUT', body)
export const archiveProject = (teamId: string, projectId: string) =>
  projectRequest<Project>(`${projectsPath(teamId)}/${encodeURIComponent(projectId)}/archive`, 'POST')

async function timeRequest(path: string, body?: unknown, signal?: AbortSignal): Promise<TimeEntry | null> {
  const csrf = body === undefined ? null : await getCsrf()
  const response = await request(path, {
    method: body === undefined ? 'GET' : 'POST', signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź projekt, datę i czas trwania wpisu. Wpis może mieć najwyżej 24 godziny, podana data pracy musi mieścić się w ostatnich 2 latach i nie może być z przyszłości, a opis może mieć najwyżej 2000 znaków. Wpis ręczny może być bez daty.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Nie znaleziono wpisu, projektu lub zespołu albo nie masz już do niego dostępu.',
      422: 'Zespół wymaga opisu pracy albo tagu lub zadania Jira. Uzupełnij wpis i spróbuj ponownie.',
      409: path.endsWith('/pause') || path.endsWith('/resume') ? 'Stan timera uległ zmianie. Sprawdź aktualny stan i spróbuj ponownie.'
        : path.endsWith('/start') ? 'Nie można uruchomić timera. Sprawdź aktywny timer, status projektu i połączenie z Jira.'
        : path.endsWith('/stop') ? 'Nie można zatrzymać tego timera. Mógł już zostać zatrzymany lub nie upłynęła jeszcze pełna sekunda.'
        : path.endsWith('/update') ? 'Nie można zmienić tego timera. Mógł już zostać zatrzymany, a projekt musi być aktywny i należeć do tego samego zespołu.'
        : path.endsWith('/switch') ? 'Nie można przełączyć timera. Bieżący timer musi działać co najmniej sekundę, a nowy projekt musi być aktywny.'
        : path.endsWith('/discard') ? 'Nie można odrzucić tego timera. Mógł już zostać zatrzymany lub odrzucony.'
        : 'Zespół jest zarchiwizowany i nie można dodać do niego czasu.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się odczytać lub zapisać czasu. Odśwież stan przed ponowieniem operacji.')
  }
  return response.status === 204 ? null : response.json() as Promise<TimeEntry>
}

export const getActiveTimer = (signal?: AbortSignal) => timeRequest('/api/timer/active', undefined, signal)
export const startTimer = (body: StartTimerInput) => timeRequest('/api/timer/start', body) as Promise<TimeEntry>
export const stopTimer = (body: { entryId: string; description: string | null }) => timeRequest('/api/timer/stop', body) as Promise<TimeEntry>
export interface UpdateTimerInput {
  entryId: string
  projectId: string
  description: string | null
  jiraIssueId: string | null
  tagId?: string | null
}

export interface SwitchTimerInput {
  stop: { entryId: string; description: string | null }
  start: StartTimerInput
}

export const updateTimer = (body: UpdateTimerInput) => timeRequest('/api/timer/update', body) as Promise<TimeEntry>
export const switchTimer = (body: SwitchTimerInput) => timeRequest('/api/timer/switch', body) as unknown as Promise<{ stopped: TimeEntry; started: TimeEntry }>
export const discardTimer = async (body: { entryId: string }) => { await timeRequest('/api/timer/discard', body) }
export const changeTimerState =({ action, ...body }: { action: 'pause' | 'resume'; entryId: string; description: string | null }) =>
  timeRequest(`/api/timer/${action}`, body) as Promise<TimeEntry>
export const createManualTimeEntry = (body: ManualTimeInput) => timeRequest('/api/time-entries', body) as Promise<TimeEntry>

export type UpdateTimeEntryInput = Omit<ManualTimeInput, 'teamId'>

async function changeTimeEntry(id: string, method: 'PATCH' | 'DELETE', body?: UpdateTimeEntryInput): Promise<void> {
  const csrf = await getCsrf()
  const response = await request(`/api/time-entries/${encodeURIComponent(id)}`, {
    method, headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź projekt, datę i czas trwania. Wpis może mieć najwyżej 24 godziny, podana data pracy musi mieścić się w ostatnich 2 latach i nie może być z przyszłości, a opis może mieć najwyżej 2000 znaków. Wpis z timera wymaga daty.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Wpis został usunięty lub nie masz już dostępu do wpisu, projektu lub zespołu.',
      409: 'Wpis jest aktywnym timerem, wybrany projekt lub tag jest archiwalny. Odśwież historię.',
      422: 'Zespół wymaga opisu pracy albo tagu lub zadania Jira. Uzupełnij wpis i spróbuj ponownie.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się zmienić wpisu. Odśwież historię przed ponowieniem operacji.')
  }
}

export const updateTimeEntry = ({ id, ...body }: UpdateTimeEntryInput & { id: string }) => changeTimeEntry(id, 'PATCH', body)
export const deleteTimeEntry = (id: string) => changeTimeEntry(id, 'DELETE')

export interface ReportFilters {
  undated?: boolean
  from: string
  to: string
  userId: string
  projectId: string
  entryType: '' | 'TIMER' | 'MANUAL'
  q?: string
  tagId?: string
  issueKey?: string
}

export interface ReportContext {
  teamId: string
  teamName: string
  timezone: string
  filter: { from: string | null; to: string | null; undated?: boolean; userIds: string[]; projectIds: string[]; entryType: 'TIMER' | 'MANUAL' | null; q?: string | null } | null
}

export interface ReportPage<T> {
  content: T[]
  page: number
  size: number
  hasNext: boolean
}

export interface ReportResult<T> {
  context: ReportContext
  data: ReportPage<T>
}

export interface ReportSummary {
  context: ReportContext
  totals: { durationSeconds: number; entryCount: number }
}

export interface ReportBucket {
  workDate: string | null
  userId: string | null
  displayName: string | null
  projectId: string | null
  projectName: string | null
  projectArchivedAt: string | null
  projectColor?: string | null
  tagId?: string | null
  tagName?: string | null
  tagColor?: string | null
  jiraIssueKey?: string | null
  durationSeconds: number
  entryCount: number
}

export interface TeamMember {
  userId: string
  displayName: string
  role: TeamRole
  joinedAt: string
  leftAt: string | null
  jobTitle?: string | null
  ftePercent?: number | null
}

export type ReportMember = TeamMember

export interface TeamMembersPage {
  content: TeamMember[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

async function membershipRequest(path: string, method: 'GET' | 'DELETE' | 'POST', signal?: AbortSignal) {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(path, { method, signal, headers: csrf ? { [csrf.headerName]: csrf.token } : undefined })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź wybraną stronę listy członków.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Zespół lub członek jest niedostępny. Możliwe, że członkostwo już się zakończyło.',
      409: 'Właściciel nie może opuścić zespołu ani zostać z niego usunięty.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać lub zmienić członkostwa. Odśwież listę przed ponowieniem operacji.')
  }
  return response
}

export async function getTeamMembers(teamId: string, includeFormer: boolean, page: number, signal?: AbortSignal): Promise<TeamMembersPage> {
  const params = new URLSearchParams({ includeFormer: String(includeFormer), page: String(page), size: '20' })
  const response = await membershipRequest(`/api/teams/${encodeURIComponent(teamId)}/members?${params}`, 'GET', signal)
  return response.json() as Promise<TeamMembersPage>
}

export async function removeTeamMember(teamId: string, userId: string): Promise<void> {
  await membershipRequest(`/api/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}`, 'DELETE')
}

export async function leaveTeam(teamId: string): Promise<void> {
  await membershipRequest(`/api/teams/${encodeURIComponent(teamId)}/leave`, 'POST')
}

export interface ReportProject {
  projectId: string
  name: string
  description: string | null
  createdAt: string
  archivedAt: string | null
}

export interface ReportEntry {
  tagName?: string | null
  tagColor?: string | null
  jiraIssueKey?: string | null
  editedAt?: string | null
  editedByName?: string | null
  projectColor?: string | null
  id: string
  userId: string
  displayName: string
  projectId: string
  projectName: string
  projectArchivedAt: string | null
  entryType: 'TIMER' | 'MANUAL'
  workDate: string | null
  startedAt: string | null
  endedAt: string | null
  durationSeconds: number
  description: string | null
  pausedAt: string | null
  pausedDurationMicros: number
  createdAt: string
  updatedAt: string
}

export type ReportSort = 'WORK_DATE:DESC' | 'WORK_DATE:ASC' | 'DURATION:DESC' | 'DURATION:ASC' | 'CREATED_AT:DESC' | 'CREATED_AT:ASC'

function reportParams(filters: ReportFilters, page?: number) {
  const params = new URLSearchParams()
  if (filters.undated) params.set('undated', 'true')
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.userId) params.set('userIds', filters.userId)
  if (filters.projectId) params.set('projectIds', filters.projectId)
  if (filters.tagId) params.set('tagIds', filters.tagId)
  if (filters.issueKey?.trim()) params.set('issueKey', filters.issueKey.trim())
  if (filters.entryType) params.set('entryType', filters.entryType)
  if (filters.q?.trim()) params.set('q', filters.q.trim())
  if (page !== undefined) { params.set('page', String(page)); params.set('size', '20') }
  return params
}

async function reportRequest<T>(teamId: string, endpoint: string, params: URLSearchParams, signal?: AbortSignal): Promise<T> {
  const response = await request(`/api/teams/${encodeURIComponent(teamId)}/reports/${endpoint}?${params}`, { signal })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź zakres dat (najwyżej 366 dni) i filtry raportu.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Widok zespołu jest dostępny wyłącznie dla jego właściciela, a podgląd aktywnych timerów można wyłączyć w ustawieniach zespołu.',
      404: 'Zespół lub wybrane dane są niedostępne. Sprawdź swoje uprawnienia i filtry.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać raportu zespołu. Spróbuj ponownie.')
  }
  return response.json() as Promise<T>
}

export const getReportSummary = (teamId: string, filters: ReportFilters, signal?: AbortSignal) =>
  reportRequest<ReportSummary>(teamId, 'summary', reportParams(filters), signal)

export type ReportGroup = 'MEMBER' | 'PROJECT' | 'DAY' | 'MEMBER_PROJECT' | 'TAG' | 'JIRA_ISSUE'

export interface ReportActiveTimer {
  entry: ReportEntry
  elapsedSeconds: number
}

export interface ReportActive {
  context: ReportContext
  asOf: string
  data: ReportPage<ReportActiveTimer>
}

export const getReportActive = (teamId: string, page: number, signal?: AbortSignal) =>
  reportRequest<ReportActive>(teamId, 'active-timers', new URLSearchParams({ page: String(page), size: '20' }), signal)

export function getReportBreakdown(teamId: string, filters: ReportFilters, groupBy: ReportGroup, page: number, signal?: AbortSignal) {
  const params = reportParams(filters, page)
  // A day-by-day table and chart should cover a month in one page.
  if (groupBy === 'DAY') params.set('size', '100')
  params.set('groupBy', groupBy)
  return reportRequest<ReportResult<ReportBucket> & { groupBy: ReportGroup }>(teamId, 'breakdown', params, signal)
}

export function getReportEntries(teamId: string, filters: ReportFilters, sort: ReportSort, page: number, signal?: AbortSignal) {
  const params = reportParams(filters, page)
  const [field, direction] = sort.split(':')
  params.set('sort', field!)
  params.set('direction', direction!)
  return reportRequest<ReportResult<ReportEntry>>(teamId, 'entries', params, signal)
}

export type ReportDownload = 'csv' | 'xlsx' | 'pdf' | 'mine-pdf'

const downloadPaths: Record<ReportDownload, string> = { csv: 'export.csv', xlsx: 'export.xlsx', pdf: 'export.pdf', 'mine-pdf': 'mine/export.pdf' }

/** A file version of the report the owner is looking at; the server applies the same filters as on screen. */
export async function downloadReport(teamId: string, filters: ReportFilters, kind: ReportDownload): Promise<{ blob: Blob; filename: string }> {
  const response = await request(`/api/teams/${encodeURIComponent(teamId)}/reports/${downloadPaths[kind]}?${reportParams(filters)}`)
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 429 ? tooManyRequestsMessage(response)
      : response.status === 400 ? 'Sprawdź zakres dat (najwyżej 366 dni), klucz zadania Jira i filtry. Plik CSV lub Excel może mieć najwyżej 50 000 wpisów, więc zawęź raport.'
      : response.status === 401 ? 'Sesja wygasła. Zaloguj się ponownie.'
      : response.status === 403 || response.status === 404 ? 'Raport jest niedostępny. Sprawdź swoje uprawnienia i filtry.'
      : 'Nie udało się pobrać pliku. Spróbuj ponownie.')
  }
  return { blob: await response.blob(), filename: filenameFrom(response, kind === 'csv' ? 'hourforge-raport.csv' : kind === 'xlsx' ? 'hourforge-raport.xlsx' : 'hourforge-raport.pdf') }
}

export const getReportMembers = (teamId: string, page: number, signal?: AbortSignal) =>
  reportRequest<ReportResult<ReportMember>>(teamId, 'members', new URLSearchParams({ page: String(page), size: '100' }), signal)
export const getReportProjects = (teamId: string, page: number, signal?: AbortSignal) =>
  reportRequest<ReportResult<ReportProject>>(teamId, 'projects', new URLSearchParams({ page: String(page), size: '100' }), signal)

export async function downloadAccountData(): Promise<{ blob: Blob; filename: string }> {
  const response = await request('/api/account/export')
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 429 ? tooManyRequestsMessage(response)
      : response.status === 401 ? 'Sesja wygasła. Zaloguj się ponownie.' : 'Nie udało się pobrać danych. Spróbuj ponownie.')
  }
  return { blob: await response.blob(), filename: filenameFrom(response, 'hourforge-export.json') }
}

export async function deleteAccount(currentPassword: string): Promise<void> {
  await accountRequest('/api/account/delete', 'POST', { currentPassword },
    { 400: 'Obecne hasło jest nieprawidłowe.', 409: 'Najpierw przekaż własność lub usuń zespoły, których jesteś właścicielem.' },
    'Nie udało się usunąć konta. Spróbuj ponownie.')
}

export interface JiraStatus {
  available: boolean
  siteConnected: boolean
  siteName: string | null
  siteUrl: string | null
  userConnected: boolean
  canManage: boolean
}

export interface JiraSiteChoice {
  cloudId: string
  name: string
  url: string
}

export type JiraIntent = 'SITE' | 'MEMBER'

async function jiraRequest<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(path, {
    method, signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Tej operacji nie możesz wykonać w tym zespole lub sesja formularza wygasła.',
      404: 'Nie znaleziono zespołu lub witryny Jira albo nie masz już do nich dostępu.',
      409: 'Najpierw połącz konto Jira. Właściciel zespołu musi też wybrać witrynę Jira.',
      502: 'Jira jest teraz niedostępna. Spróbuj ponownie za chwilę.',
    }
    throw new ApiError(response.status, response.status === 429 ? tooManyRequestsMessage(response)
      : messages[response.status] ?? 'Nie udało się wykonać operacji na integracji z Jira. Spróbuj ponownie.')
  }
  return (response.status === 204 ? null : await response.json()) as T
}

const jiraPath = (suffix: string, teamId?: string) => `/api/integrations/jira/${suffix}${teamId ? `?${new URLSearchParams({ teamId })}` : ''}`
export const getJiraStatus = (teamId: string, signal?: AbortSignal) => jiraRequest<JiraStatus>(jiraPath('status', teamId), 'GET', undefined, signal)
export const getJiraSites = (teamId: string, signal?: AbortSignal) => jiraRequest<JiraSiteChoice[]>(jiraPath('sites', teamId), 'GET', undefined, signal)
export const startJiraAuthorization = (input: { teamId: string; intent: JiraIntent }) =>
  jiraRequest<{ url: string }>(jiraPath('authorize'), 'POST', input)
export const selectJiraSite = (input: { teamId: string; cloudId: string }) => jiraRequest<null>(jiraPath('site'), 'POST', input)
export const disconnectJiraAccount = (teamId: string) => jiraRequest<null>(jiraPath('connection', teamId), 'DELETE')
export const disconnectJiraSite = (teamId: string) => jiraRequest<null>(jiraPath('site', teamId), 'DELETE')

export interface JiraIssue {
  id: string
  key: string
  summary: string
  status: string
  issueType: string
  projectKey: string
}

export interface JiraIssuePage {
  issues: JiraIssue[]
  nextPageToken: string | null
}

export interface JiraIssueFilters {
  q: string
  assignee: 'ME' | 'ANYONE'
  sprint: 'ANY' | 'ACTIVE' | 'CLOSED'
  includeDone: boolean
}

export function getJiraIssues(teamId: string, filters: JiraIssueFilters, pageToken: string | null, signal?: AbortSignal) {
  const params = new URLSearchParams({ teamId, assignee: filters.assignee, sprint: filters.sprint, includeDone: String(filters.includeDone) })
  if (filters.q.trim()) params.set('q', filters.q.trim())
  if (pageToken) params.set('pageToken', pageToken)
  return jiraRequest<JiraIssuePage>(`/api/integrations/jira/issues?${params}`, 'GET', undefined, signal)
}

export interface Preferences {
  defaultTeamId: string | null
  startPage: 'DASHBOARD' | 'TIME' | 'PROJECTS' | 'TEAMS'
  timeFormat: 'CLOCK' | 'DECIMAL'
  weekStart: 'MONDAY' | 'SUNDAY'
  longTimerEmail: boolean
  browserNotifications: boolean
  longTimerHours: number
  titleTimer: boolean
  teamEventEmails?: boolean
  weeklyReportEmail?: boolean
}

export async function getPreferences(signal?: AbortSignal): Promise<Preferences> {
  const response = await request('/api/account/preferences', { signal })
  if (!response.ok) throw new ApiError(response.status, response.status === 401 ? 'Sesja wygasła. Zaloguj się ponownie.' : 'Nie udało się wczytać ustawień.')
  return response.json() as Promise<Preferences>
}

export async function savePreferences(input: Preferences): Promise<Preferences> {
  const csrf = await getCsrf()
  const response = await request('/api/account/preferences', { method: 'PUT',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: JSON.stringify(input) })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź wybrane ustawienia. Zespół domyślny musi być jednym z Twoich zespołów.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Sesja formularza wygasła. Spróbuj ponownie.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się zapisać ustawień. Spróbuj ponownie.')
  }
  return response.json() as Promise<Preferences>
}

export interface CopyDayResult {
  copied: number
  totalSeconds: number
}

export async function copyDay(input: { teamId: string; fromDate: string; toDate: string }): Promise<CopyDayResult> {
  const csrf = await getCsrf()
  const response = await request('/api/time-entries/copy-day', { method: 'POST',
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: JSON.stringify(input) })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Nie można skopiować tego dnia: wybierz inny dzień docelowy z ostatnich 2 lat (nie z przyszłości), a wpisów może być najwyżej 100.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Sesja formularza wygasła. Spróbuj ponownie.',
      404: 'Nie ma wpisów do skopiowania albo nie masz już dostępu do zespołu.',
      409: 'Zespół jest zarchiwizowany i nie można dodać do niego czasu.',
    }
    throw new ApiError(response.status, response.status === 429 ? tooManyRequestsMessage(response) : messages[response.status] ?? 'Nie udało się skopiować wpisów. Spróbuj ponownie.')
  }
  return response.json() as Promise<CopyDayResult>
}

export interface EntryTemplate {
  id: string
  name: string
  projectId: string
  description: string | null
  jiraIssueId: string | null
  jiraIssueKey: string | null
}

export interface TemplateInput {
  name: string
  projectId: string
  description: string | null
  jiraIssueId: string | null
}

async function templateRequest<T>(teamId: string, method: string, suffix = '', body?: unknown, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(`/api/teams/${encodeURIComponent(teamId)}/templates${suffix}`, { method, signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined, body: body === undefined ? undefined : JSON.stringify(body) })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź nazwę szablonu (najwyżej 80 znaków) i opis (najwyżej 2000 znaków).',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Sesja formularza wygasła. Spróbuj ponownie.',
      404: 'Nie znaleziono szablonu, projektu lub zadania Jira albo nie masz już dostępu.',
      409: 'Szablon o takiej nazwie już istnieje, masz ich za dużo (najwyżej 30 w zespole), projekt jest archiwalny albo zespół jest zarchiwizowany.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wykonać operacji na szablonie. Spróbuj ponownie.')
  }
  return (response.status === 204 ? null : await response.json()) as T
}

export const getTemplates = (teamId: string, signal?: AbortSignal) => templateRequest<EntryTemplate[]>(teamId, 'GET', '', undefined, signal)
export const createTemplate = (teamId: string, input: TemplateInput) => templateRequest<EntryTemplate>(teamId, 'POST', '', input)
export const deleteTemplate = (teamId: string, templateId: string) => templateRequest<null>(teamId, 'DELETE', `/${encodeURIComponent(templateId)}`)

export interface DashboardOverview {
  todaySeconds: number
  weekSeconds: number
  teams: { teamId: string; teamName: string; archived: boolean; todaySeconds: number; weekSeconds: number }[]
}

export interface DashboardActivity {
  from: string
  to: string
  today: string
  maxSeconds: number
  days: { workDate: string; seconds: number }[]
}

async function dashboardRequest<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await request(path, { signal })
  if (!response.ok) {
    throw new ApiError(response.status, response.status === 401 ? 'Sesja wygasła. Zaloguj się ponownie.' : 'Nie udało się wczytać podsumowania czasu. Spróbuj ponownie.')
  }
  return response.json() as Promise<T>
}

export const getDashboardOverview = (weekStart: Preferences['weekStart'], signal?: AbortSignal) =>
  dashboardRequest<DashboardOverview>(`/api/dashboard/overview?${new URLSearchParams({ weekStart })}`, signal)
export const getDashboardActivity = (weekStart: Preferences['weekStart'], weeks = 12, signal?: AbortSignal) =>
  dashboardRequest<DashboardActivity>(`/api/dashboard/activity?${new URLSearchParams({ weekStart, weeks: String(weeks) })}`, signal)

export const getJiraIssue = (teamId: string, issueId: string, signal?: AbortSignal) =>
  jiraRequest<JiraIssue>(`/api/integrations/jira/issues/${encodeURIComponent(issueId)}?${new URLSearchParams({ teamId })}`, 'GET', undefined, signal)

export type CorrectionInput = Omit<UpdateTimeEntryInput, 'jiraIssueId'>

async function correctionRequest<T>(path: string, method: 'GET' | 'POST' | 'PATCH' | 'DELETE', body?: CorrectionInput, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(path, {
    method, signal, headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź projekt, datę i czas trwania. Wpis może mieć najwyżej 24 godziny, a data pracy musi mieścić się w ostatnich 2 latach i nie może być z przyszłości.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Korekty czasu może wykonywać tylko właściciel lub administrator zespołu albo sesja formularza wygasła.',
      404: 'Wpis, osoba lub projekt są niedostępne. Odśwież listę.',
      409: 'Zespół jest zarchiwizowany, wpis jest uruchomionym timerem albo projekt jest archiwalny.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się zapisać korekty. Odśwież listę przed ponowieniem operacji.')
  }
  return (response.status === 204 ? null : await response.json()) as T
}

export const getMemberTimeEntries = (teamId: string, memberId: string, page: number, signal?: AbortSignal) =>
  correctionRequest<TimeEntryHistory>(`${teamPath(teamId)}/members/${encodeURIComponent(memberId)}/time-entries?page=${page}&size=10`, 'GET', undefined, signal)
export const addMemberTimeEntry = ({ teamId, memberId, ...body }: CorrectionInput & { teamId: string; memberId: string }) =>
  correctionRequest<null>(`${teamPath(teamId)}/members/${encodeURIComponent(memberId)}/time-entries`, 'POST', body)
export const correctMemberTimeEntry = ({ teamId, id, ...body }: CorrectionInput & { teamId: string; id: string }) =>
  correctionRequest<null>(`${teamPath(teamId)}/time-entries/${encodeURIComponent(id)}`, 'PATCH', body)
export const deleteMemberTimeEntry = ({ teamId, id }: { teamId: string; id: string }) =>
  correctionRequest<null>(`${teamPath(teamId)}/time-entries/${encodeURIComponent(id)}`, 'DELETE')

export interface Tag {
  id: string
  teamId: string
  name: string
  color: string | null
  archivedAt: string | null
}

export interface TagInput {
  name: string
  color: string | null
}

async function tagRequest<T>(teamId: string, method: 'GET' | 'POST' | 'PUT', suffix = '', body?: unknown, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(`${teamPath(teamId)}/tags${suffix}`, {
    method, signal, headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Podaj nazwę tagu (do 40 znaków) i wybierz kolor z listy.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Tagami zarządza właściciel lub administrator zespołu albo sesja formularza wygasła.',
      404: 'Zespół lub tag są niedostępne. Odśwież listę.',
      409: method === 'GET' ? 'Nie udało się wczytać tagów.' : 'Tag o tej nazwie już istnieje, jest zarchiwizowany albo zespół jest zarchiwizowany.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wykonać operacji na tagach. Odśwież listę i spróbuj ponownie.')
  }
  return await response.json() as T
}

export const getTags = (teamId: string, includeArchived: boolean, signal?: AbortSignal) =>
  tagRequest<Tag[]>(teamId, 'GET', includeArchived ? '?includeArchived=true' : '', undefined, signal)
export const createTag = ({ teamId, ...body }: TagInput & { teamId: string }) => tagRequest<Tag>(teamId, 'POST', '', body)
export const updateTag = ({ teamId, id, ...body }: TagInput & { teamId: string; id: string }) => tagRequest<Tag>(teamId, 'PUT', `/${encodeURIComponent(id)}`, body)
export const archiveTag = ({ teamId, id }: { teamId: string; id: string }) => tagRequest<Tag>(teamId, 'POST', `/${encodeURIComponent(id)}/archive`)
export const restoreTag = ({ teamId, id }: { teamId: string; id: string }) => tagRequest<Tag>(teamId, 'POST', `/${encodeURIComponent(id)}/restore`)

export interface ReportTimesheet {
  context: ReportContext
  days: string[]
  rows: { userId: string; displayName: string; seconds: number[]; total: number }[]
  dayTotals: number[]
  total: number
  truncated: boolean
}

export const getReportTimesheet = (teamId: string, filters: ReportFilters, signal?: AbortSignal) =>
  reportRequest<ReportTimesheet>(teamId, 'timesheet', reportParams(filters), signal)

export type UnsubscribeKind = 'TEAM_EVENTS' | 'WEEKLY_REPORT' | 'LONG_TIMER'

export const unsubscribe = (input: { token: string; kind: UnsubscribeKind }) => publicPost('/api/notifications/unsubscribe', input,
  { 400: 'Link jest nieprawidłowy.', 404: 'Link jest nieprawidłowy albo nie jest już aktualny. Ustawienia powiadomień znajdziesz w koncie.' },
  'Nie udało się wypisać z wiadomości. Spróbuj ponownie.')
