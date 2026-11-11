import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError, getDashboardActivity, type Preferences } from './api'
import { useAuthError } from './teams'

export const dashboardKey = (userId: string) => ['dashboard', userId] as const

const retryTransient = (count: number, error: Error) =>
  count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500)

export function useDashboardActivity(userId: string, weekStart: Preferences['weekStart'], weeks = 12) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: [...dashboardKey(userId), 'activity', weekStart, weeks],
    queryFn: ({ signal }) => getDashboardActivity(weekStart, weeks, signal),
    staleTime: 60_000,
    retry: retryTransient,
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}
