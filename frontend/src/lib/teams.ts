import { useCallback, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, acceptInvitation, createInvitation, createTeam, getTeams, type Team } from './api'
import { sessionOptions } from './auth'

export const teamsKey = (userId: string) => ['teams', userId] as const

export function useAuthError() {
  const client = useQueryClient()
  return useCallback((error: Error) => {
    if (error instanceof ApiError && error.status === 401) {
      client.setQueryData(sessionOptions.queryKey, null)
      client.removeQueries({ queryKey: ['teams'], type: 'inactive' })
      client.removeQueries({ queryKey: ['projects'], type: 'inactive' })
    }
  }, [client])
}

export function useTeams(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: teamsKey(userId), queryFn: ({ signal }) => getTeams(signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useCreateTeam(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: createTeam, onError,
    onSuccess: async team => {
      client.setQueryData<Team[]>(teamsKey(userId), previous => [team, ...(previous ?? []).filter(item => item.id !== team.id)])
      await client.invalidateQueries({ queryKey: teamsKey(userId) })
    } })
}

export function useCreateInvitation() {
  const onError = useAuthError()
  return useMutation({ mutationFn: createInvitation, onError, gcTime: 0 })
}

export function useAcceptInvitation(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: acceptInvitation, onError, gcTime: 0,
    onSuccess: async team => {
      client.setQueryData<Team[]>(teamsKey(userId), previous => [team, ...(previous ?? []).filter(item => item.id !== team.id)])
      await client.invalidateQueries({ queryKey: teamsKey(userId) })
    } })
}
