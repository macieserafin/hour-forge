import { useState, type FormEvent } from 'react'
import { LoaderCircle } from 'lucide-react'
import { Modal } from '../components/Modal'
import type { User } from '../lib/api'
import { passwordChangeError, useChangePassword, useSignOutEverywhere, useUpdateProfile } from '../lib/account'

function ProfileSection({ user }: { user: User }) {
  const save = useUpdateProfile()
  const [name, setName] = useState(user.displayName)
  const [validation, setValidation] = useState('')
  const [saved, setSaved] = useState(false)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed || trimmed.length > 255) { setValidation('Wpisz nazwę wyświetlaną (maksymalnie 255 znaków).'); return }
    setValidation(''); setSaved(false)
    save.mutate(trimmed, { onSuccess: () => setSaved(true) })
  }
  return <section className="overview-panel account-section" aria-labelledby="account-profile-title">
    <h2 id="account-profile-title">Profil</h2>
    <p className="field-hint">Adres e-mail: <strong>{user.email}</strong></p>
    <form onSubmit={submit} onInput={() => { setValidation(''); setSaved(false); if (save.isError) save.reset() }}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="account-name">Nazwa wyświetlana</label>
          <input className="text-input" id="account-name" autoComplete="name" maxLength={255} required value={name} onChange={event => setName(event.target.value)} /></div>
      </fieldset>
      {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
      {saved && <p className="project-notice" role="status">Nazwa wyświetlana została zapisana.</p>}
      <button className="button-primary" type="submit" disabled={save.isPending || name.trim() === user.displayName}>
        {save.isPending && <LoaderCircle size={16} className="spin" />}{save.isPending ? 'Zapisywanie…' : 'Zapisz nazwę'}</button>
    </form>
  </section>
}

function PasswordSection() {
  const change = useChangePassword()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [validation, setValidation] = useState('')
  const [done, setDone] = useState(false)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = passwordChangeError(current, next, confirmation)
    setValidation(message); setDone(false)
    if (message) return
    change.mutate({ currentPassword: current, newPassword: next }, {
      onSuccess: () => { setCurrent(''); setNext(''); setConfirmation(''); setDone(true) },
    })
  }
  return <section className="overview-panel account-section" aria-labelledby="account-password-title">
    <h2 id="account-password-title">Zmiana hasła</h2>
    <p className="field-hint">Po zmianie hasła wylogujemy Cię na wszystkich innych urządzeniach.</p>
    <form onSubmit={submit} onInput={() => { setValidation(''); setDone(false); if (change.isError) change.reset() }}>
      <fieldset disabled={change.isPending}>
        <div className="field-group"><label htmlFor="account-current-password">Obecne hasło</label>
          <input className="text-input" id="account-current-password" type="password" autoComplete="current-password" value={current} onChange={event => setCurrent(event.target.value)} /></div>
        <div className="field-group"><label htmlFor="account-new-password">Nowe hasło</label>
          <input className="text-input" id="account-new-password" type="password" autoComplete="new-password" maxLength={72} aria-describedby="account-new-password-hint" value={next} onChange={event => setNext(event.target.value)} />
          <p className="field-hint" id="account-new-password-hint">Co najmniej 12 znaków. Możesz używać spacji i znaków specjalnych.</p></div>
        <div className="field-group"><label htmlFor="account-confirm-password">Powtórz nowe hasło</label>
          <input className="text-input" id="account-confirm-password" type="password" autoComplete="new-password" maxLength={72} value={confirmation} onChange={event => setConfirmation(event.target.value)} /></div>
      </fieldset>
      {(validation || change.isError) && <p className="form-error" role="alert">{validation || change.error?.message}</p>}
      {done && <p className="project-notice" role="status">Hasło zostało zmienione. Inne urządzenia zostały wylogowane.</p>}
      <button className="button-primary" type="submit" disabled={change.isPending}>
        {change.isPending && <LoaderCircle size={16} className="spin" />}{change.isPending ? 'Zapisywanie…' : 'Zmień hasło'}</button>
    </form>
  </section>
}

function SignOutSection() {
  const signOut = useSignOutEverywhere()
  const [confirm, setConfirm] = useState(false)
  return <section className="overview-panel account-section" aria-labelledby="account-sessions-title">
    <h2 id="account-sessions-title">Sesje</h2>
    <p className="field-hint">Wyloguj konto na wszystkich urządzeniach, także na tym. Użyj tego, gdy zgubisz urządzenie lub podejrzewasz, że ktoś ma dostęp do Twojego konta.</p>
    <button className="button-secondary" type="button" onClick={() => { signOut.reset(); setConfirm(true) }}>Wyloguj ze wszystkich urządzeń</button>
    {confirm && <Modal title="Wyloguj ze wszystkich urządzeń" busy={signOut.isPending} onClose={() => setConfirm(false)}>
      <p className="dialog-description">Wszystkie sesje, także ta, zostaną zakończone i trzeba będzie zalogować się ponownie. Aktywny timer nie zostanie zatrzymany.</p>
      {signOut.isError && <p className="form-error" role="alert">{signOut.error.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={signOut.isPending} onClick={() => setConfirm(false)}>Anuluj</button>
        <button type="button" className="button-primary button-danger" disabled={signOut.isPending} onClick={() => signOut.mutate()}>
          {signOut.isPending && <LoaderCircle size={16} className="spin" />}{signOut.isPending ? 'Wylogowywanie…' : 'Wyloguj wszędzie'}</button></footer>
    </Modal>}
  </section>
}

export function AccountPanel({ user }: { user: User }) {
  return <div className="account-panel">
    <ProfileSection user={user} />
    <PasswordSection />
    <SignOutSection />
  </div>
}
