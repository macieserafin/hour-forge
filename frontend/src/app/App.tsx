import { lazy, Suspense, type ReactNode } from 'react'
import { AlertCircle, Compass, RefreshCw, ShieldOff } from 'lucide-react'
import { Link, Navigate, Route, Routes, useLocation, useSearchParams } from 'react-router-dom'
import { AnvilMark } from '../brand/Anvil'
import { useT } from '../content/i18n'
import { useSession } from '../lib/auth'
import { HOME, loginUrl, safeReturnTo } from '../lib/navigation'
import { startPaths, usePreferences } from '../lib/preferences'
import { Button, Loading } from '../ui'
import { UserProvider, useUser } from './session'
import { Shell } from './Shell'
import { LoginPage } from '../features/auth/LoginPage'
import {
  ConfirmAccountDeletionPage,
  ConfirmEmailChangePage,
  ForgotPasswordPage,
  InvitePage,
  RegisterPage,
  ResetPasswordPage,
  UnsubscribePage,
  VerifyEmailPage,
} from '../features/auth/AccessPages'

const MyWork = lazy(() => import('../features/work/MyWork').then(module => ({ default: module.MyWork })))
const History = lazy(() => import('../features/history/History').then(module => ({ default: module.History })))
const Projects = lazy(() => import('../features/projects/Projects').then(module => ({ default: module.Projects })))
const ProjectDetail = lazy(() =>
  import('../features/projects/Projects').then(module => ({ default: module.ProjectDetail })),
)
const teamsModule = () => import('../features/teams/Teams')
const peopleModule = () => import('../features/teams/People')
const TeamsList = lazy(() => teamsModule().then(module => ({ default: module.TeamsList })))
const TeamLayout = lazy(() => teamsModule().then(module => ({ default: module.TeamLayout })))
const TeamSettings = lazy(() => teamsModule().then(module => ({ default: module.TeamSettings })))
const TeamTags = lazy(() => teamsModule().then(module => ({ default: module.TeamTags })))
const TeamMembers = lazy(() => peopleModule().then(module => ({ default: module.TeamMembers })))
const TeamInvitations = lazy(() => peopleModule().then(module => ({ default: module.TeamInvitations })))
const TeamJira = lazy(() => import('../features/teams/Jira').then(module => ({ default: module.TeamJira })))
const MyReport = lazy(() => import('../features/reports/MyReport').then(module => ({ default: module.MyReport })))
const TeamReport = lazy(() => import('../features/reports/TeamReport').then(module => ({ default: module.TeamReport })))
const accountModule = () => import('../features/account/Account')
const AccountLayout = lazy(() => accountModule().then(module => ({ default: module.AccountLayout })))
const ProfilePage = lazy(() => accountModule().then(module => ({ default: module.ProfilePage })))
const PreferencesPage = lazy(() => accountModule().then(module => ({ default: module.PreferencesPage })))
const SecurityPage = lazy(() => accountModule().then(module => ({ default: module.SecurityPage })))
const DataPage = lazy(() => accountModule().then(module => ({ default: module.DataPage })))
const helpModule = () => import('../features/help/Help')
const Help = lazy(() => helpModule().then(module => ({ default: module.Help })))
const WhatsNew = lazy(() => helpModule().then(module => ({ default: module.WhatsNew })))
const Operator = lazy(() => import('../features/operator/Operator').then(module => ({ default: module.Operator })))
const StartPage = lazy(() => import('../features/start/StartPage').then(module => ({ default: module.StartPage })))

/** Routing and the session gate. Old addresses from e-mails, bookmarks and Google keep working. */
export function App() {
  const t = useT()
  const session = useSession()
  const location = useLocation()
  const [search] = useSearchParams()

  if (session.isPending)
    return (
      <Centered>
        <Loading label={t('app.loadingAccount')} />
      </Centered>
    )
  if (session.isError)
    return (
      <Centered>
        <div className="card">
          <AlertCircle size={28} aria-hidden="true" />
          <h1>{t('app.accountError.title')}</h1>
          <p className="muted">{t('app.accountError.body')}</p>
          <Button variant="primary" onClick={() => void session.refetch()} pending={session.isFetching}>
            <RefreshCw size={16} aria-hidden="true" />
            {t('common.retry')}
          </Button>
        </div>
      </Centered>
    )

  const user = session.data
  const signIn = <Navigate to={loginUrl(location.pathname + location.search)} replace />
  const signedIn = (page: ReactNode) => (user ? <UserProvider user={user}>{page}</UserProvider> : signIn)
  const afterSignIn = search.get('returnTo') ? (
    <Navigate to={safeReturnTo(search.get('returnTo'))} replace />
  ) : user ? (
    <UserProvider user={user}>
      <StartRedirect />
    </UserProvider>
  ) : null

  return (
    <Routes>
      <Route path="/login" element={user ? afterSignIn : <LoginPage />} />
      <Route path="/register" element={user ? afterSignIn : <RegisterPage />} />
      <Route path="/forgot-password" element={user ? <Navigate to={HOME} replace /> : <ForgotPasswordPage />} />
      <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
      <Route path="/verify-email/:token" element={<VerifyEmailPage />} />
      <Route path="/confirm-email-change/:token" element={<ConfirmEmailChangePage />} />
      <Route path="/confirm-account-deletion/:token" element={<ConfirmAccountDeletionPage />} />
      <Route path="/unsubscribe/:token" element={<UnsubscribePage />} />
      <Route path="/invite/:token" element={user ? <InvitePage user={user} /> : signIn} />

      <Route path="/app" element={signedIn(<Shell />)}>
        <Route index element={<Navigate to={HOME} replace />} />
        <Route
          path="start"
          element={
            <Lazy>
              <StartPage />
            </Lazy>
          }
        />
        <Route
          path="work"
          element={
            <Lazy>
              <MyWork />
            </Lazy>
          }
        />
        <Route
          path="work/history"
          element={
            <Lazy>
              <History />
            </Lazy>
          }
        />
        <Route
          path="projects"
          element={
            <Lazy>
              <Projects />
            </Lazy>
          }
        />
        <Route
          path="teams/:teamId/projects/:projectId"
          element={
            <Lazy>
              <ProjectDetail />
            </Lazy>
          }
        />
        <Route
          path="reports"
          element={
            <Lazy>
              <MyReport />
            </Lazy>
          }
        />
        <Route path="reports/mine" element={<Navigate to="/app/reports" replace />} />
        <Route
          path="teams"
          element={
            <Lazy>
              <TeamsList />
            </Lazy>
          }
        />
        <Route
          path="teams/:teamId/reports"
          element={
            <Lazy>
              <TeamReport />
            </Lazy>
          }
        />
        <Route
          path="teams/:teamId"
          element={
            <Lazy>
              <TeamLayout />
            </Lazy>
          }
        >
          <Route
            index
            element={
              <Lazy>
                <TeamMembers />
              </Lazy>
            }
          />
          <Route
            path="invitations"
            element={
              <Lazy>
                <TeamInvitations />
              </Lazy>
            }
          />
          <Route
            path="tags"
            element={
              <Lazy>
                <TeamTags />
              </Lazy>
            }
          />
          <Route
            path="integrations"
            element={
              <Lazy>
                <TeamJira />
              </Lazy>
            }
          />
          <Route
            path="settings"
            element={
              <Lazy>
                <TeamSettings />
              </Lazy>
            }
          />
        </Route>
        <Route
          path="account"
          element={
            <Lazy>
              <AccountLayout />
            </Lazy>
          }
        >
          <Route
            index
            element={
              <Lazy>
                <ProfilePage />
              </Lazy>
            }
          />
          <Route
            path="preferences"
            element={
              <Lazy>
                <PreferencesPage />
              </Lazy>
            }
          />
          <Route path="notifications" element={<Navigate to="/app/account/preferences#notifications" replace />} />
          <Route
            path="security"
            element={
              <Lazy>
                <SecurityPage />
              </Lazy>
            }
          />
          <Route path="sessions" element={<Navigate to="/app/account/security#sessions" replace />} />
          <Route path="integrations" element={<Navigate to="/app/teams" replace />} />
          <Route
            path="data"
            element={
              <Lazy>
                <DataPage />
              </Lazy>
            }
          />
        </Route>
        <Route
          path="help"
          element={
            <Lazy>
              <Help />
            </Lazy>
          }
        />
        <Route
          path="whats-new"
          element={
            <Lazy>
              <WhatsNew />
            </Lazy>
          }
        />
        <Route
          path="operator"
          element={
            <OperatorOnly>
              <Lazy>
                <Operator />
              </Lazy>
            </OperatorOnly>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Route>

      {/* Addresses of the previous frontend; the server's Google return list still uses them. */}
      <Route path="/dashboard" element={<Legacy />} />
      <Route path="/time" element={<Legacy />} />
      <Route path="/projects" element={<Legacy />} />
      <Route path="/teams" element={<Legacy />} />
      <Route path="/integrations" element={<Legacy />} />
      <Route path="/whats-new" element={<Legacy />} />
      <Route path="/account" element={<Legacy />} />
      <Route path="/operator" element={<Legacy />} />
      <Route path="/" element={user ? <Navigate to={HOME} replace /> : <Navigate to="/login" replace />} />
      <Route
        path="*"
        element={
          user ? (
            <UserProvider user={user}>
              <Shell />
            </UserProvider>
          ) : (
            <Navigate to="/login" replace />
          )
        }
      >
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

function Lazy({ children }: { children: ReactNode }) {
  return (
    <Suspense
      fallback={
        <div className="page">
          <Loading />
        </div>
      }
    >
      {children}
    </Suspense>
  )
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <main className="screen-center" id="main">
      <AnvilMark size={40} />
      {children}
    </main>
  )
}

/** The start page chosen in the preferences; old values all lead into the new app. */
function StartRedirect() {
  const user = useUser()
  const t = useT()
  const preferences = usePreferences(user.id)
  if (preferences.isPending)
    return (
      <Centered>
        <Loading label={t('common.loading')} />
      </Centered>
    )
  return <Navigate to={startPaths[preferences.data?.startPage ?? 'DASHBOARD']} replace />
}

/** Maps an address of the previous frontend, with its filters, to the new structure. */
export function legacyTarget(pathname: string, search: URLSearchParams, hash = ''): string {
  return legacyPath(pathname, search, hash) + hash
}

/** The old account was one page; its sections linked from e-mails (#google, #sessions) now live under Security. */
const accountPageOf: Record<string, string> = {
  '#google': '/security',
  '#sessions': '/security',
  '#notifications': '/preferences',
}

function legacyPath(pathname: string, search: URLSearchParams, hash: string): string {
  const team = search.get('team')
  const rest = new URLSearchParams(search)
  rest.delete('team')
  rest.delete('view')
  const query = (params: URLSearchParams) => (params.toString() ? `?${params}` : '')
  switch (pathname) {
    case '/dashboard':
      if (search.get('view') === 'team' && team) return `/app/teams/${encodeURIComponent(team)}/reports${query(rest)}`
      return `/app/work${team ? `?team=${encodeURIComponent(team)}` : ''}`
    case '/time':
      return `/app/work${team ? `?team=${encodeURIComponent(team)}` : ''}`
    case '/projects':
      return `/app/projects${team ? `?team=${encodeURIComponent(team)}` : ''}`
    case '/teams':
      return team ? `/app/teams/${encodeURIComponent(team)}` : '/app/teams'
    case '/integrations':
      return team ? `/app/teams/${encodeURIComponent(team)}/integrations${query(rest)}` : '/app/teams'
    case '/whats-new':
      return '/app/whats-new'
    case '/account':
      return `/app/account${rest.has('google') ? '/security' : (accountPageOf[hash] ?? '')}${query(rest)}`
    case '/operator':
      return '/app/operator'
    default:
      return HOME
  }
}

function Legacy() {
  const location = useLocation()
  return <Navigate to={legacyTarget(location.pathname, new URLSearchParams(location.search), location.hash)} replace />
}

function OperatorOnly({ children }: { children: ReactNode }) {
  const user = useUser()
  const t = useT()
  if (user.operator) return <>{children}</>
  return (
    <div className="page">
      <div className="empty">
        <ShieldOff size={28} aria-hidden="true" />
        <h1>{t('noAccess.title')}</h1>
        <p className="muted">{t('noAccess.body')}</p>
        <Link className="btn btn-primary" to={HOME}>
          {t('notFound.action')}
        </Link>
      </div>
    </div>
  )
}

function NotFound() {
  const t = useT()
  return (
    <div className="page">
      <div className="empty">
        <Compass size={28} aria-hidden="true" />
        <h1>{t('notFound.title')}</h1>
        <p className="muted">{t('notFound.body')}</p>
        <Link className="btn btn-primary" to={HOME}>
          {t('notFound.action')}
        </Link>
      </div>
    </div>
  )
}
