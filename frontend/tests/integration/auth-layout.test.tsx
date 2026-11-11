import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { AuthLayout } from '../../src/features/auth/AuthLayout'
import { I18nProvider } from '../../src/content/i18n'
import { renderApp } from '../support/render'

function renderLayout() {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => new Promise(() => {})),
  )
  return renderApp(
    <I18nProvider locale="pl">
      <AuthLayout>
        <h1>Zaloguj się</h1>
      </AuthLayout>
    </I18nProvider>,
    '/login',
  )
}

describe('auth layout author credit', () => {
  it('credits the author with a described photo and links that open safely in a new tab', () => {
    renderLayout()
    const cards = Array.from(document.querySelectorAll<HTMLElement>('.auth-credit'))
    expect(cards).toHaveLength(2)
    for (const card of cards) {
      expect(within(card).getByText('Maciej Serafin')).toBeInTheDocument()
      expect(within(card).getByRole('img', { name: 'Zdjęcie Macieja Serafina' })).toBeInTheDocument()
      const site = within(card).getByRole('link', { name: /^macieserafin\.pl/ })
      const github = within(card).getByRole('link', { name: /^GitHub/ })
      expect(site).toHaveAttribute('href', 'https://macieserafin.pl')
      expect(github).toHaveAttribute('href', 'https://github.com/macieserafin')
      for (const link of [site, github]) {
        expect(link).toHaveAttribute('target', '_blank')
        expect(link).toHaveAttribute('rel', 'noopener noreferrer')
      }
    }
  })

  it('keeps the credit outside the decorative, screen-reader-hidden story text', () => {
    renderLayout()
    const hidden = document.querySelector('[aria-hidden="true"].auth-story-text')
    expect(hidden).not.toBeNull()
    expect(hidden!.querySelector('a')).toBeNull()
    expect(screen.getByText('Każda godzina pracy na swoim miejscu.')).toBeInTheDocument()
  })
})
