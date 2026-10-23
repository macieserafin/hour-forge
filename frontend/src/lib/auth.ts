import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { answerTwoFactor, confirmEmail, confirmEmailChange, confirmPasswordReset, getUser, login, logout, register, requestPasswordReset, resendVerification } from './api'

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
    onSuccess: async result => {
      // With two-step sign-in the session starts only after the code.
      if (result === 'SIGNED_IN') await client.invalidateQueries({ queryKey: sessionOptions.queryKey })
    },
  })
}

export function useAnswerTwoFactor() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: answerTwoFactor,
    retry: false,
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
