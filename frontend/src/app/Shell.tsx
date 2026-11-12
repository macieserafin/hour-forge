import { useEffect, useRef, useState, type ReactNode } from 'react'
import {
  BarChart3,
  Clock3,
  FolderKanban,
  HelpCircle,
  LogOut,
  Settings,
  ShieldCheck,
  Sparkles,
  Square,
  Users,
} from 'lucide-react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { AnvilMark } from '../brand/Anvil'
import { useI18n, type Locale } from '../content/i18n'
import { formatClock, setDurationStyle } from '../domain/duration'
import { usePreferences } from '../lib/preferences'
import { avatarUrl, type User } from '../lib/api'
import { useLogout } from '../lib/auth'
import { timerElapsedSeconds, useActiveTimer } from '../lib/time'
import { useUser } from './session'
import { Announcement } from '../ui/Announcement'
import { LegalLinks } from '../features/legal/Legal'
import { EVENT, unseenCount } from '../content/releases'
import './shell.css'

/** The signed-in frame. Sidebar on wide screens, bottom bar on phones, a timer dock visible everywhere but My work. */
export function Shell() {
  const user = useUser()
  const { t, locale } = useI18n()
  const location = useLocation()
  const main = useRef<HTMLElement>(null)
  // Saved time is written the way the person chose; every screen renders inside this frame.
  setDurationStyle(usePreferences(user.id).data?.timeFormat ?? 'CLOCK', locale)
  const first = useRef(true)

  // Moving between sections puts focus at the top of the new content (keyboard and screen readers keep their place).
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    main.current?.focus({ preventScroll: true })
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  // On phones the section tabs are one scrollable row; keep the current tab in sight instead of cut off at the edge.
  useEffect(() => {
    const frame = requestAnimationFrame(() => {
      const active = main.current?.querySelector<HTMLElement>('.tabs [aria-current="page"]')
      const row = active?.parentElement
      if (!active || !row || row.scrollWidth <= row.clientWidth) return
      const offset = active.getBoundingClientRect().left - row.getBoundingClientRect().left
      row.scrollLeft += offset - (row.clientWidth - active.offsetWidth) / 2
    })
    return () => cancelAnimationFrame(frame)
  }, [location.pathname])

  const items: { to: string; label: string; icon: ReactNode }[] = [
    { to: '/app/work', label: t('nav.work'), icon: <Clock3 size={19} aria-hidden="true" /> },
    { to: '/app/projects', label: t('nav.projects'), icon: <FolderKanban size={19} aria-hidden="true" /> },
    { to: '/app/reports', label: t('nav.reports'), icon: <BarChart3 size={19} aria-hidden="true" /> },
    { to: '/app/teams', label: t('nav.teams'), icon: <Users size={19} aria-hidden="true" /> },
  ]

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        {t('app.skip')}
      </a>
      <nav className="nav" aria-label={t('nav.label')}>
        <Link className="brand" to="/app/work">
          <AnvilMark size={30} />
          <span>
            Hour<b>Forge</b>
          </span>
        </Link>
        <ul className="nav-list">
          {items.map(item => (
            <li key={item.to}>
              <NavLink to={item.to}>
                {item.icon}
                <span>{item.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="nav-end">
          <ul className="nav-list">
            <li>
              <NavLink to="/app/help">
                <HelpCircle size={19} aria-hidden="true" />
                <span>{t('nav.help')}</span>
              </NavLink>
            </li>
            {user.operator && (
              <li>
                <NavLink to="/app/operator">
                  <ShieldCheck size={19} aria-hidden="true" />
                  <span>{t('nav.operator')}</span>
                </NavLink>
              </li>
            )}
          </ul>
          <AccountMenu user={user} />
        </div>
      </nav>
      <main id="main" className="main" ref={main} tabIndex={-1}>
        <Announcement />
        <Outlet />
      </main>
      {location.pathname !== '/app/work' && <TimerDock />}
    </div>
  )
}

export function Avatar({ user }: { user: User }) {
  const initials = user.displayName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(part => part[0]?.toUpperCase())
    .join('')
  return (
    <span className="avatar" aria-hidden="true">
      {user.avatarUpdatedAt ? <img src={avatarUrl(user.id, user.avatarUpdatedAt)} alt="" /> : initials}
    </span>
  )
}

function AccountMenu({ user }: { user: User }) {
  const { t, locale, setLocale } = useI18n()
  const [open, setOpen] = useState(false)
  const logout = useLogout()
  const root = useRef<HTMLDivElement>(null)
  const [unseen, setUnseen] = useState(() => unseenCount(user.id))
  useEffect(() => {
    const refresh = () => setUnseen(unseenCount(user.id))
    window.addEventListener(EVENT, refresh)
    return () => window.removeEventListener(EVENT, refresh)
  }, [user.id])
  useEffect(() => {
    if (!open) return
    const close = (event: Event) => {
      if (event instanceof KeyboardEvent ? event.key === 'Escape' : !root.current?.contains(event.target as Node))
        setOpen(false)
    }
    document.addEventListener('pointerdown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('pointerdown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])
  return (
    <div ref={root}>
      <button
        type="button"
        className="account-button"
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={t('nav.account', { name: user.displayName })}
        onClick={() => setOpen(!open)}
      >
        <Avatar user={user} />
        <span className="account-text">
          <b>{user.displayName}</b>
          <small>{user.email}</small>
        </span>
        {unseen > 0 && <span className="dot-badge" role="img" aria-label={t('nav.whatsNew')} />}
      </button>
      {open && (
        <div className="menu" role="group" aria-label={t('nav.accountMenu')}>
          <Link to="/app/account" onClick={() => setOpen(false)}>
            <Settings size={17} aria-hidden="true" />
            {t('nav.settings')}
          </Link>
          <Link to="/app/whats-new" onClick={() => setOpen(false)}>
            <Sparkles size={17} aria-hidden="true" />
            {t('nav.whatsNew')}
            {unseen > 0 && <span className="count-badge">{unseen}</span>}
          </Link>
          <LegalLinks className="legal-links menu-legal" />
          <hr />
          <div className="menu-row">
            <span id="menu-language">{t('nav.language')}</span>
            <div className="segmented" role="group" aria-labelledby="menu-language">
              {(['pl', 'en'] as Locale[]).map(choice => (
                <button
                  key={choice}
                  type="button"
                  aria-pressed={locale === choice}
                  lang={choice}
                  onClick={() => setLocale(choice)}
                >
                  {choice === 'pl' ? 'Polski' : 'English'}
                </button>
              ))}
            </div>
          </div>
          <hr />
          <button type="button" onClick={() => logout.mutate()} disabled={logout.isPending}>
            <LogOut size={17} aria-hidden="true" />
            {t(logout.isPending ? 'nav.signingOut' : 'nav.signOut')}
          </button>
          {logout.isError && (
            <p className="field-error" role="alert">
              {logout.error.message}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

function TimerDock() {
  const user = useUser()
  const { t } = useI18n()
  const active = useActiveTimer(user.id)
  const [now, setNow] = useState(Date.now)
  const entry = active.data
  useEffect(() => {
    if (!entry || entry.pausedAt) return
    const tick = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(tick)
  }, [entry])
  if (!entry) return null
  return (
    <Link className="dock" to="/app/work" aria-label={t('dock.open')}>
      <span className={`dock-pulse ${entry.pausedAt ? 'is-paused' : ''}`} aria-hidden="true" />
      <span className="dock-text">
        <b>{t(entry.pausedAt ? 'dock.paused' : 'dock.running')}</b>
        {entry.description ?? ''}
      </span>
      <span className="num" aria-hidden="true">
        {formatClock(timerElapsedSeconds(entry, now))}
      </span>
      <span className="icon-btn" aria-hidden="true">
        <Square size={14} />
      </span>
    </Link>
  )
}
