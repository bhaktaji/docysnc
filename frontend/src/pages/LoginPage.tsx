import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, Card, Input } from '../components/ui'
import { errorMessage } from '../api/client'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <div className="mb-8">
          <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--text-muted)]">DocSync</div>
          <h2 className="mt-2 text-3xl font-semibold tracking-tight">Welcome back</h2>
          <p className="mt-2 text-sm text-[var(--text-muted)]">Sign in to manage repositories, analysis and documentation.</p>
        </div>
        <form onSubmit={submit} className="space-y-4">
          <Input placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} />
          {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
          <Button className="w-full" type="submit">Sign in</Button>
        </form>
        <p className="mt-6 text-sm text-[var(--text-muted)]">
          No account? <Link className="font-medium text-[var(--accent)]" to="/register">Create one</Link>
        </p>
      </Card>
    </div>
  )
}
