import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError, getDashboardSummary } from './api'
import { useAuthError } from './teams'

export const dashboardKey = (userId: string) => ['dashboard', userId] as const

export function useDashboardSummary(userId: string, teamId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...dashboardKey(userId), teamId],
    queryFn: ({ signal }) => getDashboardSummary(teamId, signal),
    refetchInterval: 60_000, refetchOnWindowFocus: 'always',
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}
