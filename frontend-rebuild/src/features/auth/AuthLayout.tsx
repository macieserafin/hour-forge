import type { ReactNode } from 'react'
import { AnvilMark } from '../../brand/Anvil'
import { useI18n } from '../../content/i18n'
import { Announcement } from '../../ui/Announcement'
import './auth.css'

/** U02/U03 frame: the brand story beside the form on wide screens, the form alone on phones. */
export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useI18n()
  return <div className="auth">
    <aside className="auth-story" aria-hidden="true">
      <span className="brand"><AnvilMark size={34} /><span>Hour<b>Forge</b></span></span>
      <div className="auth-story-text">
        <p className="auth-tagline">{t('auth.tagline')}</p>
        <p>{t('auth.story')}</p>
      </div>
      <div className="auth-sparks"><span /><span /><span /></div>
    </aside>
    <main className="auth-main" id="main">
      <Announcement />
      <span className="brand auth-mobile-brand"><AnvilMark size={30} /><span>Hour<b>Forge</b></span></span>
      <div className="auth-form">{children}</div>
    </main>
  </div>
}
