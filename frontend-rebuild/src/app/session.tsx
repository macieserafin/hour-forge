import { createContext, useContext, type ReactNode } from 'react'
import type { User } from '../lib/api'

/** The signed-in person; the router renders the app only after the session is known. */
const Context = createContext<User | null>(null)

export function UserProvider({ user, children }: { user: User; children: ReactNode }) {
  return <Context.Provider value={user}>{children}</Context.Provider>
}

export function useUser(): User {
  const user = useContext(Context)
  if (!user) throw new Error('useUser outside a signed-in page.')
  return user
}
