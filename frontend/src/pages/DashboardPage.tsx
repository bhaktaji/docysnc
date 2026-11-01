import { useQuery } from '@tanstack/react-query'
import { useOrg } from '../org/OrgContext'
import { repositoryApi, jobApi } from '../api/endpoints'
import { Card, EmptyState, PageHeader, StatCard } from '../components/ui'

export function DashboardPage() {
  const { selectedOrganizationId, selectedOrganization } = useOrg()
  const reposQuery = useQuery({
    queryKey: ['repositories', selectedOrganizationId],
    queryFn: () => repositoryApi.list(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  })
  const jobsQuery = useQuery({
    queryKey: ['jobs', selectedOrganizationId],
    queryFn: () => jobApi.list({ organizationId: selectedOrganizationId! }),
    enabled: !!selectedOrganizationId,
  })

  if (!selectedOrganizationId) {
    return <EmptyState title="No organization selected" description="Create or select an organization to begin onboarding repositories." />
  }

  const repos = reposQuery.data ?? []
  const jobs = jobsQuery.data ?? []

  return (
    <div className="fade-in">
      <PageHeader
        title="Dashboard"
        description={`Operational overview for ${selectedOrganization?.name ?? 'your organization'}.`}
      />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Repositories" value={repos.length} hint="Connected to DocSync" />
        <StatCard label="Ready" value={repos.filter((repo) => repo.status === 'READY').length} hint="Successfully synchronized" />
        <StatCard label="Active jobs" value={jobs.filter((job) => job.status === 'QUEUED' || job.status === 'RUNNING').length} hint="Queue and processing" />
        <StatCard label="Failed jobs" value={jobs.filter((job) => job.status === 'FAILED').length} hint="Requires attention" />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <Card>
          <h3 className="text-lg font-semibold">Recent repositories</h3>
          <div className="mt-4 space-y-3">
            {repos.length === 0 ? (
              <p className="text-sm text-[var(--text-muted)]">No repositories connected yet.</p>
            ) : (
              repos.slice(0, 6).map((repo) => (
                <div key={repo.id} className="flex items-center justify-between rounded-xl bg-[var(--surface-muted)] px-4 py-3">
                  <div>
                    <div className="font-medium">{repo.fullName}</div>
                    <div className="text-xs text-[var(--text-muted)]">{repo.defaultBranch}</div>
                  </div>
                  <span className="text-sm font-medium">{repo.status}</span>
                </div>
              ))
            )}
          </div>
        </Card>

        <Card>
          <h3 className="text-lg font-semibold">Recent jobs</h3>
          <div className="mt-4 space-y-3">
            {jobs.length === 0 ? (
              <p className="text-sm text-[var(--text-muted)]">No background jobs yet.</p>
            ) : (
              jobs.slice(0, 6).map((job) => (
                <div key={job.id} className="flex items-center justify-between rounded-xl bg-[var(--surface-muted)] px-4 py-3">
                  <div>
                    <div className="font-medium">{job.type}</div>
                    <div className="text-xs text-[var(--text-muted)]">{job.triggerCommit ?? 'No commit'}</div>
                  </div>
                  <span className="text-sm font-medium">{job.status}</span>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
