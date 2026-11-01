import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useOrg } from '../org/OrgContext'
import { organizationApi } from '../api/endpoints'
import { Button, Card, EmptyState, Input, PageHeader } from '../components/ui'
import { errorMessage } from '../api/client'

export function OrganizationsPage() {
  const { refreshOrganizations, selectOrganization } = useOrg()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [error, setError] = useState('')

  const orgsQuery = useQuery({
    queryKey: ['organizations'],
    queryFn: () => organizationApi.list(),
  })

  const createMutation = useMutation({
    mutationFn: () => organizationApi.create({ name, slug }),
    onSuccess: async (org) => {
      setName('')
      setSlug('')
      setError('')
      await refreshOrganizations()
      selectOrganization(org.id)
      queryClient.invalidateQueries({ queryKey: ['organizations'] })
    },
    onError: (err) => setError(errorMessage(err)),
  })

  return (
    <div className="fade-in">
      <PageHeader title="Organizations" description="Create and manage tenant workspaces." />
      <div className="grid gap-6 xl:grid-cols-[380px_1fr]">
        <Card>
          <h3 className="text-lg font-semibold">Create organization</h3>
          <form className="mt-4 space-y-4" onSubmit={(event) => { event.preventDefault(); createMutation.mutate() }}>
            <Input placeholder="Organization name" value={name} onChange={(e) => setName(e.target.value)} />
            <Input placeholder="slug" value={slug} onChange={(e) => setSlug(e.target.value)} />
            {error && <p className="text-sm text-[var(--danger)]">{error}</p>}
            <Button type="submit" disabled={createMutation.isPending}>Create organization</Button>
          </form>
        </Card>

        <Card>
          <h3 className="text-lg font-semibold">Your organizations</h3>
          <div className="mt-4 space-y-3">
            {(orgsQuery.data ?? []).length === 0 ? (
              <EmptyState title="No organizations" description="Create your first organization to start onboarding repositories." />
            ) : (
              (orgsQuery.data ?? []).map((org) => (
                <div key={org.id} className="flex items-center justify-between rounded-xl bg-[var(--surface-muted)] px-4 py-3">
                  <div>
                    <div className="font-medium">{org.name}</div>
                    <div className="text-xs text-[var(--text-muted)]">{org.slug} · {org.memberCount ?? 0} members</div>
                  </div>
                  <span className="text-sm font-medium">{org.currentUserRole}</span>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
