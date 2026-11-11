import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'

export function renderApp(content: ReactNode, path = '/dashboard') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const result = render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}>{content}</MemoryRouter></QueryClientProvider>)
  return { ...result, client }
}
