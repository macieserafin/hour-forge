import { useEffect } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ApiError,
  disconnectJiraAccount,
  disconnectJiraSite,
  getJiraIssue,
  getJiraIssues,
  getJiraSites,
  getJiraStatus,
  selectJiraSite,
  startJiraAuthorization,
  type JiraIntent,
  type JiraIssueFilters,
} from './api'
import { useAuthError } from './teams'

export const jiraKey = (userId: string, teamId: string) => ['jira', userId, teamId] as const

/** The only place that leaves the app for Atlassian; kept separate so it can be replaced in tests. */
export const navigation = {
  go(url: string) {
    const target = new URL(url)
    if (target.protocol !== 'https:' && target.protocol !== 'http:')
      throw new ApiError(0, 'Nieprawidłowy adres autoryzacji.')
    window.location.assign(target.toString())
  },
}

export function useJiraStatus(userId: string, teamId: string) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: jiraKey(userId, teamId),
    queryFn: ({ signal }) => getJiraStatus(teamId, signal),
    retry: false,
    staleTime: 0,
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

export function useJiraSites(userId: string, teamId: string, enabled: boolean) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: [...jiraKey(userId, teamId), 'sites'],
    queryFn: ({ signal }) => getJiraSites(teamId, signal),
    enabled,
    retry: false,
    staleTime: 0,
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

export function useJiraIssues(userId: string, teamId: string, filters: JiraIssueFilters, enabled: boolean) {
  const handleError = useAuthError()
  const query = useInfiniteQuery({
    queryKey: [...jiraKey(userId, teamId), 'issues', filters],
    queryFn: ({ pageParam, signal }) => getJiraIssues(teamId, filters, pageParam, signal),
    initialPageParam: null as string | null,
    getNextPageParam: page => page.nextPageToken,
    enabled,
    retry: false,
    staleTime: 15_000,
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

/** One issue, read live for a hover card; fetched only while the card is open and kept for a short while. */
export function useJiraIssue(userId: string, teamId: string, issueId: string, enabled: boolean) {
  return useQuery({
    queryKey: [...jiraKey(userId, teamId), 'issue', issueId],
    queryFn: ({ signal }) => getJiraIssue(teamId, issueId, signal),
    enabled,
    retry: false,
    staleTime: 60_000,
  })
}

export function useStartJira(teamId: string) {
  const onError = useAuthError()
  return useMutation({
    mutationFn: (intent: JiraIntent) => startJiraAuthorization({ teamId, intent }),
    onError,
    retry: false,
    gcTime: 0,
    onSuccess: ({ url }) => navigation.go(url),
  })
}

function useJiraChange<TVariables>(
  userId: string,
  teamId: string,
  mutationFn: (variables: TVariables) => Promise<null>,
) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({
    mutationFn,
    onError,
    retry: false,
    onSettled: () => client.invalidateQueries({ queryKey: jiraKey(userId, teamId) }),
  })
}

export const useSelectJiraSite = (userId: string, teamId: string) =>
  useJiraChange(userId, teamId, (cloudId: string) => selectJiraSite({ teamId, cloudId }))
export const useDisconnectJiraAccount = (userId: string, teamId: string) =>
  useJiraChange<void>(userId, teamId, () => disconnectJiraAccount(teamId))
export const useDisconnectJiraSite = (userId: string, teamId: string) =>
  useJiraChange<void>(userId, teamId, () => disconnectJiraSite(teamId))
