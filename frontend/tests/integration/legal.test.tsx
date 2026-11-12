import { describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { I18nProvider } from '../../src/content/i18n'
import { instance } from '../../src/content/instance'
import { legalDocument, legalKinds, legalPaths } from '../../src/content/legal'
import { AuthLayout } from '../../src/features/auth/AuthLayout'
import { LegalPage } from '../../src/features/legal/Legal'
import { renderApp } from '../support/render'

function renderPages(path: string, locale: 'pl' | 'en' = 'pl') {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => new Promise(() => {})),
  )
  return renderApp(
    <I18nProvider locale={locale}>
      <Routes>
        {legalKinds.map(kind => (
          <Route key={kind} path={legalPaths[kind]} element={<LegalPage kind={kind} />} />
        ))}
      </Routes>
    </I18nProvider>,
    path,
  )
}

describe('legal documents', () => {
  it('has the same structure in Polish and English for every document', () => {
    for (const kind of legalKinds) {
      const pl = legalDocument('pl', kind, instance)
      const en = legalDocument('en', kind, instance)
      expect(en.sections.length).toBe(pl.sections.length)
      expect(en.sections.map(s => [s.items?.length, s.paragraphs?.length, s.table?.rows.length])).toEqual(
        pl.sections.map(s => [s.items?.length, s.paragraphs?.length, s.table?.rows.length]),
      )
    }
  })

  it('prints the operator details in the privacy policy and the terms', () => {
    for (const kind of ['privacy', 'terms'] as const) {
      const text = JSON.stringify(legalDocument('en', kind, instance))
      expect(text).toContain(instance.operatorName)
      expect(text).toContain(instance.contactEmail)
    }
  })

  it('keeps every table row as wide as its header', () => {
    for (const kind of legalKinds)
      for (const locale of ['pl', 'en'] as const)
        for (const section of legalDocument(locale, kind, instance).sections)
          for (const row of section.table?.rows ?? []) expect(row).toHaveLength(section.table!.head.length)
  })
})

describe('legal pages', () => {
  it('shows the privacy policy without a session, with the date and a labelled section per heading', () => {
    renderPages('/privacy')
    expect(screen.getByRole('heading', { level: 1, name: 'Polityka prywatności' })).toBeInTheDocument()
    expect(screen.getByText(instance.legalUpdated)).toBeInTheDocument()
    const headings = screen.getAllByRole('heading', { level: 2 })
    expect(headings.length).toBeGreaterThanOrEqual(8)
    for (const heading of headings)
      expect(screen.getByRole('region', { name: heading.textContent! })).toBeInTheDocument()
  })

  it('links the three documents and marks the current one', () => {
    renderPages('/terms')
    const nav = screen.getByRole('navigation', { name: 'Dokumenty prawne' })
    expect(
      within(nav)
        .getAllByRole('link')
        .map(link => link.getAttribute('href')),
    ).toEqual(['/privacy', '/terms', '/subprocessors'])
    expect(within(nav).getByRole('link', { name: 'Regulamin' })).toHaveAttribute('aria-current', 'page')
  })

  it('renders the subprocessor tables with column and row headers', () => {
    renderPages('/subprocessors')
    const tables = screen.getAllByRole('table')
    expect(tables).toHaveLength(2)
    expect(
      within(tables[0]!)
        .getAllByRole('columnheader')
        .map(cell => cell.textContent),
    ).toEqual(['Cel', 'Dostawca', 'Dane', 'Miejsce'])
    expect(within(tables[0]!).getByRole('rowheader', { name: /Serwer aplikacji/ })).toBeInTheDocument()
  })

  it('switches the language of the page', async () => {
    renderPages('/privacy')
    await userEvent.click(screen.getByRole('button', { name: 'English' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Privacy policy' })).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('en')
  })
})

describe('legal links on sign-in pages', () => {
  it('shows the three documents under the form', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => {})),
    )
    renderApp(
      <I18nProvider locale="en">
        <AuthLayout>
          <h1>Sign in</h1>
        </AuthLayout>
      </I18nProvider>,
      '/login',
    )
    const nav = screen.getByRole('navigation', { name: 'Legal documents' })
    expect(within(nav).getByRole('link', { name: 'Privacy policy' })).toHaveAttribute('href', '/privacy')
    expect(within(nav).getByRole('link', { name: 'Terms of service' })).toHaveAttribute('href', '/terms')
    expect(within(nav).getByRole('link', { name: 'Subprocessors' })).toHaveAttribute('href', '/subprocessors')
  })
})
