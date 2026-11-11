import type { ReactNode } from 'react'
import { ArrowUpRight, Check } from 'lucide-react'
import { AnvilMark } from '../../brand/Anvil'
import { useI18n, type MessageKey } from '../../content/i18n'
import { Announcement } from '../../ui/Announcement'
import authorPhoto from '../../assets/maciej-serafin.jpg'
import './auth.css'

const points: MessageKey[] = ['auth.point.track', 'auth.point.teams', 'auth.point.reports']
export const authorSite = 'https://macieserafin.pl'
export const authorGitHub = 'https://github.com/macieserafin'

/** Frame of the sign-in pages: the brand story beside the form on wide screens, the form alone on phones. */
export function AuthLayout({ children }: { children: ReactNode }) {
  const { t } = useI18n()
  return (
    <div className="auth">
      <aside className="auth-story">
        <span className="brand" aria-hidden="true">
          <AnvilMark size={34} />
          <span>
            Hour<b>Forge</b>
          </span>
        </span>
        <div className="auth-story-text" aria-hidden="true">
          <p className="auth-kicker">
            <span className="auth-sparks">
              <span />
              <span />
              <span />
            </span>
            {t('auth.kicker')}
          </p>
          <p className="auth-tagline">{t('auth.tagline')}</p>
          <p className="auth-lead">{t('auth.story')}</p>
          <ul className="auth-points">
            {points.map(key => (
              <li key={key}>
                <Check size={16} strokeWidth={2.5} />
                {t(key)}
              </li>
            ))}
          </ul>
        </div>
        <AuthorCredit />
      </aside>
      <main className="auth-main" id="main">
        <Announcement />
        <span className="brand auth-mobile-brand">
          <AnvilMark size={30} />
          <span>
            Hour<b>Forge</b>
          </span>
        </span>
        <div className="auth-form">{children}</div>
        <AuthorCredit mobile />
      </main>
    </div>
  )
}

/** Small glass card crediting the author; shown in the story panel on wide screens and under the form on phones. */
function AuthorCredit({ mobile = false }: { mobile?: boolean }) {
  const { t } = useI18n()
  return (
    <footer className={`auth-credit${mobile ? ' auth-credit-mobile' : ''}`}>
      <img
        className="auth-credit-photo"
        src={authorPhoto}
        alt={t('auth.credit.photo')}
        width={64}
        height={64}
        loading="lazy"
        decoding="async"
      />
      <div className="auth-credit-body">
        <p className="auth-credit-by">
          {t('auth.credit.by')} <strong>Maciej Serafin</strong>
        </p>
        <p className="auth-credit-links">
          <a href={authorSite} target="_blank" rel="noopener noreferrer" aria-label={t('auth.credit.site')}>
            macieserafin.pl
            <ArrowUpRight size={14} aria-hidden="true" />
          </a>
          <a href={authorGitHub} target="_blank" rel="noopener noreferrer" aria-label={t('auth.credit.github')}>
            GitHub
            <ArrowUpRight size={14} aria-hidden="true" />
          </a>
        </p>
      </div>
    </footer>
  )
}
