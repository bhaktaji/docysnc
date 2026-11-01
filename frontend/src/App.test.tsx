/// <reference types="vitest/globals" />
import '@testing-library/jest-dom'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/AuthContext'
import { OrgProvider } from './org/OrgContext'
import { App } from './App'

test('renders login screen', () => {
  const queryClient = new QueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <OrgProvider>
          <MemoryRouter initialEntries={['/login']}>
            <App />
          </MemoryRouter>
        </OrgProvider>
      </AuthProvider>
    </QueryClientProvider>,
  )
  expect(screen.getByText('Sign in')).toBeTruthy()
})
