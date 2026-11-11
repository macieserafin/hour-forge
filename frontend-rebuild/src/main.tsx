import { lazy, StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '@fontsource-variable/inter'
import '@fontsource-variable/space-grotesk'
import './ui/tokens.css'
import './ui/base.css'
import './ui/components.css'
// Layout blocks shared by many screens (record cards, totals, entry lists, pickers, details).
import './features/work/work.css'
import './features/entries/entries.css'
import './features/teams/teams.css'
import { App } from './app/App'
import { I18nProvider } from './content/i18n'
import { ToastProvider } from './ui/toast'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 30_000 }, mutations: { retry: false } } })

// The direction lab stays reachable during development only; it is not part of the product build.
const Lab = import.meta.env.DEV ? lazy(() => import('./lab/Lab').then(module => ({ default: module.Lab }))) : null

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              {Lab && <Route path="/_kierunki" element={<Suspense><Lab /></Suspense>} />}
              <Route path="*" element={<App />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>
  </StrictMode>,
)
