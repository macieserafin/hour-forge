import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { blockAccount, clearAnnouncement, deleteAccountAsOperator, deleteTeamAsOperator, getOperatorAccounts, getOperatorStats,
  getOperatorTeams, getPublicConfig, saveAnnouncement, turnOffTwoFactorFor, unblockAccount } from './api'
import { useAuthError } from './teams'

export const publicConfigKey = ['public-config'] as const

/** Settings shared before sign-in: the bot check key and the operator's notice. */
export function usePublicConfig() {
  return useQuery({ queryKey: publicConfigKey, queryFn: ({ signal }) => getPublicConfig(signal), staleTime: 5 * 60_000, retry: 1 })
}

export function useOperatorStats() {
  return useQuery({ queryKey: ['operator', 'stats'], queryFn: ({ signal }) => getOperatorStats(signal), retry: false })
}

export function useOperatorAccounts(query: string, page: number) {
  return useQuery({ queryKey: ['operator', 'accounts', query, page], queryFn: ({ signal }) => getOperatorAccounts(query, page, signal),
    placeholderData: keepPreviousData, retry: false })
}

export function useOperatorTeams(query: string, page: number) {
  return useQuery({ queryKey: ['operator', 'teams', query, page], queryFn: ({ signal }) => getOperatorTeams(query, page, signal),
    placeholderData: keepPreviousData, retry: false })
}

/** Every operator change refreshes the operator lists, the statistics and the public notice. */
export function useOperatorActions() {
  const client = useQueryClient()
  const onError = useAuthError()
  const onSettled = async () => {
    await client.invalidateQueries({ queryKey: ['operator'] })
    await client.invalidateQueries({ queryKey: publicConfigKey })
  }
  const options = { retry: false, onError, onSettled } as const
  return {
    block: useMutation({ mutationFn: blockAccount, ...options }),
    unblock: useMutation({ mutationFn: unblockAccount, ...options }),
    turnOffTwoFactor: useMutation({ mutationFn: turnOffTwoFactorFor, ...options }),
    deleteAccount: useMutation({ mutationFn: deleteAccountAsOperator, ...options }),
    deleteTeam: useMutation({ mutationFn: deleteTeamAsOperator, ...options }),
    announce: useMutation({ mutationFn: saveAnnouncement, ...options }),
    clearAnnouncement: useMutation({ mutationFn: clearAnnouncement, ...options }),
  }
}
