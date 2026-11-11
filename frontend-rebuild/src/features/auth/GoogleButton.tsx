import { useT } from '../../content/i18n'

export function GoogleButton({ href }: { href: string }) {
  const t = useT()
  return <>
    <p className="auth-divider">{t('auth.or')}</p>
    <a className="google-button" href={href}>
      <img src={import.meta.env.BASE_URL + 'google-g.png'} alt="" width={20} height={20} />
      <span>{t('auth.google')}</span>
    </a>
  </>
}
