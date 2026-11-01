import axios from 'axios'

export const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

let accessToken: string | null = sessionStorage.getItem('docsync_token')

export function setAccessToken(token: string | null) {
  accessToken = token
  if (token) sessionStorage.setItem('docsync_token', token)
  else sessionStorage.removeItem('docsync_token')
}

export function getAccessToken() {
  return accessToken
}

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; code?: string } | undefined
    return data?.message || error.message
  }
  return error instanceof Error ? error.message : 'Unexpected error'
}
