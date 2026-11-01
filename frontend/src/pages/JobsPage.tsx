import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useOrg } from '../org/OrgContext'
import { jobApi } from '../api/endpoints'
import { Badge, Card, EmptyState, Input, PageHeader } from '../components/ui'

export function JobsPage() {
  const { selectedOrganizationId } = useOrg()
  const [repositoryId, setRepositoryId] = useState('')
  const query = useQuery({
    queryKey: ['jobs', selectedOrganizationId, repositoryId],
    queryFn: () => repositoryId
      ? jobApi.listByRepository(selectedOrganizationId!, repositoryId)
      : jobApi.list({ organizationId: selectedOrganizationId! }),
    enabled: !!selectedOrganizationId,
    refetchInterval: 5000,
  })

  if (!selectedOrganizationId) {
    return <EmptyState title="No organization selected" description="Select an organization to inspect background jobs." />
  }

  return (
    <div className="fade-in">
      <PageHeader title="Jobs" description="Monitor asynchronous processing, retries and failures." />
      <Card className="mb-6">
        <Input className="md:max-w-xl" placeholder="Filter by repository ID" value={repositoryId} onChange={(e) => setRepositoryId(e.target.value)} />
      </Card>
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-[var(--text-muted)]">
              <tr><th className="pb-3">Type</th><th className="pb-3">Status</th><th className="pb-3">Commit</th><th className="pb-3">Retries</th><th className="pb-3">Created</th></tr>
            </thead>
            <tbody>
              {(query.data ?? []).map((job) => (
                <tr key={job.id} className="border-t border-[var(--border)]">
                  <td className="py-3">{job.type}</td>
                  <td className="py-3"><Badge tone={job.status === 'FAILED' ? 'danger' : job.status === 'COMPLETED' ? 'success' : 'accent'}>{job.status}</Badge></td>
                  <td className="py-3 font-mono text-xs">{job.triggerCommit ?? '-'}</td>
                  <td className="py-3">{job.retryCount}/{job.maxRetries}</td>
                  <td className="py-3 text-[var(--text-muted)]">{new Date(job.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {(query.data ?? []).length === 0 && <p className="mt-4 text-sm text-[var(--text-muted)]">No jobs found.</p>}
        </div>
      </Card>
    </div>
  )
}
