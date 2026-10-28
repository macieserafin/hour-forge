import { useState } from 'react'
import { it, expect, vi } from 'vitest'
import { screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Modal } from '../../src/components/Modal'
import { renderApp } from '../support/render'

it('locks body scroll and prevents dismissal while saving', async () => {
  const close = vi.fn()
  const { unmount } = renderApp(<Modal title="Saving" busy onClose={close}><input aria-label="Name" /></Modal>)
  const dialog = screen.getByRole('dialog', { name: 'Saving' })
  expect(document.body.style.overflow).toBe('hidden')
  expect(screen.getByRole('button', { name: 'Zamknij formularz' })).toBeDisabled()
  await userEvent.click(dialog)
  fireEvent(dialog, new Event('cancel', { bubbles: true, cancelable: true }))
  expect(close).not.toHaveBeenCalled()
  unmount()
  expect(document.body.style.overflow).toBe('')
})
it('allows cancelling an idle dialog', async () => {
  const close = vi.fn()
  renderApp(<Modal title="Edit" busy={false} onClose={close}>Form</Modal>)
  await userEvent.click(screen.getByRole('button', { name: 'Zamknij formularz' }))
  expect(close).toHaveBeenCalledOnce()
})

function Harness({ removeOpenerOnClose = false }: { removeOpenerOnClose?: boolean }) {
  const [open, setOpen] = useState(false)
  const [showOpener, setShowOpener] = useState(true)
  return <main>
    {showOpener && <button type="button" onClick={() => setOpen(true)}>Otwórz formularz</button>}
    <a href="/start">Początek strony</a>
    {open && <Modal title="Formularz" busy={false} onClose={() => { setOpen(false); if (removeOpenerOnClose) setShowOpener(false) }}>
      <input aria-label="Nazwa" />
      <button type="button" onClick={() => setOpen(false)}>Zapisz</button>
    </Modal>}
  </main>
}

it('returns focus to the button that opened the dialog when it is closed with the close button', async () => {
  renderApp(<Harness />)
  await userEvent.click(screen.getByRole('button', { name: 'Otwórz formularz' }))
  await userEvent.click(screen.getByRole('button', { name: 'Zamknij formularz' }))
  expect(screen.getByRole('button', { name: 'Otwórz formularz' })).toHaveFocus()
})
it('returns focus to the opening button after the form is submitted', async () => {
  renderApp(<Harness />)
  await userEvent.click(screen.getByRole('button', { name: 'Otwórz formularz' }))
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz' }))
  expect(screen.getByRole('button', { name: 'Otwórz formularz' })).toHaveFocus()
})
it('returns focus to the opening button after Escape', async () => {
  renderApp(<Harness />)
  await userEvent.click(screen.getByRole('button', { name: 'Otwórz formularz' }))
  fireEvent(screen.getByRole('dialog', { name: 'Formularz' }), new Event('cancel', { bubbles: true, cancelable: true }))
  expect(screen.getByRole('button', { name: 'Otwórz formularz' })).toHaveFocus()
})
it('does not fail when the opening button is gone by the time the dialog closes', async () => {
  renderApp(<Harness removeOpenerOnClose />)
  await userEvent.click(screen.getByRole('button', { name: 'Otwórz formularz' }))
  await userEvent.click(screen.getByRole('button', { name: 'Zamknij formularz' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Początek strony' })).toBeInTheDocument()
})
