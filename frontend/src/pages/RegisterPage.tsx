import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Button, Card, Input } from '../components/ui'
import { errorMessage } from '../api/client'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await register(email, password, displayName)
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
          <h2 className="mt-2 text-3xl font-semibold tracking-tight">Create your workspace</h2>
          <p className="mt-2 text-sm text-[var(--text-muted)]">Start with a secure account and organization.</p>
        </div>
        <form onSubmit={submit} className="space-y-4">
          <Input placeholder="Display name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          <Input placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} />
          {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
          <Button className="w-full" type="submit">Create account</Button>
        </form>
        <p className="mt-6 text-sm text-[var(--text-muted)]">
          Already have an account? <Link className="font-medium text-[var(--accent)]" to="/login">Sign in</Link>
        </p>
      </Card>
    </div>
  )
}
