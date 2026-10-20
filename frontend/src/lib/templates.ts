import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createTemplate, deleteTemplate, getTemplates, type EntryTemplate } from './api'
import { useAuthError } from './teams'

export const templatesKey = (userId: string, teamId: string) => ['templates', userId, teamId] as const

export function useTemplates(userId: string, teamId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: templatesKey(userId, teamId), queryFn: ({ signal }) => getTemplates(teamId, signal), retry: false, staleTime: 60_000 })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useCreateTemplate(userId: string, teamId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: (input: Parameters<typeof createTemplate>[1]) => createTemplate(teamId, input), onError, retry: false,
    onSuccess: created => client.setQueryData<EntryTemplate[]>(templatesKey(userId, teamId), previous => [...(previous ?? []), created].sort((a, b) => a.name.localeCompare(b.name, 'pl'))),
    onSettled: () => client.invalidateQueries({ queryKey: templatesKey(userId, teamId) }) })
}

export function useDeleteTemplate(userId: string, teamId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: (templateId: string) => deleteTemplate(teamId, templateId), onError, retry: false,
    onSettled: () => client.invalidateQueries({ queryKey: templatesKey(userId, teamId) }) })
}
