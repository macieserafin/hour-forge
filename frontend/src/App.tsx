import { AlertCircle, LoaderCircle, RefreshCw } from 'lucide-react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Brand } from './components/Brand'
import { useSession } from './lib/auth'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'

export function App() {
  const session = useSession()

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
  return <Routes>
    <Route path="/login" element={user ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
    <Route path="/dashboard" element={user ? <DashboardPage user={user} /> : <Navigate to="/login" replace />} />
    <Route path="*" element={<Navigate to={user ? '/dashboard' : '/login'} replace />} />
  </Routes>
}
