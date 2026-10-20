import { AlertCircle, LoaderCircle, RefreshCw } from 'lucide-react'
import { Navigate, Route, Routes, useLocation, useSearchParams } from 'react-router-dom'
import { Brand } from './components/Brand'
import { useSession } from './lib/auth'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { InvitePage } from './pages/InvitePage'
import { VerifyEmailPage } from './pages/VerifyEmailPage'
import { ConfirmEmailChangePage } from './pages/ConfirmEmailChangePage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { loginUrl, safeReturnTo } from './lib/navigation'

export function App() {
  const session = useSession()
  const location = useLocation()
  const [search] = useSearchParams()

  if (session.isPending) return <div className="session-screen" aria-busy="true">
    <Brand /><div className="session-loader"><LoaderCircle className="spin" size={22} />
      <p role="status">Wczytywanie konta…</p></div>
  </div>

  if (session.isError) return <main className="session-screen">
    <Brand /><section className="connection-card">
      <span className="error-icon"><AlertCircle size={24} /></span>
      <h1>Nie udało się wczytać konta</h1>
      <p>Nie możemy teraz wczytać Twojego konta. Sprawdź połączenie i spróbuj ponownie.</p>
      <button className="button-primary" onClick={() => void session.refetch()} disabled={session.isFetching}>
        <RefreshCw size={16} className={session.isFetching ? 'spin' : ''} />Spróbuj ponownie
      </button>
    </section>
  </main>

  const user = session.data
  const signIn = <Navigate to={loginUrl(location.pathname + location.search)} replace />
  return <Routes>
    <Route path="/login" element={user ? <Navigate to={safeReturnTo(search.get('returnTo'))} replace /> : <LoginPage />} />
    <Route path="/register" element={user ? <Navigate to={safeReturnTo(search.get('returnTo'))} replace /> : <RegisterPage />} />
    <Route path="/dashboard" element={user ? <DashboardPage user={user} /> : signIn} />
    <Route path="/teams" element={user ? <DashboardPage user={user} section="teams" /> : signIn} />
    <Route path="/projects" element={user ? <DashboardPage user={user} section="projects" /> : signIn} />
    <Route path="/time" element={user ? <DashboardPage user={user} section="time" /> : signIn} />
    <Route path="/verify-email/:token" element={<VerifyEmailPage />} />
    <Route path="/confirm-email-change/:token" element={<ConfirmEmailChangePage />} />
    <Route path="/forgot-password" element={user ? <Navigate to="/dashboard" replace /> : <ForgotPasswordPage />} />
    <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
    <Route path="/account" element={user ? <DashboardPage user={user} section="account" /> : signIn} />
    <Route path="/invite/:token" element={user ? <InvitePage user={user} /> : signIn} />
    <Route path="*" element={<Navigate to={user ? '/dashboard' : '/login'} replace />} />
  </Routes>
}
