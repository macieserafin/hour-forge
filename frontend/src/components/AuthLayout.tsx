import type { ReactNode } from 'react'
import { Brand } from './Brand'
import { AnnouncementBanner } from './AnnouncementBanner'

export function AuthLayout({ children }: { children: ReactNode }) {
  return <main className="login-layout">
    <section className="login-story" aria-label="HourForge — przestrzeń do pracy">
      <Brand />
      <div className="story-content">
        <p className="product-category">REJESTRACJA CZASU PRACY</p>
        <h1>Czas pracy<br />Twojego zespołu.</h1>
        <p>Rejestracja pracy nad projektami w jednym panelu, dla małych zespołów.</p>
      </div>
      <div className="story-footer"><span>HourForge</span><span>Panel użytkownika</span></div>
    </section>
    <section className="login-form-panel">
      <div className="login-top"><span className="mobile-brand"><Brand /></span><span className="login-context">Panel użytkownika</span></div>
      <AnnouncementBanner />
      <div className="login-form-wrap">{children}</div>
      <footer className="login-footer"><span>© {new Date().getFullYear()} HourForge</span></footer>
    </section>
  </main>
}
