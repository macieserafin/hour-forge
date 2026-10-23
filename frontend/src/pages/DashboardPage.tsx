import { useEffect, useRef, useState } from 'react'
import { Clock3, Folder, LayoutDashboard, LoaderCircle, LogOut, Menu, PlugZap, ShieldCheck, Sparkles, UserRound, Users, X } from 'lucide-react'
import { Brand } from '../components/Brand'
import type { User } from '../lib/api'
import { useLogout } from '../lib/auth'
import { NavLink, useSearchParams } from 'react-router-dom'
import { TeamsPanel } from './TeamsPanel'
import { ProjectsPanel } from './ProjectsPanel'
import { TimePanel } from './TimePanel'
import { PersonalDashboard } from './PersonalDashboard'
import { AccountPanel } from './AccountPanel'
import { IntegrationsPanel } from './IntegrationsPanel'
import { WhatsNewPanel } from './WhatsNewPanel'
import { OperatorPanel } from './OperatorPanel'
import { AnnouncementBanner } from '../components/AnnouncementBanner'
import { unseenReleases } from '../lib/whatsNew'
import { TimerWatcher } from '../components/TimerWatcher'
import { usePreferences } from '../lib/preferences'
import { setTimeFormat } from '../lib/time'

function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('').toLocaleUpperCase('pl-PL') || 'U'
}

interface SidebarProps {
  user: User
  isLoggingOut: boolean
  onLogout: () => void
  onNavigate: () => void
}

function Sidebar({ user, isLoggingOut, onLogout, onNavigate }: SidebarProps) {
  const [search] = useSearchParams()
  const [unseen, setUnseen] = useState(() => unseenReleases(user.id).length)
  useEffect(() => {
    const refresh = () => setUnseen(unseenReleases(user.id).length)
    window.addEventListener('hourforge:whats-new-seen', refresh)
    return () => window.removeEventListener('hourforge:whats-new-seen', refresh)
  }, [user.id])
  const teamQuery = search.get('team') ? `?${new URLSearchParams({ team: search.get('team')! })}` : ''
  return <div className="sidebar-content">
    <div className="sidebar-brand"><Brand compact /></div>
    <p className="nav-caption">Panel użytkownika</p>
    <nav className="sidebar-nav" aria-label="Nawigacja główna">
      <NavLink to="/dashboard" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><LayoutDashboard size={18} />Pulpit</NavLink>
      <NavLink to={`/time${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><Clock3 size={18} />Rejestr czasu</NavLink>
      <NavLink to={`/projects${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><Folder size={18} />Projekty</NavLink>
      <NavLink to={`/teams${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><Users size={18} />Zespoły</NavLink>
      <NavLink to={`/integrations${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><PlugZap size={18} />Integracje</NavLink>
      <NavLink to="/account" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><UserRound size={18} />Konto</NavLink>
      {user.operator && <NavLink to="/operator" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><ShieldCheck size={18} />Operator</NavLink>}
    </nav>
    <NavLink to="/whats-new" className={({ isActive }) => `nav-item nav-whats-new ${isActive ? 'active' : ''}`} onClick={onNavigate}><Sparkles size={18} />Co nowego{unseen > 0 && <span className="nav-badge" aria-label={`Nowości: ${unseen}`}>{unseen}</span>}</NavLink>
    <div className="sidebar-bottom">
      <div className="sidebar-profile"><span className="avatar">{initials(user.displayName)}</span><div><strong title={user.displayName}>{user.displayName}</strong><span title={user.email}>{user.email}</span></div></div>
      <button className="logout-button" onClick={onLogout} disabled={isLoggingOut}>
        {isLoggingOut ? <LoaderCircle size={16} className="spin" /> : <LogOut size={16} />}{isLoggingOut ? 'Wylogowywanie…' : 'Wyloguj się'}
      </button>
    </div>
  </div>
}

export function DashboardPage({ user, section = 'dashboard' }: { user: User; section?: 'dashboard' | 'teams' | 'projects' | 'time' | 'integrations' | 'whats-new' | 'account' | 'operator' }) {
  const logout = useLogout()
  const [search] = useSearchParams()
  const menu = useRef<HTMLDialogElement>(null)
  const preferences = usePreferences(user.id)
  // Saved time is written the way the person chose; the content is rebuilt when that choice changes.
  const timeFormat = preferences.data?.timeFormat ?? 'CLOCK'
  setTimeFormat(timeFormat)
  if (preferences.isPending) return <div className="session-screen" aria-busy="true"><Brand /><div className="session-loader"><LoaderCircle className="spin" size={22} /><p role="status">Wczytywanie ustawień…</p></div></div>
  const firstName = user.displayName.trim().split(/\s+/)[0] || user.displayName
  const date = new Date().toLocaleDateString('pl-PL', { weekday: 'long', day: 'numeric', month: 'long' })
  const sectionTitle = section === 'teams' ? 'Zespoły' : section === 'projects' ? 'Projekty' : section === 'time' ? 'Rejestr czasu' : section === 'integrations' ? 'Integracje' : section === 'whats-new' ? 'Co nowego' : section === 'account' ? 'Konto' : section === 'operator' ? 'Operator instancji' : 'Pulpit'
  const sidebarProps: SidebarProps = { user, isLoggingOut: logout.isPending, onLogout: () => logout.mutate(), onNavigate: () => menu.current?.close() }

  return <div className="app-layout">
    <TimerWatcher userId={user.id} />
    <a className="skip-link" href="#main">Przejdź do treści</a>
    <aside className="sidebar desktop-sidebar"><Sidebar {...sidebarProps} /></aside>
    <dialog className="mobile-menu" ref={menu} aria-label="Menu nawigacyjne" onClick={event => { if (event.target === event.currentTarget) menu.current?.close() }}>
      <button className="mobile-menu-close icon-button" aria-label="Zamknij menu" onClick={() => menu.current?.close()}><X size={19} /></button>
      <Sidebar {...sidebarProps} />
    </dialog>
    <div className="workspace-main">
      <AnnouncementBanner />
      <header className="topbar">
        <div className="breadcrumbs"><button className="icon-button menu-trigger" aria-label="Otwórz menu" onClick={() => menu.current?.showModal()}><Menu size={20} /></button><strong>{sectionTitle}</strong></div>
        <div className="topbar-right"><span className="topbar-user">{user.displayName}</span><span className="avatar avatar-small" aria-label={user.displayName}>{initials(user.displayName)}</span></div>
      </header>

      <main id="main" className="dashboard-content">
        <div className="page-heading"><div><h1>{section === 'dashboard' ? `Cześć, ${firstName}.` : sectionTitle}</h1><p>{section === 'teams' ? 'Wspólna praca zaczyna się od zespołu.' : section === 'projects' ? 'Projekty i historia pracy Twojego zespołu.' : section === 'time' ? 'Uruchom timer lub dodaj czas wykonanej pracy.' : section === 'integrations' ? 'Połącz zespół z narzędziami, z których już korzystasz.' : section === 'whats-new' ? 'Zmiany i nowe możliwości w HourForge.' : section === 'account' ? 'Nazwa, hasło i bezpieczeństwo Twojego konta.' : section === 'operator' ? 'Statystyki, ogłoszenia i usuwanie nadużyć na tej instancji.' : 'To jest Twój pulpit w HourForge.'}</p></div><p className="date-label">{date}</p></div>
        {logout.isError && <div className="dashboard-error" role="alert">{logout.error.message}<button onClick={() => logout.mutate()}>Spróbuj ponownie</button></div>}

        {section === 'teams' ? <TeamsPanel userId={user.id} /> : section === 'projects' ? <ProjectsPanel userId={user.id} /> : section === 'time' ? <TimePanel userId={user.id} /> : section === 'whats-new' ? <WhatsNewPanel userId={user.id} /> : section === 'integrations' ? <IntegrationsPanel userId={user.id} /> : section === 'account' ? <AccountPanel user={user} /> : section === 'operator' ? <OperatorPanel userId={user.id} /> : <div className={`dashboard-overview${search.get('view') === 'team' ? ' team-overview' : ''}`}>
          <PersonalDashboard userId={user.id} />
          <section className="account-details" aria-labelledby="account-title">
            <h2 id="account-title">Twoje konto</h2>
            <dl>
              <dt>Nazwa użytkownika</dt><dd>{user.displayName}</dd>
              <dt>Adres e-mail</dt><dd className="account-email">{user.email}</dd>
            </dl>
          </section>
        </div>}
        <footer className="dashboard-footer"><span>HourForge</span><span>Panel użytkownika</span></footer>
      </main>
    </div>
  </div>
}
