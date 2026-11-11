import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, getTeamMembers, leaveTeam, removeTeamMember } from './api'
import { teamsKey, useAuthError } from './teams'
import { dashboardKey } from './dashboard'

export const membersKey = (userId: string, teamId: string) => ['team-members', userId, teamId] as const

export function useTeamMembers(userId: string, teamId: string, includeFormer: boolean, page: number) {
  const handleError = useAuthError()
  const client = useQueryClient()
  const query = useQuery({ queryKey: [...membersKey(userId, teamId), includeFormer, page],
    queryFn: ({ signal }) => getTeamMembers(teamId, includeFormer, page, signal),
    refetchInterval: 30_000, refetchOnWindowFocus: 'always',
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => {
    if (!query.error) return
    handleError(query.error)
    if (query.error instanceof ApiError && [403, 404].includes(query.error.status)) {
      void client.invalidateQueries({ queryKey: teamsKey(userId) })
    }
  }, [query.error, handleError, client, userId])
  return query
}

export function useEndMembership(userId: string, teamId: string) {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({
    mutationFn: (targetUserId: string | null) => targetUserId === null ? leaveTeam(teamId) : removeTeamMember(teamId, targetUserId),
    retry: false,
    onMutate: async () => {
      await Promise.all([
        client.cancelQueries({ queryKey: membersKey(userId, teamId) }),
        client.cancelQueries({ queryKey: [...dashboardKey(userId), teamId] }),
      ])
    },
    onError: handleError,
    // A lost response may follow a committed change: reconcile without repeating the write.
    onSettled: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: membersKey(userId, teamId) }),
        client.invalidateQueries({ queryKey: [...dashboardKey(userId), teamId] }),
        client.invalidateQueries({ queryKey: ['projects', userId, teamId] }),
        client.invalidateQueries({ queryKey: ['time-entries', userId] }),
        client.invalidateQueries({ queryKey: ['timer', userId] }),
        client.invalidateQueries({ queryKey: teamsKey(userId) }),
      ])
    },
  })
}
