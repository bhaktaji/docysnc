import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Moon, Sun } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { useOrg } from '../org/OrgContext'
import { useTheme } from '../theme/ThemeContext'
import { Button } from './ui'

const navigation = [
  ['/', 'Dashboard'],
  ['/organizations', 'Organizations'],
  ['/repositories', 'Repositories'],
  ['/connections', 'Connections'],
  ['/analysis', 'Analysis'],
  ['/documentation', 'Documentation'],
  ['/jobs', 'Jobs'],
] as const

export function Layout() {
  const { user, logout } = useAuth()
  const { organizations, selectedOrganizationId, selectOrganization } = useOrg()
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-[var(--background)] text-[var(--text)]">
      <div className="mx-auto flex min-h-screen max-w-[1600px]">
        <aside className="hidden w-72 shrink-0 border-r border-[var(--border)] bg-[var(--surface)]/80 p-6 backdrop-blur md:block">
          <div className="mb-8">
            <div className="text-xs font-semibold uppercase tracking-[0.24em] text-[var(--text-muted)]">DocSync</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight">Control Center</div>
          </div>
          <nav className="space-y-1">
            {navigation.map(([path, label]) => (
              <NavLink
                key={path}
                to={path}
                className={({ isActive }) =>
                  `block rounded-xl px-4 py-3 text-sm font-medium transition ${
                    isActive ? 'bg-[var(--accent-soft)] text-[var(--accent)]' : 'text-[var(--text-muted)] hover:bg-[var(--surface-muted)] hover:text-[var(--text)]'
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-10 border-b border-[var(--border)] bg-[var(--surface)]/85 px-4 py-4 backdrop-blur md:px-8">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <div className="text-sm text-[var(--text-muted)]">Workspace</div>
                <div className="text-lg font-semibold">{user?.displayName}</div>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                {organizations.length > 0 && (
                  <select
                    value={selectedOrganizationId ?? ''}
                    onChange={(event) => selectOrganization(event.target.value)}
                    className="rounded-xl border border-[var(--border)] bg-[var(--surface)] px-4 py-2 text-sm"
                  >
                    {organizations.map((org) => (
                      <option key={org.id} value={org.id}>
                        {org.name}
                      </option>
                    ))}
                  </select>
                )}
                <button
                  type="button"
                  onClick={toggleTheme}
                  className="rounded-xl border border-[var(--border)] bg-[var(--surface)] p-2 text-[var(--text-muted)] transition hover:text-[var(--text)]"
                  aria-label="Toggle theme"
                >
                  {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
                </button>
                <Button
                  variant="secondary"
                  onClick={() => {
                    logout()
                    navigate('/login')
                  }}
                >
                  Logout
                </Button>
              </div>
            </div>
          </header>

          <main className="flex-1 px-4 py-6 md:px-8">
            <div className="fade-in">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </div>
  )
}
