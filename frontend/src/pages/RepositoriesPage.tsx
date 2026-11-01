import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useOrg } from '../org/OrgContext'
import { repositoryApi } from '../api/endpoints'
import { Button, Card, EmptyState, Input, PageHeader } from '../components/ui'
import { errorMessage } from '../api/client'

export function RepositoriesPage() {
  const { selectedOrganizationId } = useOrg()
  const queryClient = useQueryClient()
  const [fullName, setFullName] = useState('')
  const [installationId, setInstallationId] = useState('')
  const [error, setError] = useState('')

  const reposQuery = useQuery({
    queryKey: ['repositories', selectedOrganizationId],
    queryFn: () => repositoryApi.list(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  })

  const connectMutation = useMutation({
    mutationFn: () => repositoryApi.connect(selectedOrganizationId!, {
      installationId: Number(installationId),
      fullName,
    }),
    onSuccess: () => {
      setFullName('')
      setInstallationId('')
      setError('')
      queryClient.invalidateQueries({ queryKey: ['repositories', selectedOrganizationId] })
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const syncMutation = useMutation({
    mutationFn: (repositoryId: string) => repositoryApi.sync(selectedOrganizationId!, repositoryId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['repositories', selectedOrganizationId] }),
  })

  const disconnectMutation = useMutation({
    mutationFn: (repositoryId: string) => repositoryApi.disconnect(selectedOrganizationId!, repositoryId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['repositories', selectedOrganizationId] }),
  })

  if (!selectedOrganizationId) {
    return <EmptyState title="No organization selected" description="Select an organization to manage repositories." />
  }

  return (
    <div className="fade-in">
      <PageHeader title="Repositories" description="Connect, synchronize and archive repositories." />
      <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
        <Card>
          <h3 className="text-lg font-semibold">Connect repository</h3>
          <form className="mt-4 space-y-4" onSubmit={(event) => { event.preventDefault(); connectMutation.mutate() }}>
            <Input placeholder="Installation ID" value={installationId} onChange={(e) => setInstallationId(e.target.value)} />
            <Input placeholder="owner/repository" value={fullName} onChange={(e) => setFullName(e.target.value)} />
            {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
            <Button type="submit" disabled={connectMutation.isPending}>Connect</Button>
          </form>
        </Card>

        <Card>
          <h3 className="text-lg font-semibold">Connected repositories</h3>
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-[var(--text-muted)]">
                <tr><th className="pb-3">Repository</th><th className="pb-3">Status</th><th className="pb-3">Branch</th><th className="pb-3">Actions</th></tr>
              </thead>
              <tbody>
                {(reposQuery.data ?? []).map((repo) => (
                  <tr key={repo.id} className="border-t border-[var(--border)]">
                    <td className="py-3">
                      <div className="font-medium">{repo.fullName}</div>
                      <div className="text-xs text-[var(--text-muted)]">{repo.cloneUrl}</div>
                    </td>
                    <td className="py-3">{repo.status}</td>
                    <td className="py-3">{repo.defaultBranch}</td>
                    <td className="py-3">
                      <div className="flex gap-2">
                        <Button variant="secondary" onClick={() => syncMutation.mutate(repo.id)}>Sync</Button>
                        <Button variant="danger" onClick={() => disconnectMutation.mutate(repo.id)}>Disconnect</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(reposQuery.data ?? []).length === 0 && <p className="mt-4 text-sm text-[var(--text-muted)]">No repositories connected.</p>}
          </div>
        </Card>
      </div>
    </div>
  )
}
