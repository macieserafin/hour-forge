import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, changeTimerState, createManualTimeEntry, deleteTimeEntry, getActiveTimer, getTimeEntryHistory, startTimer, stopTimer, updateTimeEntry, type TimeEntryHistoryFilters } from './api'
import { teamsKey, useAuthError } from './teams'
import { dashboardKey } from './dashboard'

export const activeTimerKey = (userId: string) => ['timer', userId, 'active'] as const
const historyKey = (userId: string) => ['time-entries', userId] as const

export function useTimeEntryHistory(userId: string, teamId: string, filters: TimeEntryHistoryFilters, page: number) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...historyKey(userId), teamId, filters, page],
    queryFn: ({ signal }) => getTimeEntryHistory(teamId, filters, page, signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useActiveTimer(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: activeTimerKey(userId), queryFn: ({ signal }) => getActiveTimer(signal),
    refetchInterval: 15_000, refetchOnWindowFocus: 'always',
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

function useTimeCallbacks(userId: string, refreshHistory = false) {
  const client = useQueryClient()
  const handleError = useAuthError()
  return {
    // Reconcile with the server even when a response was lost after a successful write.
    onSettled: () => Promise.all([
      client.invalidateQueries({ queryKey: activeTimerKey(userId) }),
      ...(refreshHistory ? [client.invalidateQueries({ queryKey: historyKey(userId) }),
        client.invalidateQueries({ queryKey: dashboardKey(userId) })] : []),
    ]),
    onError: (error: Error) => {
      handleError(error)
      if (error instanceof ApiError && [403, 404, 409].includes(error.status)) {
        void client.invalidateQueries({ queryKey: teamsKey(userId) })
        void client.invalidateQueries({ queryKey: ['projects', userId] })
      }
    },
    onMutate: () => Promise.all([
      client.cancelQueries({ queryKey: activeTimerKey(userId) }),
      ...(refreshHistory ? [client.cancelQueries({ queryKey: historyKey(userId) }),
        client.cancelQueries({ queryKey: dashboardKey(userId) })] : []),
    ]),
  }
}

export function useStartTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId)
  return useMutation({ mutationFn: startTimer, retry: false, ...callbacks,
    onSuccess: entry => client.setQueryData(activeTimerKey(userId), entry) })
}

export function useStopTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: stopTimer, retry: false, ...callbacks,
    onSuccess: () => client.setQueryData(activeTimerKey(userId), null) })
}

export function useChangeTimerState(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId)
  return useMutation({ mutationFn: changeTimerState, retry: false, ...callbacks,
    onSuccess: entry => client.setQueryData(activeTimerKey(userId), entry) })
}

export function useManualTimeEntry(userId: string) {
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: createManualTimeEntry, retry: false, ...callbacks })
}

export function useUpdateTimeEntry(userId: string) {
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: updateTimeEntry, retry: false, ...callbacks })
}

export function useDeleteTimeEntry(userId: string) {
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: deleteTimeEntry, retry: false, ...callbacks })
}

export const MAX_MANUAL_DURATION_SECONDS = 24 * 60 * 60
export const workDateRangeMessage = 'Data pracy musi mieścić się w ostatnich 2 latach i nie może być późniejsza niż dziś.'
export const maxDurationMessage = 'Jeden wpis może mieć najwyżej 24 godziny.'

/** Today's date (YYYY-MM-DD) in the team's time zone, matching the backend's work date rules. */
export function teamToday(timezone: string, now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
}

/** Allowed work dates: from two years ago (Feb 29 becomes Feb 28, like Java minusYears) up to today. */
export function workDateRange(timezone: string, now = new Date()) {
  const max = teamToday(timezone, now)
  const [year, month, day] = max.split('-').map(Number) as [number, number, number]
  const lastDay = new Date(Date.UTC(year - 2, month, 0)).getUTCDate()
  const min = `${year - 2}-${String(month).padStart(2, '0')}-${String(Math.min(day, lastDay)).padStart(2, '0')}`
  return { min, max }
}

export function formatDuration(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds))
  return [Math.floor(safe / 3600), Math.floor(safe / 60) % 60, safe % 60]
    .map(part => String(part).padStart(2, '0')).join(':')
}
