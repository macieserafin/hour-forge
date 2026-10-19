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
