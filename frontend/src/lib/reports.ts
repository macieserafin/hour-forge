import { useEffect } from 'react'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, getReportBreakdown, getReportEntries, getReportMembers, getReportProjects, getReportSummary,
  type ReportFilters, type ReportResult, type ReportSort } from './api'
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

export function useReportBreakdown(userId: string, teamId: string, filters: ReportFilters, groupBy: 'MEMBER' | 'PROJECT', page: number) {
  const query = useQuery({ queryKey: [...reportsKey(userId, teamId), 'breakdown', filters, groupBy, page],
    queryFn: ({ signal }) => getReportBreakdown(teamId, filters, groupBy, page, signal), retry,
    refetchInterval: 60_000, refetchOnWindowFocus: 'always' })
  useReportError(userId, query.error)
  return query
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
  return { from, to: date.toISOString().slice(0, 10), userId: '', projectId: '', entryType: '' }
}
