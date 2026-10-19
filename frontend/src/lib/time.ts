import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, changeTimerState, createManualTimeEntry, getActiveTimer, getTimeEntryHistory, startTimer, stopTimer, type TimeEntryHistoryFilters } from './api'
import { teamsKey, useAuthError } from './teams'

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
      ...(refreshHistory ? [client.invalidateQueries({ queryKey: historyKey(userId) })] : []),
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
      ...(refreshHistory ? [client.cancelQueries({ queryKey: historyKey(userId) })] : []),
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

export function formatDuration(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds))
  return [Math.floor(safe / 3600), Math.floor(safe / 60) % 60, safe % 60]
    .map(part => String(part).padStart(2, '0')).join(':')
}
