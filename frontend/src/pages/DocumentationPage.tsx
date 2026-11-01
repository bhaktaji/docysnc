import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import ReactDiffViewer from 'react-diff-viewer-continued'
import { useOrg } from '../org/OrgContext'
import { generationApi } from '../api/endpoints'
import { Badge, Button, Card, EmptyState, Input, PageHeader } from '../components/ui'
import { errorMessage } from '../api/client'

export function DocumentationPage() {
  const { selectedOrganizationId } = useOrg()
  const queryClient = useQueryClient()
  const [repositoryId, setRepositoryId] = useState('')
  const [selectedChangeId, setSelectedChangeId] = useState<string | null>(null)
  const [error, setError] = useState('')

  const changesQuery = useQuery({
    queryKey: ['documentation-changes', selectedOrganizationId, repositoryId],
    queryFn: () => generationApi.listChanges(selectedOrganizationId!, repositoryId),
    enabled: !!selectedOrganizationId && !!repositoryId,
  })

  const selectedChange = (changesQuery.data ?? []).find((change) => change.id === selectedChangeId) ?? null

  const generateMutation = useMutation({
    mutationFn: () => generationApi.generate(selectedOrganizationId!, repositoryId),
    onSuccess: (changes) => {
      if (changes.length > 0) setSelectedChangeId(changes[0].id)
      queryClient.invalidateQueries({ queryKey: ['documentation-changes', selectedOrganizationId, repositoryId] })
    },
    onError: (err) => setError(errorMessage(err)),
  })

  const approveMutation = useMutation({
    mutationFn: (changeId: string) => generationApi.approve(selectedOrganizationId!, repositoryId, changeId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documentation-changes', selectedOrganizationId, repositoryId] }),
    onError: (err) => setError(errorMessage(err)),
  })

  const rejectMutation = useMutation({
    mutationFn: (changeId: string) => generationApi.reject(selectedOrganizationId!, repositoryId, changeId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['documentation-changes', selectedOrganizationId, repositoryId] }),
    onError: (err) => setError(errorMessage(err)),
  })

  const regenerateMutation = useMutation({
    mutationFn: (changeId: string) => generationApi.regenerate(selectedOrganizationId!, repositoryId, changeId),
    onSuccess: (change) => {
      setSelectedChangeId(change.id)
      queryClient.invalidateQueries({ queryKey: ['documentation-changes', selectedOrganizationId, repositoryId] })
    },
    onError: (err) => setError(errorMessage(err)),
  })

  if (!selectedOrganizationId) {
    return <EmptyState title="No organization selected" description="Select an organization to review documentation." />
  }

  return (
    <div className="fade-in">
      <PageHeader title="Documentation Studio" description="Generate, review and regenerate documentation changes." />
      <Card className="mb-6">
        <div className="flex flex-col gap-3 md:flex-row">
          <Input className="md:max-w-xl" placeholder="Repository ID" value={repositoryId} onChange={(e) => setRepositoryId(e.target.value)} />
          <Button onClick={() => generateMutation.mutate()} disabled={!repositoryId || generateMutation.isPending}>Generate updates</Button>
        </div>
        {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
      </Card>

      <div className="grid gap-6 xl:grid-cols-[360px_1fr]">
        <Card>
          <h3 className="text-lg font-semibold">Generated changes</h3>
          <div className="mt-4 space-y-3">
            {(changesQuery.data ?? []).map((change) => (
              <button
                key={change.id}
                type="button"
                onClick={() => setSelectedChangeId(change.id)}
                className={`w-full rounded-xl border px-4 py-3 text-left transition ${selectedChangeId === change.id ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-[var(--border)] bg-[var(--surface-muted)] hover:bg-[var(--surface)]'}`}
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="font-medium">{change.filePath}</span>
                  <Badge tone={change.status === 'APPROVED' ? 'success' : change.status === 'REJECTED' ? 'danger' : 'accent'}>{change.status}</Badge>
                </div>
                <div className="mt-1 text-xs text-[var(--text-muted)]">Version {change.version}</div>
              </button>
            ))}
            {(changesQuery.data ?? []).length === 0 && <p className="text-sm text-[var(--text-muted)]">No generated changes yet.</p>}
          </div>
        </Card>

        <Card>
          {selectedChange ? (
            <div className="space-y-5">
              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" onClick={() => approveMutation.mutate(selectedChange.id)} disabled={approveMutation.isPending}>Approve</Button>
                <Button variant="danger" onClick={() => rejectMutation.mutate(selectedChange.id)} disabled={rejectMutation.isPending}>Reject</Button>
                <Button variant="secondary" onClick={() => regenerateMutation.mutate(selectedChange.id)} disabled={regenerateMutation.isPending}>Regenerate</Button>
              </div>
              <div>
                <h3 className="font-semibold">Preview</h3>
                <div className="prose mt-3 max-w-none rounded-xl border border-[var(--border)] bg-[var(--surface-muted)] p-5">
                  <ReactMarkdown>{selectedChange.generatedContent}</ReactMarkdown>
                </div>
              </div>
              <div>
                <h3 className="font-semibold">Unified diff</h3>
                <div className="mt-3 overflow-hidden rounded-xl border border-[var(--border)]">
                  <ReactDiffViewer oldValue={selectedChange.originalContent ?? ''} newValue={selectedChange.generatedContent} splitView />
                </div>
              </div>
            </div>
          ) : (
            <EmptyState title="No change selected" description="Generate documentation updates and select a change to review." />
          )}
        </Card>
      </div>
    </div>
  )
}
