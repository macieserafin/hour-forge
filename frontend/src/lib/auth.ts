import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { confirmEmail, confirmEmailChange, confirmPasswordReset, getAuthOptions, getUser, login, logout, register, requestPasswordReset, resendVerification } from './api'

export const sessionOptions = queryOptions({
  queryKey: ['session'],
  queryFn: ({ signal }) => getUser(signal),
})

export function useSession() {
  return useQuery(sessionOptions)
}

export function useRegister() {
  return useMutation({ mutationFn: register, retry: false, gcTime: 0 })
}

export function useAuthOptions() {
  return useQuery({ queryKey: ['auth-options'], queryFn: ({ signal }) => getAuthOptions(signal), staleTime: 5 * 60_000 })
}

export function useRequestPasswordReset() {
  return useMutation({ mutationFn: requestPasswordReset, retry: false, gcTime: 0 })
}

export function useConfirmPasswordReset() {
  return useMutation({ mutationFn: confirmPasswordReset, retry: false, gcTime: 0 })
}

export function useConfirmEmailChange() {
  return useMutation({ mutationFn: confirmEmailChange, retry: false, gcTime: 0 })
}

export function useConfirmEmail() {
  const client = useQueryClient()
  return useMutation({ mutationFn: confirmEmail, retry: false, gcTime: 0,
    onSuccess: () => client.invalidateQueries({ queryKey: sessionOptions.queryKey }) })
}

export function useResendVerification() {
  return useMutation({ mutationFn: resendVerification, retry: false, gcTime: 0 })
}

export function useLogin() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: login,
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: sessionOptions.queryKey })
    },
  })
}

export function useLogout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: async () => {
      await client.cancelQueries()
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' })
      client.setQueryData(sessionOptions.queryKey, null)
    },
  })
}
