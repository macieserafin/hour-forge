import { useCallback, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, acceptInvitation, archiveTeam, createInvitation, createTeam, deleteTeam, getInvitationPreview, getInvitations, revokeInvitation, getTeamSummary, getTeams,
  transferOwnership, updateTeam, type Team } from './api'
import { sessionOptions } from './auth'

export const teamsKey = (userId: string) => ['teams', userId] as const

export function useAuthError() {
  const client = useQueryClient()
  return useCallback((error: Error) => {
    if (error instanceof ApiError && error.status === 401) {
      client.setQueryData(sessionOptions.queryKey, null)
      client.removeQueries({ queryKey: ['teams'], type: 'inactive' })
      client.removeQueries({ queryKey: ['projects'], type: 'inactive' })
      client.removeQueries({ queryKey: ['timer'], type: 'inactive' })
      client.removeQueries({ queryKey: ['time-entries'], type: 'inactive' })
      client.removeQueries({ queryKey: ['dashboard'], type: 'inactive' })
      client.removeQueries({ queryKey: ['team-members'], type: 'inactive' })
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

function useReplaceTeam(userId: string) {
  const client = useQueryClient()
  return async (team: Team) => {
    client.setQueryData<Team[]>(teamsKey(userId), previous => previous?.map(item => item.id === team.id ? team : item))
    await Promise.all([
      client.invalidateQueries({ queryKey: teamsKey(userId) }),
      client.invalidateQueries({ queryKey: ['projects', userId, team.id] }),
      client.invalidateQueries({ queryKey: ['dashboard', userId, team.id] }),
      client.invalidateQueries({ queryKey: ['time-entries', userId] }),
    ])
  }
}

export function useUpdateTeam(userId: string) {
  const onError = useAuthError()
  const replace = useReplaceTeam(userId)
  return useMutation({ mutationFn: updateTeam, onError, onSuccess: replace })
}

export function useArchiveTeam(userId: string) {
  const onError = useAuthError()
  const replace = useReplaceTeam(userId)
  return useMutation({ mutationFn: archiveTeam, onError, onSuccess: replace })
}

export function useDeleteTeam(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: deleteTeam, onError,
    onSuccess: async (_result, teamId) => {
      client.setQueryData<Team[]>(teamsKey(userId), previous => previous?.filter(team => team.id !== teamId))
      for (const key of [['projects', userId, teamId], ['team-members', userId, teamId], ['dashboard', userId, teamId], ['team-summary', userId, teamId]]) {
        client.removeQueries({ queryKey: key })
      }
      await Promise.all([
        client.invalidateQueries({ queryKey: teamsKey(userId) }),
        client.invalidateQueries({ queryKey: ['time-entries', userId] }),
        client.invalidateQueries({ queryKey: ['timer', userId] }),
      ])
    } })
}

export function useTransferOwnership(userId: string, teamId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: (targetUserId: string) => transferOwnership(teamId, targetUserId), onError, retry: false,
    // A lost response may follow a committed change: reconcile instead of repeating the write.
    onSettled: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: teamsKey(userId) }),
        client.invalidateQueries({ queryKey: ['team-members', userId, teamId] }),
        client.invalidateQueries({ queryKey: ['dashboard', userId, teamId] }),
        client.invalidateQueries({ queryKey: ['projects', userId, teamId] }),
      ])
    } })
}

export function useTeamSummary(userId: string, teamId: string, enabled: boolean) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: ['team-summary', userId, teamId], queryFn: ({ signal }) => getTeamSummary(teamId, signal),
    enabled, staleTime: 0, retry: false })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useInvitationPreview(userId: string, token: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: ['invitation-preview', userId, token], queryFn: ({ signal }) => getInvitationPreview(token, signal),
    retry: false, staleTime: 0 })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export const invitationsKey = (userId: string, teamId: string) => [...teamsKey(userId), teamId, 'invitations'] as const

export function useInvitations(userId: string, teamId: string, page: number) {
  const client = useQueryClient()
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...invitationsKey(userId, teamId), page],
    queryFn: ({ signal }) => getInvitations(teamId, page, signal), retry: false,
    refetchInterval: 30_000, refetchOnWindowFocus: 'always' })
  useEffect(() => {
    if (query.error) {
      handleError(query.error)
      if (query.error instanceof ApiError && [403, 404].includes(query.error.status)) void client.invalidateQueries({ queryKey: teamsKey(userId), exact: true })
    }
  }, [query.error, handleError, client, userId])
  return query
}

export function useRevokeInvitation(userId: string, teamId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: (id: string) => revokeInvitation(teamId, id), onError, retry: false,
    onSettled: async () => {
      await client.invalidateQueries({ queryKey: invitationsKey(userId, teamId) })
      await client.invalidateQueries({ queryKey: teamsKey(userId), exact: true })
    } })
}

export function useCreateInvitation(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: createInvitation, onError, gcTime: 0, retry: false,
    onSettled: (_data, _error, input) => client.invalidateQueries({ queryKey: invitationsKey(userId, input.teamId) }) })
}

export function useAcceptInvitation(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: acceptInvitation, onError, gcTime: 0,
    onSuccess: async team => {
      client.setQueryData<Team[]>(teamsKey(userId), previous => [team, ...(previous ?? []).filter(item => item.id !== team.id)])
      await client.invalidateQueries({ queryKey: teamsKey(userId) })
      await client.invalidateQueries({ queryKey: ['team-members', userId, team.id] })
      await client.invalidateQueries({ queryKey: ['dashboard', userId, team.id] })
    } })
}
