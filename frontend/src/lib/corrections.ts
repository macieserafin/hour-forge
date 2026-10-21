import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, addMemberTimeEntry, correctMemberTimeEntry, deleteMemberTimeEntry, getMemberTimeEntries } from './api'
import { dashboardKey } from './dashboard'
import { useAuthError } from './teams'

const correctionsKey = (userId: string, teamId: string, memberId: string) => ['member-time-entries', userId, teamId, memberId] as const

export function useMemberTimeEntries(userId: string, teamId: string, memberId: string, page: number) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...correctionsKey(userId, teamId, memberId), page],
    queryFn: ({ signal }) => getMemberTimeEntries(teamId, memberId, page, signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

function useCorrectionCallbacks(userId: string, teamId: string, memberId: string) {
  const client = useQueryClient()
  const handleError = useAuthError()
  return {
    retry: false as const,
    onError: handleError,
    // A lost response may follow a committed change: reconcile instead of repeating the write.
    onSettled: () => Promise.all([
      client.invalidateQueries({ queryKey: correctionsKey(userId, teamId, memberId) }),
      client.invalidateQueries({ queryKey: dashboardKey(userId) }),
    ]),
  }
}

export const useAddMemberEntry = (userId: string, teamId: string, memberId: string) =>
  useMutation({ mutationFn: addMemberTimeEntry, ...useCorrectionCallbacks(userId, teamId, memberId) })
export const useCorrectEntry = (userId: string, teamId: string, memberId: string) =>
  useMutation({ mutationFn: correctMemberTimeEntry, ...useCorrectionCallbacks(userId, teamId, memberId) })
export const useDeleteMemberEntry = (userId: string, teamId: string, memberId: string) =>
  useMutation({ mutationFn: deleteMemberTimeEntry, ...useCorrectionCallbacks(userId, teamId, memberId) })
