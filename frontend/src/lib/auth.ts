import { queryOptions, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getUser, login, logout, register } from './api'

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
