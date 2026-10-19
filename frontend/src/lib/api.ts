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

export interface Project {
  id: string
  teamId: string
  name: string
  description: string | null
  createdAt: string
  archivedAt: string | null
}

export interface ProjectInput {
  name: string
  description: string | null
}

export interface TimeEntry {
  id: string
  userId: string
  teamId: string
  projectId: string
  entryType: 'TIMER' | 'MANUAL'
  workDate: string
  startedAt: string | null
  endedAt: string | null
  durationSeconds: number | null
  description: string | null
  createdAt: string
  updatedAt: string
  pausedAt: string | null
  pausedDurationSeconds: number
}

export interface StartTimerInput {
  teamId: string
  projectId: string
  description: string | null
}

export interface DashboardSummary {
  teamId: string
  timezone: string
  today: string
  weekStart: string
  weekEnd: string
  todaySeconds: number
  weekSeconds: number
}

export async function getDashboardSummary(teamId: string, signal?: AbortSignal): Promise<DashboardSummary> {
  const response = await request(`/api/dashboard/summary?${new URLSearchParams({ teamId })}`, { signal })
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
  workDate: string
  durationSeconds: number
  description: string | null
  startedAt: string | null
  endedAt: string | null
}

export interface TimeEntryHistory {
  content: TimeEntryHistoryItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TimeEntryHistoryFilters {
  projectId: string
  from: string
  to: string
}

export async function getTimeEntryHistory(teamId: string, filters: TimeEntryHistoryFilters,
  page: number, signal?: AbortSignal): Promise<TimeEntryHistory> {
  const params = new URLSearchParams({ teamId, page: String(page), size: '20' })
  if (filters.projectId) params.set('projectId', filters.projectId)
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
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
  workDate: string
  durationSeconds: number
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

async function projectRequest<T>(path: string, method = 'GET', body?: ProjectInput, signal?: AbortSignal): Promise<T> {
  const csrf = method === 'GET' ? null : await getCsrf()
  const response = await request(path, {
    method, signal,
    headers: csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : undefined,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Wpisz nazwę projektu (maksymalnie 255 znaków) i sprawdź dane formularza.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Nie znaleziono projektu lub zespołu albo nie masz już do niego dostępu.',
      409: path.endsWith('/archive')
        ? 'Projekt ma aktywny timer. Zatrzymaj go przed archiwizacją.'
        : 'Projekt jest już zarchiwizowany i nie można go edytować.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać lub zapisać projektu. Spróbuj ponownie.')
  }
  return response.json() as Promise<T>
}

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
      400: 'Sprawdź projekt, datę i czas trwania wpisu. Czas musi być dodatnią liczbą pełnych sekund.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Nie znaleziono wpisu, projektu lub zespołu albo nie masz już do niego dostępu.',
      409: path.endsWith('/pause') || path.endsWith('/resume') ? 'Stan timera uległ zmianie. Sprawdź aktualny stan i spróbuj ponownie.'
        : path.endsWith('/start') ? 'Nie można uruchomić timera. Sprawdź aktywny timer i status projektu.'
        : path.endsWith('/stop') ? 'Nie można zatrzymać tego timera. Mógł już zostać zatrzymany lub nie upłynęła jeszcze pełna sekunda.'
        : 'Projekt jest zarchiwizowany i nie można dodać do niego czasu.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się odczytać lub zapisać czasu. Odśwież stan przed ponowieniem operacji.')
  }
  return response.status === 204 ? null : response.json() as Promise<TimeEntry>
}

export const getActiveTimer = (signal?: AbortSignal) => timeRequest('/api/timer/active', undefined, signal)
export const startTimer = (body: StartTimerInput) => timeRequest('/api/timer/start', body) as Promise<TimeEntry>
export const stopTimer = (body: { entryId: string; description: string | null }) => timeRequest('/api/timer/stop', body) as Promise<TimeEntry>
export const changeTimerState = ({ action, ...body }: { action: 'pause' | 'resume'; entryId: string; description: string | null }) =>
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
      400: 'Sprawdź projekt, datę i dodatni czas trwania w pełnych sekundach.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Nie masz uprawnień do tej operacji lub sesja formularza wygasła.',
      404: 'Wpis został usunięty lub nie masz już dostępu do wpisu, projektu lub zespołu.',
      409: 'Wpis jest aktywnym timerem lub wybrany projekt jest archiwalny. Odśwież historię.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się zmienić wpisu. Odśwież historię przed ponowieniem operacji.')
  }
}

export const updateTimeEntry = ({ id, ...body }: UpdateTimeEntryInput & { id: string }) => changeTimeEntry(id, 'PATCH', body)
export const deleteTimeEntry = (id: string) => changeTimeEntry(id, 'DELETE')

export interface ReportFilters {
  from: string
  to: string
  userId: string
  projectId: string
  entryType: '' | 'TIMER' | 'MANUAL'
}

export interface ReportContext {
  teamId: string
  teamName: string
  timezone: string
  filter: { from: string; to: string; userIds: string[]; projectIds: string[]; entryType: 'TIMER' | 'MANUAL' | null } | null
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
  durationSeconds: number
  entryCount: number
}

export interface ReportMember {
  userId: string
  displayName: string
  role: 'OWNER' | 'MEMBER'
  joinedAt: string
  leftAt: string | null
}

export interface ReportProject {
  projectId: string
  name: string
  description: string | null
  createdAt: string
  archivedAt: string | null
}

export interface ReportEntry {
  id: string
  userId: string
  displayName: string
  projectId: string
  projectName: string
  projectArchivedAt: string | null
  entryType: 'TIMER' | 'MANUAL'
  workDate: string
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
  if (filters.from) params.set('from', filters.from)
  if (filters.to) params.set('to', filters.to)
  if (filters.userId) params.set('userIds', filters.userId)
  if (filters.projectId) params.set('projectIds', filters.projectId)
  if (filters.entryType) params.set('entryType', filters.entryType)
  if (page !== undefined) { params.set('page', String(page)); params.set('size', '20') }
  return params
}

async function reportRequest<T>(teamId: string, endpoint: string, params: URLSearchParams, signal?: AbortSignal): Promise<T> {
  const response = await request(`/api/teams/${encodeURIComponent(teamId)}/reports/${endpoint}?${params}`, { signal })
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Sprawdź zakres dat i filtry raportu.',
      401: 'Sesja wygasła. Zaloguj się ponownie.',
      403: 'Widok zespołu jest dostępny wyłącznie dla jego właściciela.',
      404: 'Zespół lub wybrane dane są niedostępne. Sprawdź swoje uprawnienia i filtry.',
    }
    throw new ApiError(response.status, messages[response.status] ?? 'Nie udało się wczytać raportu zespołu. Spróbuj ponownie.')
  }
  return response.json() as Promise<T>
}

export const getReportSummary = (teamId: string, filters: ReportFilters, signal?: AbortSignal) =>
  reportRequest<ReportSummary>(teamId, 'summary', reportParams(filters), signal)

export function getReportBreakdown(teamId: string, filters: ReportFilters, groupBy: 'MEMBER' | 'PROJECT', page: number, signal?: AbortSignal) {
  const params = reportParams(filters, page)
  params.set('groupBy', groupBy)
  return reportRequest<ReportResult<ReportBucket> & { groupBy: 'MEMBER' | 'PROJECT' }>(teamId, 'breakdown', params, signal)
}

export function getReportEntries(teamId: string, filters: ReportFilters, sort: ReportSort, page: number, signal?: AbortSignal) {
  const params = reportParams(filters, page)
  const [field, direction] = sort.split(':')
  params.set('sort', field!)
  params.set('direction', direction!)
  return reportRequest<ReportResult<ReportEntry>>(teamId, 'entries', params, signal)
}

export const getReportMembers = (teamId: string, page: number, signal?: AbortSignal) =>
  reportRequest<ReportResult<ReportMember>>(teamId, 'members', new URLSearchParams({ page: String(page), size: '100' }), signal)
export const getReportProjects = (teamId: string, page: number, signal?: AbortSignal) =>
  reportRequest<ReportResult<ReportProject>>(teamId, 'projects', new URLSearchParams({ page: String(page), size: '100' }), signal)
