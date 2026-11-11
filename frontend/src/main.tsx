import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource-variable/inter'
import '@fontsource-variable/space-grotesk'
import './ui/tokens.css'
import './ui/base.css'
import './ui/components.css'
import './ui/layout.css'
// Blocks used by several screens (record cards, totals, entry lists, pickers, details).
import './features/work/work.css'
import './features/entries/entries.css'
import './features/teams/teams.css'
import { App } from './app/App'
import { I18nProvider } from './content/i18n'
import { ToastProvider } from './ui/toast'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 }, mutations: { retry: false } },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <ToastProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ToastProvider>
      </I18nProvider>
    </QueryClientProvider>
  </StrictMode>,
)
