import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useOrg } from '../org/OrgContext'
import { analysisApi } from '../api/endpoints'
import { Badge, Button, Card, EmptyState, Input, PageHeader } from '../components/ui'
import { errorMessage } from '../api/client'

export function AnalysisPage() {
  const { selectedOrganizationId } = useOrg()
  const queryClient = useQueryClient()
  const [repositoryId, setRepositoryId] = useState('')
  const [error, setError] = useState('')

  const analysesQuery = useQuery({
    queryKey: ['analyses', selectedOrganizationId, repositoryId],
    queryFn: () => analysisApi.list(selectedOrganizationId!, repositoryId),
    enabled: !!selectedOrganizationId && !!repositoryId,
  })

  const findingsQuery = useQuery({
    queryKey: ['findings', selectedOrganizationId, repositoryId],
    queryFn: () => analysisApi.findings(selectedOrganizationId!, repositoryId),
    enabled: !!selectedOrganizationId && !!repositoryId,
  })

  const docsQuery = useQuery({
    queryKey: ['documentation', selectedOrganizationId, repositoryId],
    queryFn: () => analysisApi.documentation(selectedOrganizationId!, repositoryId),
    enabled: !!selectedOrganizationId && !!repositoryId,
  })

  const triggerMutation = useMutation({
    mutationFn: () => analysisApi.trigger(selectedOrganizationId!, repositoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['analyses', selectedOrganizationId, repositoryId] })
      queryClient.invalidateQueries({ queryKey: ['findings', selectedOrganizationId, repositoryId] })
      queryClient.invalidateQueries({ queryKey: ['documentation', selectedOrganizationId, repositoryId] })
    },
    onError: (err) => setError(errorMessage(err)),
  })

  if (!selectedOrganizationId) {
    return <EmptyState title="No organization selected" description="Select an organization to run repository analysis." />
  }

  return (
    <div className="fade-in">
      <PageHeader title="Analysis" description="Inspect code symbols, documentation impact and findings." />
      <Card className="mb-6">
        <div className="flex flex-col gap-3 md:flex-row">
          <Input className="md:max-w-xl" placeholder="Repository ID" value={repositoryId} onChange={(e) => setRepositoryId(e.target.value)} />
          <Button onClick={() => triggerMutation.mutate()} disabled={!repositoryId || triggerMutation.isPending}>Run analysis</Button>
        </div>
        {error && <p className="mt-3 text-sm text-[var(--danger)]">{error}</p>}
      </Card>

      <div className="grid gap-6 xl:grid-cols-3">
        <Card>
          <h3 className="text-lg font-semibold">Analysis runs</h3>
          <div className="mt-4 space-y-3">
            {(analysesQuery.data ?? []).map((analysis) => (
              <div key={analysis.id} className="rounded-xl bg-[var(--surface-muted)] p-4">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{analysis.status}</span>
                  <span className="text-xs text-[var(--text-muted)]">{analysis.scope}</span>
                </div>
                <div className="mt-2 text-xs text-[var(--text-muted)]">{analysis.commitSha}</div>
                {analysis.stats && <pre className="mt-3 max-h-40 overflow-auto rounded-lg bg-[var(--background)] p-3 text-xs">{JSON.stringify(analysis.stats, null, 2)}</pre>}
              </div>
            ))}
            {(analysesQuery.data ?? []).length === 0 && <p className="text-sm text-[var(--text-muted)]">No analysis runs yet.</p>}
          </div>
        </Card>

        <Card>
          <h3 className="text-lg font-semibold">Findings</h3>
          <div className="mt-4 space-y-3">
            {(findingsQuery.data ?? []).map((finding) => (
              <div key={finding.id} className="rounded-xl bg-[var(--surface-muted)] p-4">
                <div className="flex items-center gap-2">
                  <Badge tone={finding.severity === 'CRITICAL' ? 'danger' : finding.severity === 'WARNING' ? 'warning' : 'neutral'}>{finding.severity}</Badge>
                  <span className="font-medium">{finding.title}</span>
                </div>
                <p className="mt-2 text-sm text-[var(--text-muted)]">{finding.description}</p>
                {finding.filePath && <div className="mt-2 text-xs text-[var(--text-muted)]">{finding.filePath}</div>}
              </div>
            ))}
            {(findingsQuery.data ?? []).length === 0 && <p className="text-sm text-[var(--text-muted)]">No findings yet.</p>}
          </div>
        </Card>

        <Card>
          <h3 className="text-lg font-semibold">Documentation pages</h3>
          <div className="mt-4 space-y-2">
            {(docsQuery.data ?? []).map((page) => (
              <div key={page.id} className="rounded-xl bg-[var(--surface-muted)] px-4 py-3">
                <div className="font-medium">{page.path}</div>
                <div className="text-xs text-[var(--text-muted)]">{page.type}</div>
              </div>
            ))}
            {(docsQuery.data ?? []).length === 0 && <p className="text-sm text-[var(--text-muted)]">No documentation pages discovered.</p>}
          </div>
        </Card>
      </div>
    </div>
  )
}
