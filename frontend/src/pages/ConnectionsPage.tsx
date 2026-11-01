import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useOrg } from '../org/OrgContext'
import { connectionApi } from '../api/endpoints'
import { Button, Card, EmptyState, PageHeader } from '../components/ui'
import { errorMessage } from '../api/client'

export function ConnectionsPage() {
  const { selectedOrganizationId } = useOrg()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')

  const connectionsQuery = useQuery({
    queryKey: ['connections', selectedOrganizationId],
    queryFn: () => connectionApi.list(selectedOrganizationId!),
    enabled: !!selectedOrganizationId,
  })

  const claimableQuery = useQuery({
    queryKey: ['claimable-connections'],
    queryFn: () => connectionApi.claimable(),
  })

  const claimMutation = useMutation({
    mutationFn: (connectionId: string) => connectionApi.claim(selectedOrganizationId!, connectionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['connections', selectedOrganizationId] })
      queryClient.invalidateQueries({ queryKey: ['claimable-connections'] })
      queryClient.invalidateQueries({ queryKey: ['repositories', selectedOrganizationId] })
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const revokeMutation = useMutation({
    mutationFn: (connectionId: string) => connectionApi.revoke(selectedOrganizationId!, connectionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['connections', selectedOrganizationId] }),
    onError: (err) => setError(errorMessage(err)),
  })

  if (!selectedOrganizationId) {
    return <EmptyState title="No organization selected" description="Select an organization to manage GitHub installations." />
  }

  return (
    <div className="fade-in">
      <PageHeader title="GitHub connections" description="Review linked installations and claim pending installations." />
      {error && <p className="mb-4 text-sm text-[var(--danger)]">{error}</p>}
      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <h3 className="text-lg font-semibold">Linked installations</h3>
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-[var(--text-muted)]"><tr><th className="pb-3">Account</th><th className="pb-3">Installation</th><th className="pb-3">Status</th><th className="pb-3">Actions</th></tr></thead>
              <tbody>
                {(connectionsQuery.data ?? []).map((connection) => (
                  <tr key={connection.id} className="border-t border-[var(--border)]">
                    <td className="py-3">{connection.accountLogin ?? 'Unknown'}</td>
                    <td className="py-3">{connection.installationId}</td>
                    <td className="py-3"><span className="font-medium">{connection.status}</span></td>
                    <td className="py-3">
                      <Button variant="danger" onClick={() => revokeMutation.mutate(connection.id)}>Revoke</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(connectionsQuery.data ?? []).length === 0 && <p className="mt-4 text-sm text-[var(--text-muted)]">No linked installations.</p>}
          </div>
        </Card>

        <Card>
          <h3 className="text-lg font-semibold">Claimable installations</h3>
          <div className="mt-4 space-y-3">
            {(claimableQuery.data ?? []).length === 0 ? (
              <p className="text-sm text-[var(--text-muted)]">No pending installations for your GitHub account.</p>
            ) : (
              (claimableQuery.data ?? []).map((connection) => (
                <div key={connection.id} className="flex items-center justify-between rounded-xl bg-[var(--surface-muted)] px-4 py-3">
                  <div>
                    <div className="font-medium">{connection.accountLogin ?? 'GitHub installation'}</div>
                    <div className="text-xs text-[var(--text-muted)]">Installation {connection.installationId}</div>
                  </div>
                  <Button onClick={() => claimMutation.mutate(connection.id)}>Claim</Button>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
