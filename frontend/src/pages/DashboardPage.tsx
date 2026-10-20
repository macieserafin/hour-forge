import { useRef } from 'react'
import { Clock3, Folder, LayoutDashboard, LoaderCircle, LogOut, Menu, UserRound, Users, X } from 'lucide-react'
import { Brand } from '../components/Brand'
import type { User } from '../lib/api'
import { useLogout } from '../lib/auth'
import { NavLink, useSearchParams } from 'react-router-dom'
import { TeamsPanel } from './TeamsPanel'
import { ProjectsPanel } from './ProjectsPanel'
import { TimePanel } from './TimePanel'
import { PersonalDashboard } from './PersonalDashboard'
import { AccountPanel } from './AccountPanel'

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
  const teamQuery = search.get('team') ? `?${new URLSearchParams({ team: search.get('team')! })}` : ''
  return <div className="sidebar-content">
    <div className="sidebar-brand"><Brand compact /></div>
    <p className="nav-caption">Panel użytkownika</p>
    <nav className="sidebar-nav" aria-label="Nawigacja główna">
      <NavLink to="/dashboard" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><LayoutDashboard size={18} />Pulpit</NavLink>
      <NavLink to={`/time${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><Clock3 size={18} />Rejestr czasu</NavLink>
      <NavLink to={`/projects${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><Folder size={18} />Projekty</NavLink>
      <NavLink to={`/teams${teamQuery}`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><Users size={18} />Zespoły</NavLink>
      <NavLink to="/account" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`} onClick={onNavigate}><UserRound size={18} />Konto</NavLink>
    </nav>
    <div className="sidebar-bottom">
      <div className="sidebar-profile"><span className="avatar">{initials(user.displayName)}</span><div><strong title={user.displayName}>{user.displayName}</strong><span title={user.email}>{user.email}</span></div></div>
      <button className="logout-button" onClick={onLogout} disabled={isLoggingOut}>
        {isLoggingOut ? <LoaderCircle size={16} className="spin" /> : <LogOut size={16} />}{isLoggingOut ? 'Wylogowywanie…' : 'Wyloguj się'}
      </button>
    </div>
  </div>
}

export function DashboardPage({ user, section = 'dashboard' }: { user: User; section?: 'dashboard' | 'teams' | 'projects' | 'time' | 'account' }) {
  const logout = useLogout()
  const [search] = useSearchParams()
  const menu = useRef<HTMLDialogElement>(null)
  const firstName = user.displayName.trim().split(/\s+/)[0] || user.displayName
  const date = new Date().toLocaleDateString('pl-PL', { weekday: 'long', day: 'numeric', month: 'long' })
  const sectionTitle = section === 'teams' ? 'Zespoły' : section === 'projects' ? 'Projekty' : section === 'time' ? 'Rejestr czasu' : section === 'account' ? 'Konto' : 'Pulpit'
  const sidebarProps: SidebarProps = { user, isLoggingOut: logout.isPending, onLogout: () => logout.mutate(), onNavigate: () => menu.current?.close() }

  return <div className="app-layout">
    <a className="skip-link" href="#main">Przejdź do treści</a>
    <aside className="sidebar desktop-sidebar"><Sidebar {...sidebarProps} /></aside>
    <dialog className="mobile-menu" ref={menu} aria-label="Menu nawigacyjne" onClick={event => { if (event.target === event.currentTarget) menu.current?.close() }}>
      <button className="mobile-menu-close icon-button" aria-label="Zamknij menu" onClick={() => menu.current?.close()}><X size={19} /></button>
      <Sidebar {...sidebarProps} />
    </dialog>
    <div className="workspace-main">
      <header className="topbar">
        <div className="breadcrumbs"><button className="icon-button menu-trigger" aria-label="Otwórz menu" onClick={() => menu.current?.showModal()}><Menu size={20} /></button><strong>{sectionTitle}</strong></div>
        <div className="topbar-right"><span className="topbar-user">{user.displayName}</span><span className="avatar avatar-small" aria-label={user.displayName}>{initials(user.displayName)}</span></div>
      </header>

      <main id="main" className="dashboard-content">
        <div className="page-heading"><div><h1>{section === 'dashboard' ? `Cześć, ${firstName}.` : sectionTitle}</h1><p>{section === 'teams' ? 'Wspólna praca zaczyna się od zespołu.' : section === 'projects' ? 'Projekty i historia pracy Twojego zespołu.' : section === 'time' ? 'Uruchom timer lub dodaj czas wykonanej pracy.' : section === 'account' ? 'Nazwa, hasło i bezpieczeństwo Twojego konta.' : 'To jest Twój pulpit w HourForge.'}</p></div><p className="date-label">{date}</p></div>
        {logout.isError && <div className="dashboard-error" role="alert">{logout.error.message}<button onClick={() => logout.mutate()}>Spróbuj ponownie</button></div>}

        {section === 'teams' ? <TeamsPanel userId={user.id} /> : section === 'projects' ? <ProjectsPanel userId={user.id} /> : section === 'time' ? <TimePanel userId={user.id} /> : section === 'account' ? <AccountPanel user={user} /> : <div className={`dashboard-overview${search.get('view') === 'team' ? ' team-overview' : ''}`}>
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
