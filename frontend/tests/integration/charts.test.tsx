import { it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { BarChart } from '../../src/components/BarChart'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { PersonalDashboard } from '../../src/pages/PersonalDashboard'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'
import { reportBucket, reportHandlers, reportPage } from '../support/reports'

const items = [
  { key: 'a', label: 'Pon.', value: 3600, display: 'poniedziałek: 01:00:00', short: '1:00' },
  { key: 'b', label: 'Wt.', value: 1800, display: 'wtorek: 00:30:00', short: '0:30' },
  { key: 'c', label: 'Śr.', value: 0, display: 'środa: 00:00:00', short: '0:00' },
]

it('draws proportional bars and keeps the numbers available as a table for assistive technology', () => {
  const { container } = render(<BarChart title="Czas w tygodniu" labelHeading="Dzień" items={items} />)
  const figure = screen.getByRole('figure', { name: 'Czas w tygodniu' })
  expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  const bars = [...container.querySelectorAll('rect.chart-bar')]
  expect(bars).toHaveLength(2)
  expect(Number(bars[0]!.getAttribute('height')) / Number(bars[1]!.getAttribute('height'))).toBeCloseTo(2, 5)
  const table = within(figure).getByRole('table', { name: 'Czas w tygodniu' })
  expect(within(table).getAllByRole('row')).toHaveLength(4)
  expect(within(table).getByRole('rowheader', { name: 'Wt.' })).toBeInTheDocument()
  expect(within(table).getByRole('cell', { name: 'poniedziałek: 01:00:00' })).toBeInTheDocument()
  expect(screen.getByText('1:00')).toBeInTheDocument()
})

it('does not divide by zero or draw bars when nothing was worked', () => {
  const { container } = render(<BarChart title="Pusty" items={items.map(item => ({ ...item, value: 0 }))} />)
  expect(container.querySelectorAll('rect.chart-bar')).toHaveLength(0)
  expect(container.querySelector('svg')!.innerHTML).not.toContain('NaN')
})

it('draws horizontal bars with long labels shortened but complete in the table', () => {
  const long = 'Bardzo długa nazwa projektu, która nie zmieści się w kolumnie'
  const { container } = render(<BarChart title="Projekty" layout="rows" labelHeading="Projekt"
    items={[{ key: 'p', label: long, value: 60, display: '00:01:00', short: '0:01' }]} />)
  expect(container.querySelector('text.chart-label')!.textContent).toHaveLength(19)
  expect(container.querySelector('text.chart-label')!.textContent!.endsWith('…')).toBe(true)
  expect(screen.getByRole('rowheader', { name: long })).toBeInTheDocument()
})

it('hides labels that would overlap on a long range but keeps every day in the table', () => {
  const days = Array.from({ length: 31 }, (_, index) => ({ key: `d${index}`, label: String(index + 1), value: index + 1, display: `dzień ${index + 1}`, short: '' }))
  const { container } = render(<BarChart title="Miesiąc" items={days} />)
  expect(container.querySelectorAll('text.chart-label').length).toBeLessThan(31)
  expect(container.querySelectorAll('text.chart-value')).toHaveLength(0)
  expect(screen.getAllByRole('row')).toHaveLength(32)
})

it('shows the personal week as a chart with every day of the week in its table', async () => {
  mockApi()
  renderApp(<PersonalDashboard userId="actor" />)
  const chart = await screen.findByRole('figure', { name: 'Twój czas w tym tygodniu' })
  const rows = within(within(chart).getByRole('table')).getAllByRole('row')
  expect(rows).toHaveLength(8)
  expect(rows[1]).toHaveTextContent('00:15:00')
  expect(rows[3]).toHaveTextContent('00:45:00')
})

it('draws the day and project charts only for a complete result', async () => {
  const bucket = (extra: object) => ({ ...reportBucket, ...extra })
  const handlers = reportHandlers()
  mockApi({ ...handlers, 'GET /api/teams/team/reports/breakdown': url => {
    const group = url.searchParams.get('groupBy')
    const rows = group === 'DAY' ? [bucket({ workDate: '2026-10-19', durationSeconds: 3600 }), bucket({ workDate: '2026-10-20', durationSeconds: 1800 })]
      : group === 'PROJECT' ? [bucket({ projectId: 'a', projectName: 'Alfa', durationSeconds: 100 }), bucket({ projectId: 'b', projectName: 'Beta', durationSeconds: 300 })]
      : [bucket({ userId: 'u', displayName: 'Ktoś', projectId: 'a', projectName: 'Alfa' })]
    return json({ ...reportPage(rows, 0, group === 'MEMBER'), groupBy: group })
  } })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const dayChart = await screen.findByRole('figure', { name: 'Czas w kolejnych dniach' })
  expect(within(dayChart).getAllByRole('row')).toHaveLength(3)
  const projectChart = screen.getByRole('figure', { name: 'Podział czasu na projekty' })
  // Biggest first: the chart reads as a ranking.
  expect(within(projectChart).getAllByRole('rowheader').map(header => header.textContent)).toEqual(['Beta', 'Alfa'])
  expect(screen.queryByRole('figure', { name: /osoby/i })).not.toBeInTheDocument()
})

it('groups the smallest projects into one bar and skips the chart when the list continues on another page', async () => {
  const projects = Array.from({ length: 10 }, (_, index) => ({ ...reportBucket, projectId: `p${index}`, projectName: `Projekt ${index}`, durationSeconds: 600 - index }))
  mockApi({ ...reportHandlers(), 'GET /api/teams/team/reports/breakdown': url => {
    const group = url.searchParams.get('groupBy')
    return json({ ...reportPage(group === 'PROJECT' ? projects : [], 0, group === 'DAY'), groupBy: group })
  } })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const chart = await screen.findByRole('figure', { name: 'Podział czasu na projekty' })
  const names = within(chart).getAllByRole('rowheader').map(header => header.textContent)
  expect(names).toHaveLength(9)
  expect(names.at(-1)).toBe('Pozostałe projekty')
  expect(screen.queryByRole('figure', { name: 'Czas w kolejnych dniach' })).not.toBeInTheDocument()
})
