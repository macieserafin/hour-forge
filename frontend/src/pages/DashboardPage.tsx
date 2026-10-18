import { useRef } from 'react'
import { Clock3, Folder, LayoutDashboard, LoaderCircle, LogOut, Menu, Users, X } from 'lucide-react'
import { Brand } from '../components/Brand'
import type { User } from '../lib/api'
import { useLogout } from '../lib/auth'

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
  return <div className="sidebar-content">
    <div className="sidebar-brand"><Brand compact /></div>
    <p className="nav-caption">Panel użytkownika</p>
    <nav className="sidebar-nav" aria-label="Nawigacja główna">
      <a href="/dashboard" className="nav-item active" aria-current="page" onClick={event => { event.preventDefault(); onNavigate() }}><LayoutDashboard size={18} />Pulpit</a>
      <button className="nav-item" disabled title="Dostępne wkrótce"><Clock3 size={18} />Rejestr czasu</button>
      <button className="nav-item" disabled title="Dostępne wkrótce"><Folder size={18} />Projekty</button>
      <button className="nav-item" disabled title="Dostępne wkrótce"><Users size={18} />Zespół</button>
    </nav>
    <div className="sidebar-bottom">
      <div className="sidebar-profile"><span className="avatar">{initials(user.displayName)}</span><div><strong title={user.displayName}>{user.displayName}</strong><span title={user.email}>{user.email}</span></div></div>
      <button className="logout-button" onClick={onLogout} disabled={isLoggingOut}>
        {isLoggingOut ? <LoaderCircle size={16} className="spin" /> : <LogOut size={16} />}{isLoggingOut ? 'Wylogowywanie…' : 'Wyloguj się'}
      </button>
    </div>
  </div>
}

export function DashboardPage({ user }: { user: User }) {
  const logout = useLogout()
  const menu = useRef<HTMLDialogElement>(null)
  const firstName = user.displayName.trim().split(/\s+/)[0] || user.displayName
  const date = new Date().toLocaleDateString('pl-PL', { weekday: 'long', day: 'numeric', month: 'long' })
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
        <div className="breadcrumbs"><button className="icon-button menu-trigger" aria-label="Otwórz menu" onClick={() => menu.current?.showModal()}><Menu size={20} /></button><strong>Pulpit</strong></div>
        <div className="topbar-right"><span className="topbar-user">{user.displayName}</span><span className="avatar avatar-small" aria-label={user.displayName}>{initials(user.displayName)}</span></div>
      </header>

      <main id="main" className="dashboard-content">
        <div className="page-heading"><div><h1>Cześć, {firstName}.</h1><p>To jest Twój pulpit w HourForge.</p></div><p className="date-label">{date}</p></div>
        {logout.isError && <div className="dashboard-error" role="alert">{logout.error.message}<button onClick={() => logout.mutate()}>Spróbuj ponownie</button></div>}

        <div className="dashboard-overview">
          <section className="overview-panel" aria-labelledby="overview-title">
            <header className="section-heading"><h2 id="overview-title">Przegląd pracy</h2></header>
            <div className="empty-state">
              <Clock3 size={28} strokeWidth={1.4} aria-hidden="true" />
              <h3>Pulpit czeka na pierwsze moduły.</h3>
              <p>Rejestrowanie czasu, projekty i zarządzanie zespołem są w przygotowaniu.</p>
            </div>
          </section>
          <section className="account-details" aria-labelledby="account-title">
            <h2 id="account-title">Twoje konto</h2>
            <dl>
              <dt>Nazwa użytkownika</dt><dd>{user.displayName}</dd>
              <dt>Adres e-mail</dt><dd className="account-email">{user.email}</dd>
            </dl>
          </section>
        </div>
        <footer className="dashboard-footer"><span>HourForge</span><span>Panel użytkownika</span></footer>
      </main>
    </div>
  </div>
}
