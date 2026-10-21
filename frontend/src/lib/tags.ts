import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, archiveTag, createTag, getTags, restoreTag, updateTag } from './api'
import { useAuthError } from './teams'

export const tagsKey = (userId: string, teamId: string) => ['tags', userId, teamId] as const

export function useTags(userId: string, teamId: string, includeArchived = false, enabled = true) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...tagsKey(userId, teamId), includeArchived], enabled,
    queryFn: ({ signal }) => getTags(teamId, includeArchived, signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

function useTagMutation<TInput>(userId: string, teamId: string, mutationFn: (input: TInput) => Promise<unknown>) {
  const client = useQueryClient()
  const handleError = useAuthError()
  // A lost response may follow a committed change: reconcile instead of repeating the write.
  return useMutation({ mutationFn, retry: false, onError: handleError,
    onSettled: () => client.invalidateQueries({ queryKey: tagsKey(userId, teamId) }) })
}

export const useCreateTag = (userId: string, teamId: string) => useTagMutation(userId, teamId, createTag)
export const useUpdateTag = (userId: string, teamId: string) => useTagMutation(userId, teamId, updateTag)
export const useArchiveTag = (userId: string, teamId: string) => useTagMutation(userId, teamId, archiveTag)
export const useRestoreTag = (userId: string, teamId: string) => useTagMutation(userId, teamId, restoreTag)
