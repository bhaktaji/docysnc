export interface User {
  id: string
  email: string
  displayName: string
  avatarUrl?: string | null
  githubId?: number | null
  githubLogin?: string | null
  createdAt?: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface Organization {
  id: string
  name: string
  slug: string
  description?: string | null
  plan: string
  currentUserRole: string
  memberCount?: number
  createdAt: string
  updatedAt: string
}

export interface Member {
  id: string
  userId: string
  email: string
  displayName: string
  role: string
  status: string
  joinedAt: string
}

export interface RepositoryConnection {
  id: string
  organizationId?: string | null
  provider: string
  installationId: number
  accountLogin?: string | null
  accountType?: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export interface RepositoryConfiguration {
  autoAnalyze: boolean
  analyzePullRequests: boolean
  languages: string[]
  excludePaths: string[]
}

export interface Repository {
  id: string
  organizationId: string
  connectionId: string
  provider: string
  installationId: number
  externalId: number
  name: string
  fullName: string
  cloneUrl?: string | null
  defaultBranch: string
  status: string
  configuration: RepositoryConfiguration
  errorMessage?: string | null
  lastSyncedAt?: string | null
  lastCommitSha?: string | null
  lastAnalysisAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface Analysis {
  id: string
  repositoryId: string
  jobId?: string | null
  branch: string
  commitSha: string
  scope: string
  status: string
  stats?: Record<string, unknown> | null
  errorMessage?: string | null
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
}

export interface Finding {
  id: string
  repositoryId: string
  analysisId: string
  severity: string
  category: string
  title: string
  description: string
  entityId?: string | null
  filePath?: string | null
  lineNumber?: number | null
  status: string
  createdAt: string
}

export interface DocumentationPage {
  id: string
  repositoryId: string
  analysisId: string
  title: string
  type: string
  path: string
  contentHash?: string | null
  lastAnalyzedCommit?: string | null
}

export interface DocumentationPageDetail extends DocumentationPage {
  content?: string | null
}

export interface Job {
  id: string
  repositoryId: string
  webhookEventId?: string | null
  type: string
  status: string
  priority: number
  triggerCommit?: string | null
  prNumber?: number | null
  errorMessage?: string | null
  retryCount: number
  maxRetries: number
  nextAttemptAt?: string | null
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
}

export interface GeneratedChange {
  id: string
  repositoryId: string
  analysisId?: string | null
  generationRunId: string
  filePath: string
  changeType: string
  originalContent?: string | null
  generatedContent: string
  diffContent?: string | null
  provenance?: string | null
  confidence?: number | null
  status: string
  version: number
  createdAt: string
}

export interface ApiError {
  code: string
  message: string
  traceId?: string
  timestamp?: string
}
