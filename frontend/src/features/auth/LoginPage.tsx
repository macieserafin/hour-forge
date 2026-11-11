import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, Eye, EyeOff, KeyRound, LockKeyhole, Mail } from 'lucide-react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { useT } from '../../content/i18n'
import { ApiError, googleOutcome } from '../../lib/api'
import { useAnswerTwoFactor, useGoogleSignIn, useLogin, useResendVerification } from '../../lib/auth'
import { googleSignInUrl, registrationUrl, safeReturnTo } from '../../lib/navigation'
import { Button, Field, Notice } from '../../ui'
import { AuthLayout } from './AuthLayout'
import { GoogleButton } from './GoogleButton'

/** Sign-in: password, optional Google, two-step code, resend of the confirmation link; behaviour kept from the old app. */
export function LoginPage() {
  const t = useT()
  const location = useLocation()
  const [search] = useSearchParams()
  const registered = location.state?.registered === true
  const [email, setEmail] = useState(() =>
    registered && typeof location.state?.email === 'string' ? location.state.email : '',
  )
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)
  const [step, setStep] = useState<'password' | 'code'>('password')
  const [code, setCode] = useState('')
  const [restartReason, setRestartReason] = useState('')
  const [googleNoticeHidden, setGoogleNoticeHidden] = useState(false)
  const [urlStepTaken, setUrlStepTaken] = useState(false)
  const login = useLogin()
  const answer = useAnswerTwoFactor()
  const resend = useResendVerification()
  const google = useGoogleSignIn()
  const returnTo = search.get('returnTo')
  const isInvitation = safeReturnTo(returnTo).startsWith('/invite/')
  const googleNotice = google && !googleNoticeHidden ? googleOutcome(search.get('google'), 'login') : null

  // After Google the server may still ask for the code; it sends the browser here with ?step=two-factor.
  useEffect(() => {
    if (!google || urlStepTaken || search.get('step') !== 'two-factor') return
    setUrlStepTaken(true)
    setCode('')
    setStep('code')
  }, [google, urlStepTaken, search])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setRestartReason('')
    setGoogleNoticeHidden(true)
    login.mutate(
      { email, password, rememberMe },
      {
        onSuccess: result => {
          setPassword('')
          if (result === 'TWO_FACTOR_REQUIRED') {
            setCode('')
            setStep('code')
          }
        },
      },
    )
  }

  const submitCode = (event: FormEvent) => {
    event.preventDefault()
    if (!code.trim()) return
    answer.mutate(code, {
      onError: error => {
        if (error instanceof ApiError && error.code === 'SIGN_IN_AGAIN') {
          setRestartReason(error.message)
          setStep('password')
          answer.reset()
          login.reset()
        }
      },
    })
  }

  if (step === 'code')
    return (
      <AuthLayout>
        <h1>{t('auth.code.title')}</h1>
        <p className="muted">{t('auth.code.intro')}</p>
        <form onSubmit={submitCode} aria-label={t('auth.code.title')}>
          <Field label={t('auth.code.label')} error={answer.isError ? answer.error.message : undefined}>
            {props => (
              <div className="input-wrap">
                <KeyRound size={17} aria-hidden="true" />
                <input
                  {...props}
                  className="input"
                  name="code"
                  autoComplete="one-time-code"
                  inputMode="text"
                  autoFocus
                  required
                  maxLength={32}
                  placeholder="123456"
                  value={code}
                  disabled={answer.isPending}
                  onChange={event => {
                    setCode(event.target.value)
                    if (answer.isError) answer.reset()
                  }}
                />
              </div>
            )}
          </Field>
          <Button type="submit" variant="primary" wide pending={answer.isPending}>
            {answer.isPending ? (
              t('auth.code.checking')
            ) : (
              <>
                {t('auth.code.submit')}
                <ArrowRight size={17} aria-hidden="true" />
              </>
            )}
          </Button>
        </form>
        <p className="auth-alt">
          <button
            type="button"
            className="link-btn"
            onClick={() => {
              setStep('password')
              answer.reset()
            }}
          >
            {t('auth.code.back')}
          </button>
        </p>
      </AuthLayout>
    )

  const loginError = login.isError ? login.error.message : undefined
  return (
    <AuthLayout>
      <div>
        <h1>{t('auth.login.title')}</h1>
        <p className="muted">{t(isInvitation ? 'auth.login.introInvite' : 'auth.login.intro')}</p>
      </div>
      {googleNotice && (
        <Notice tone={googleNotice.tone === 'success' ? 'success' : 'error'}>{googleNotice.text}</Notice>
      )}
      {registered && <Notice tone="success">{t('auth.registered')}</Notice>}
      <form onSubmit={submit} aria-label={t('auth.login.title')}>
        <Field label={t('auth.email')}>
          {props => (
            <div className="input-wrap">
              <Mail size={17} aria-hidden="true" />
              <input
                {...props}
                aria-invalid={login.isError || undefined}
                className="input"
                name="email"
                type="email"
                autoComplete="username"
                placeholder="ty@firma.pl"
                required
                value={email}
                disabled={login.isPending}
                onChange={event => {
                  setEmail(event.target.value)
                  if (login.isError) login.reset()
                }}
              />
            </div>
          )}
        </Field>
        <Field label={t('auth.password')}>
          {props => (
            <div className="input-wrap">
              <LockKeyhole size={17} aria-hidden="true" />
              <input
                {...props}
                aria-invalid={login.isError || undefined}
                className="input"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                disabled={login.isPending}
                onChange={event => {
                  setPassword(event.target.value)
                  if (login.isError) login.reset()
                }}
              />
              <button
                type="button"
                className="icon-btn"
                aria-label={t(showPassword ? 'auth.hidePassword' : 'auth.showPassword')}
                aria-pressed={showPassword}
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </div>
          )}
        </Field>
        <div className="auth-row">
          <label className="check">
            <input
              type="checkbox"
              checked={rememberMe}
              disabled={login.isPending}
              onChange={event => setRememberMe(event.target.checked)}
            />
            {t('auth.remember')}
          </label>
          <Link className="text-link" to="/forgot-password">
            {t('auth.forgot')}
          </Link>
        </div>
        {restartReason && !login.isError && <Notice tone="error">{restartReason}</Notice>}
        {loginError && (
          <Notice
            tone="error"
            action={
              (login.error as ApiError).code === 'EMAIL_NOT_VERIFIED' ? (
                <div>
                  <button
                    type="button"
                    className="link-btn"
                    disabled={resend.isPending}
                    onClick={() => resend.mutate(email)}
                  >
                    {t('auth.resend')}
                  </button>
                  {resend.isSuccess && <p role="status">{t('auth.resent')}</p>}
                  {resend.isError && <p>{resend.error.message}</p>}
                </div>
              ) : undefined
            }
          >
            {loginError}
          </Notice>
        )}
        <Button type="submit" variant="primary" wide pending={login.isPending}>
          {login.isPending ? (
            t('auth.submitting')
          ) : (
            <>
              {t('auth.submit')}
              <ArrowRight size={17} aria-hidden="true" />
            </>
          )}
        </Button>
      </form>
      {google && <GoogleButton href={googleSignInUrl({ rememberMe, returnTo })} />}
      <p className="auth-alt">
        {t('auth.noAccount')}{' '}
        <Link className="text-link" to={registrationUrl(safeReturnTo(returnTo))}>
          {t('auth.register')}
        </Link>
      </p>
    </AuthLayout>
  )
}
