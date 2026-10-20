import { useEffect } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { saveBlob } from './download'
import { ApiError, downloadReport, getReportActive, getReportBreakdown, getReportEntries, getReportMembers, getReportProjects, getReportSummary,
  type ReportDownload, type ReportFilters, type ReportGroup, type ReportResult, type ReportSort } from './api'
import { dashboardKey } from './dashboard'
import { teamsKey, useAuthError } from './teams'

// Shares invalidation and session cleanup with the personal dashboard.
export const reportsKey = (userId: string, teamId: string) => [...dashboardKey(userId), teamId, 'reports'] as const
export const MAX_REPORT_DAYS = 366

/** Number of calendar days in an inclusive YYYY-MM-DD range. */
export function reportRangeDays(from: string, to: string) {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000) + 1
}

const retry = (count: number, error: Error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500)

function useReportError(userId: string, error: Error | null) {
  const handleError = useAuthError()
  const client = useQueryClient()
  useEffect(() => {
    if (!error) return
    handleError(error)
    if (error instanceof ApiError && [403, 404].includes(error.status)) {
      void client.invalidateQueries({ queryKey: teamsKey(userId) })
    }
  }, [error, handleError, client, userId])
}

export function useReportSummary(userId: string, teamId: string, filters: ReportFilters) {
  const query = useQuery({ queryKey: [...reportsKey(userId, teamId), 'summary', filters],
    queryFn: ({ signal }) => getReportSummary(teamId, filters, signal), retry,
    refetchInterval: 60_000, refetchOnWindowFocus: 'always' })
  useReportError(userId, query.error)
  return query
}

export function useReportBreakdown(userId: string, teamId: string, filters: ReportFilters, groupBy: ReportGroup, page: number) {
  const query = useQuery({ queryKey: [...reportsKey(userId, teamId), 'breakdown', filters, groupBy, page],
    queryFn: ({ signal }) => getReportBreakdown(teamId, filters, groupBy, page, signal), retry,
    refetchInterval: 60_000, refetchOnWindowFocus: 'always' })
  useReportError(userId, query.error)
  return query
}

/** The live view polls faster than the reports: a running timer is what the owner wants to see change. */
export function useReportActive(userId: string, teamId: string, page: number, enabled: boolean) {
  const query = useQuery({ queryKey: [...reportsKey(userId, teamId), 'active', page], enabled,
    queryFn: ({ signal }) => getReportActive(teamId, page, signal), retry,
    refetchInterval: 15_000, refetchOnWindowFocus: 'always' })
  useReportError(userId, query.error)
  return query
}

export function useDownloadReport(teamId: string, filters: ReportFilters, kind: ReportDownload) {
  const handleError = useAuthError()
  return useMutation({ mutationFn: () => downloadReport(teamId, filters, kind), retry: false, gcTime: 0, onError: handleError,
    onSuccess: ({ blob, filename }) => saveBlob(blob, filename) })
}

export function useReportEntries(userId: string, teamId: string, filters: ReportFilters, sort: ReportSort, page: number) {
  const query = useQuery({ queryKey: [...reportsKey(userId, teamId), 'entries', filters, sort, page],
    queryFn: ({ signal }) => getReportEntries(teamId, filters, sort, page, signal), retry,
    refetchInterval: 60_000, refetchOnWindowFocus: 'always' })
  useReportError(userId, query.error)
  return query
}

function useReportOptions<T>(userId: string, teamId: string, kind: 'members' | 'projects',
  fetchPage: (teamId: string, page: number, signal?: AbortSignal) => Promise<ReportResult<T>>) {
  const query = useInfiniteQuery({ queryKey: [...reportsKey(userId, teamId), kind], initialPageParam: 0,
    queryFn: ({ pageParam, signal }) => fetchPage(teamId, pageParam, signal),
    getNextPageParam: last => last.data.hasNext ? last.data.page + 1 : undefined,
    retry, refetchOnWindowFocus: 'always' })
  useReportError(userId, query.error)
  return query
}

export const useReportMembers = (userId: string, teamId: string) => useReportOptions(userId, teamId, 'members', getReportMembers)
export const useReportProjects = (userId: string, teamId: string) => useReportOptions(userId, teamId, 'projects', getReportProjects)

export function reportWeek(timezone: string, now = new Date()): ReportFilters {
  const parts = new Intl.DateTimeFormat('en', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now)
  const part = (type: string) => Number(parts.find(p => p.type === type)!.value)
  const date = new Date(Date.UTC(part('year'), part('month') - 1, part('day')))
  date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7)
  const from = date.toISOString().slice(0, 10)
  date.setUTCDate(date.getUTCDate() + 6)
  return { from, to: date.toISOString().slice(0, 10), userId: '', projectId: '', entryType: '', q: '' }
}

const dateText = /^\d{4}-\d{2}-\d{2}$/
const managedParams = ['from', 'to', 'person', 'project', 'type', 'undated', 'q'] as const

function localDate(timezone: string, now: Date) {
  const parts = new Intl.DateTimeFormat('en', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now)
  const part = (type: string) => Number(parts.find(p => p.type === type)!.value)
  return new Date(Date.UTC(part('year'), part('month') - 1, part('day')))
}

const isoDate = (date: Date) => date.toISOString().slice(0, 10)

/** Quick report ranges in the team's time zone; weeks run Monday to Sunday. */
export function reportRanges(timezone: string, now = new Date()) {
  const today = localDate(timezone, now)
  const monday = new Date(today)
  monday.setUTCDate(monday.getUTCDate() - (monday.getUTCDay() + 6) % 7)
  const sunday = new Date(monday)
  sunday.setUTCDate(sunday.getUTCDate() + 6)
  const lastMonday = new Date(monday)
  lastMonday.setUTCDate(lastMonday.getUTCDate() - 7)
  const lastSunday = new Date(monday)
  lastSunday.setUTCDate(lastSunday.getUTCDate() - 1)
  const monthStart = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), 1))
  const monthEnd = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth() + 1, 0))
  return {
    today: { from: isoDate(today), to: isoDate(today) },
    week: { from: isoDate(monday), to: isoDate(sunday) },
    lastWeek: { from: isoDate(lastMonday), to: isoDate(lastSunday) },
    month: { from: isoDate(monthStart), to: isoDate(monthEnd) },
  }
}

/** Reads report filters from the address; anything missing or malformed falls back to the current week. */
export function filtersFromSearch(search: URLSearchParams, timezone: string, now = new Date()): ReportFilters {
  const week = reportWeek(timezone, now)
  const undated = search.get('undated') === 'true'
  const from = search.get('from') ?? ''
  const to = search.get('to') ?? ''
  const type = search.get('type')
  const dated = dateText.test(from) && dateText.test(to)
  return {
    undated,
    from: undated ? '' : dated ? from : week.from,
    to: undated ? '' : dated ? to : week.to,
    userId: search.get('person') ?? '',
    projectId: search.get('project') ?? '',
    entryType: type === 'TIMER' || type === 'MANUAL' ? type : '',
    q: (search.get('q') ?? '').slice(0, 200),
  }
}

/** Writes the filters into a copy of the address, leaving unrelated parameters such as the selected team alone. */
export function filtersToSearch(filters: ReportFilters, base: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(base)
  managedParams.forEach(key => next.delete(key))
  if (filters.undated) next.set('undated', 'true')
  else { next.set('from', filters.from); next.set('to', filters.to) }
  if (filters.userId) next.set('person', filters.userId)
  if (filters.projectId) next.set('project', filters.projectId)
  if (filters.entryType) next.set('type', filters.entryType)
  if (filters.q?.trim()) next.set('q', filters.q.trim())
  return next
}

export function clearedSearch(base: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(base)
  managedParams.forEach(key => next.delete(key))
  return next
}
