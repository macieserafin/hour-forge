import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, archiveProject, createProject, getProject, getProjects, updateProject, type ProjectInput } from './api'
import { teamsKey, useAuthError } from './teams'

const projectsKey = (userId: string, teamId: string) => ['projects', userId, teamId] as const
const retry = (count: number, error: Error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500)

export function useProjects(userId: string, teamId: string, includeArchived: boolean) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...projectsKey(userId, teamId), 'list', includeArchived],
    queryFn: ({ signal }) => getProjects(teamId, includeArchived, signal), retry })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useProject(userId: string, teamId: string, projectId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...projectsKey(userId, teamId), 'detail', projectId],
    queryFn: ({ signal }) => getProject(teamId, projectId, signal), retry })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

function useProjectRefresh(userId: string, teamId: string) {
  const client = useQueryClient()
  const handleError = useAuthError()
  const refresh = () => client.invalidateQueries({ queryKey: projectsKey(userId, teamId) })
  return {
    onSuccess: refresh,
    onError: (error: Error) => {
      handleError(error)
      if (error instanceof ApiError && [403, 404, 409].includes(error.status)) {
        void refresh()
        void client.invalidateQueries({ queryKey: teamsKey(userId) })
      }
    },
  }
}

export function useSaveProject(userId: string, teamId: string, projectId?: string) {
  const callbacks = useProjectRefresh(userId, teamId)
  return useMutation({ mutationFn: (body: ProjectInput) => projectId
    ? updateProject(teamId, projectId, body) : createProject(teamId, body), ...callbacks })
}

export function useArchiveProject(userId: string, teamId: string) {
  const callbacks = useProjectRefresh(userId, teamId)
  return useMutation({ mutationFn: (projectId: string) => archiveProject(teamId, projectId), ...callbacks })
}
