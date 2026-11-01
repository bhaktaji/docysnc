import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { authApi } from '../api/endpoints'
import { setAccessToken, getAccessToken } from '../api/client'
import type { User } from '../api/types'

interface AuthContextValue {
  user: User | null
  token: string | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(getAccessToken())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const current = getAccessToken()
    if (!current) {
      setLoading(false)
      return
    }
    authApi
      .me()
      .then((profile) => setUser(profile))
      .catch(() => {
        setAccessToken(null)
        setToken(null)
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const login = async (email: string, password: string) => {
    const response = await authApi.login({ email, password })
    setAccessToken(response.accessToken)
    setToken(response.accessToken)
    setUser(response.user)
  }

  const register = async (email: string, password: string, displayName: string) => {
    const response = await authApi.register({ email, password, displayName })
    setAccessToken(response.accessToken)
    setToken(response.accessToken)
    setUser(response.user)
  }

  const logout = () => {
    setAccessToken(null)
    setToken(null)
    setUser(null)
    sessionStorage.removeItem('docsync_org')
  }

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
