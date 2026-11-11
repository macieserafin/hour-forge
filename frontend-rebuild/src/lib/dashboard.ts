import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError, getDashboardActivity, getDashboardOverview, getDashboardSummary, type Preferences } from './api'
import { useAuthError } from './teams'

export const dashboardKey = (userId: string) => ['dashboard', userId] as const

export function useDashboardSummary(userId: string, teamId: string, weekStart: Preferences['weekStart'] = 'MONDAY') {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...dashboardKey(userId), teamId, weekStart],
    queryFn: ({ signal }) => getDashboardSummary(teamId, signal, weekStart),
    refetchInterval: 60_000, refetchOnWindowFocus: 'always',
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

const retryTransient = (count: number, error: Error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500)

export function useDashboardOverview(userId: string, weekStart: Preferences['weekStart'], enabled = true) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...dashboardKey(userId), 'overview', weekStart], queryFn: ({ signal }) => getDashboardOverview(weekStart, signal),
    refetchInterval: 60_000, refetchOnWindowFocus: 'always', retry: retryTransient, enabled })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useDashboardActivity(userId: string, weekStart: Preferences['weekStart'], weeks = 12) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...dashboardKey(userId), 'activity', weekStart, weeks], queryFn: ({ signal }) => getDashboardActivity(weekStart, weeks, signal),
    staleTime: 60_000, retry: retryTransient })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}
