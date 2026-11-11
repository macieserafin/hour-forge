import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, getPreferences, savePreferences, type Preferences } from './api'
import { useAuthError } from './teams'

export const defaultPreferences: Preferences = {
  defaultTeamId: null,
  startPage: 'DASHBOARD',
  timeFormat: 'CLOCK',
  weekStart: 'MONDAY',
  longTimerEmail: true,
  browserNotifications: false,
  longTimerHours: 10,
  titleTimer: true,
  teamEventEmails: true,
  weeklyReportEmail: true,
}

export const preferencesKey = (userId: string) => ['preferences', userId] as const

/** The person's settings. Until they load, and if they cannot be loaded, the defaults apply. */
export function usePreferences(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: preferencesKey(userId),
    queryFn: ({ signal }) => getPreferences(signal),
    staleTime: 5 * 60_000,
    retryOnMount: false,
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

export function useSavePreferences(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({
    mutationFn: savePreferences,
    onError,
    retry: false,
    onSuccess: saved => client.setQueryData(preferencesKey(userId), saved),
  })
}

/** Start pages stored on the account (old names kept until the account stores the new views); all lead into the new app. */
export const startPaths = {
  DASHBOARD: '/app/work',
  TIME: '/app/work',
  PROJECTS: '/app/projects',
  TEAMS: '/app/teams',
} as const
