import { api } from './client'
import type {
  Analysis,
  AuthResponse,
  DocumentationPage,
  DocumentationPageDetail,
  Finding,
  GeneratedChange,
  Job,
  Member,
  Organization,
  Repository,
  RepositoryConnection,
  User,
} from './types'

// Re-export request payload types locally.
export interface RegisterRequest { email: string; password: string; displayName: string }
export interface LoginRequest { email: string; password: string }
export interface GitHubLoginRequest { code: string }
export interface CreateOrganizationRequest { name: string; slug: string; description?: string }
export interface UpdateOrganizationRequest { name?: string; description?: string }
export interface AddMemberRequest { email: string; role: string }
export interface ConnectRepositoryRequest {
  installationId: number
  fullName: string
  defaultBranch?: string
  configuration?: Partial<Repository['configuration']>
}
export interface UpdateRepositoryConfigurationRequest {
  autoAnalyze?: boolean
  analyzePullRequests?: boolean
  languages?: string[]
  excludePaths?: string[]
}

export const authApi = {
  register: (payload: RegisterRequest) => api.post<AuthResponse>('/auth/register', payload).then((r) => r.data),
  login: (payload: LoginRequest) => api.post<AuthResponse>('/auth/login', payload).then((r) => r.data),
  github: (payload: GitHubLoginRequest) => api.post<AuthResponse>('/auth/github', payload).then((r) => r.data),
  logout: () => api.delete('/auth/logout').then(() => undefined),
  me: () => api.get<User>('/users/me').then((r) => r.data),
  updateMe: (payload: { displayName: string }) => api.patch<User>('/users/me', payload).then((r) => r.data),
}

export const organizationApi = {
  list: () => api.get<Organization[]>('/organizations').then((r) => r.data),
  create: (payload: CreateOrganizationRequest) => api.post<Organization>('/organizations', payload).then((r) => r.data),
  get: (id: string) => api.get<Organization>(`/organizations/${id}`).then((r) => r.data),
  update: (id: string, payload: UpdateOrganizationRequest) => api.patch<Organization>(`/organizations/${id}`, payload).then((r) => r.data),
  members: (id: string) => api.get<Member[]>(`/organizations/${id}/members`).then((r) => r.data),
  addMember: (id: string, payload: AddMemberRequest) => api.post<Member>(`/organizations/${id}/members`, payload).then((r) => r.data),
  updateMember: (id: string, membershipId: string, role: string) =>
    api.patch<Member>(`/organizations/${id}/members/${membershipId}`, { role }).then((r) => r.data),
  removeMember: (id: string, membershipId: string) => api.delete(`/organizations/${id}/members/${membershipId}`).then(() => undefined),
}

export const connectionApi = {
  list: (organizationId: string) => api.get<RepositoryConnection[]>(`/organizations/${organizationId}/connections`).then((r) => r.data),
  claimable: () => api.get<RepositoryConnection[]>('/connections/claimable').then((r) => r.data),
  claim: (organizationId: string, connectionId: string) =>
    api.post<RepositoryConnection>(`/organizations/${organizationId}/connections/${connectionId}/claim`).then((r) => r.data),
  revoke: (organizationId: string, connectionId: string) =>
    api.delete<RepositoryConnection>(`/organizations/${organizationId}/connections/${connectionId}`).then((r) => r.data),
}

export const repositoryApi = {
  list: (organizationId: string, status?: string) =>
    api.get<Repository[]>(`/organizations/${organizationId}/repositories`, { params: status ? { status } : {} }).then((r) => r.data),
  connect: (organizationId: string, payload: ConnectRepositoryRequest) =>
    api.post<Repository>(`/organizations/${organizationId}/repositories`, payload).then((r) => r.data),
  get: (organizationId: string, repositoryId: string) =>
    api.get<Repository>(`/organizations/${organizationId}/repositories/${repositoryId}`).then((r) => r.data),
  updateConfiguration: (organizationId: string, repositoryId: string, payload: UpdateRepositoryConfigurationRequest) =>
    api.patch<Repository>(`/organizations/${organizationId}/repositories/${repositoryId}/configuration`, payload).then((r) => r.data),
  sync: (organizationId: string, repositoryId: string) =>
    api.post<Repository>(`/organizations/${organizationId}/repositories/${repositoryId}/sync`).then((r) => r.data),
  disconnect: (organizationId: string, repositoryId: string) =>
    api.delete(`/organizations/${organizationId}/repositories/${repositoryId}`).then(() => undefined),
}

export const analysisApi = {
  trigger: (organizationId: string, repositoryId: string, scope?: string) =>
    api.post<Analysis>(`/organizations/${organizationId}/repositories/${repositoryId}/analysis`, null, { params: scope ? { scope } : {} }).then((r) => r.data),
  list: (organizationId: string, repositoryId: string) =>
    api.get<Analysis[]>(`/organizations/${organizationId}/repositories/${repositoryId}/analysis`).then((r) => r.data),
  get: (organizationId: string, repositoryId: string, analysisId: string) =>
    api.get<Analysis>(`/organizations/${organizationId}/repositories/${repositoryId}/analysis/${analysisId}`).then((r) => r.data),
  findings: (organizationId: string, repositoryId: string) =>
    api.get<Finding[]>(`/organizations/${organizationId}/repositories/${repositoryId}/findings`).then((r) => r.data),
  documentation: (organizationId: string, repositoryId: string) =>
    api.get<DocumentationPage[]>(`/organizations/${organizationId}/repositories/${repositoryId}/documentation`).then((r) => r.data),
  documentationPage: (organizationId: string, repositoryId: string, pageId: string) =>
    api.get<DocumentationPageDetail>(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/${pageId}`).then((r) => r.data),
}

export const jobApi = {
  get: (jobId: string) => api.get<Job>(`/jobs/${jobId}`).then((r) => r.data),
  list: (params?: { organizationId?: string; repositoryId?: string; type?: string; status?: string }) =>
    api.get<Job[]>('/jobs', { params }).then((r) => r.data),
  listByRepository: (organizationId: string, repositoryId: string) =>
    api.get<Job[]>(`/organizations/${organizationId}/repositories/${repositoryId}/jobs`).then((r) => r.data),
}

export const generationApi = {
  generate: (organizationId: string, repositoryId: string, analysisId?: string) =>
    api.post<GeneratedChange[]>(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/generate`, null, {
      params: analysisId ? { analysisId } : {},
    }).then((r) => r.data),
  listChanges: (organizationId: string, repositoryId: string) =>
    api.get<GeneratedChange[]>(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/changes`).then((r) => r.data),
  getChange: (organizationId: string, repositoryId: string, changeId: string) =>
    api.get<GeneratedChange>(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/changes/${changeId}`).then((r) => r.data),
  approve: (organizationId: string, repositoryId: string, changeId: string) =>
    api.post(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/changes/${changeId}/approve`).then(() => undefined),
  reject: (organizationId: string, repositoryId: string, changeId: string) =>
    api.post(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/changes/${changeId}/reject`).then(() => undefined),
  regenerate: (organizationId: string, repositoryId: string, changeId: string) =>
    api.post<GeneratedChange>(`/organizations/${organizationId}/repositories/${repositoryId}/documentation/changes/${changeId}/regenerate`).then((r) => r.data),
}
