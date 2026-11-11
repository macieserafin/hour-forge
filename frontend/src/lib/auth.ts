import { queryOptions, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { usePublicConfig } from './operator'
import {
  answerTwoFactor,
  confirmEmail,
  confirmEmailChange,
  confirmPasswordReset,
  getUser,
  login,
  logout,
  register,
  requestPasswordReset,
  resendVerification,
} from './api'

export const sessionOptions = queryOptions({
  queryKey: ['session'],
  queryFn: ({ signal }) => getUser(signal),
})

/**
 * Called once the server has started a session. Data fetched before it, including requests that failed with 401 while
 * the old session was dead, must not survive: the same account signing in again reuses the same query keys, and a
 * cached 401 would sign the person straight out again, leaving the login form looking like it did nothing.
 */
async function startSession(client: QueryClient) {
  await client.cancelQueries()
  client.removeQueries({ predicate: query => query.queryKey[0] !== sessionOptions.queryKey[0] })
  await client.invalidateQueries({ queryKey: sessionOptions.queryKey })
}

export function useSession() {
  return useQuery(sessionOptions)
}

/** Google controls appear only when the server says so; while the settings load, or if they fail, nothing about Google is shown. */
export function useGoogleSignIn() {
  return usePublicConfig().data?.googleSignIn === true
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
  return useMutation({
    mutationFn: confirmEmail,
    retry: false,
    gcTime: 0,
    onSuccess: () => client.invalidateQueries({ queryKey: sessionOptions.queryKey }),
  })
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
      if (result === 'SIGNED_IN') await startSession(client)
    },
  })
}

export function useAnswerTwoFactor() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: answerTwoFactor,
    retry: false,
    onSuccess: async () => {
      await startSession(client)
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
