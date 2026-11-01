import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { organizationApi } from '../api/endpoints'
import type { Organization } from '../api/types'

interface OrgContextValue {
  organizations: Organization[]
  selectedOrganizationId: string | null
  selectedOrganization: Organization | null
  refreshOrganizations: () => Promise<void>
  selectOrganization: (id: string) => void
  createOrganization: (name: string, slug: string) => Promise<Organization>
}

const OrgContext = createContext<OrgContextValue | undefined>(undefined)

export function OrgProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [selectedOrganizationId, setSelectedOrganizationId] = useState<string | null>(
    sessionStorage.getItem('docsync_org'),
  )

  const refreshOrganizations = async () => {
    const orgs = await organizationApi.list()
    setOrganizations(orgs)
    if (orgs.length === 0) {
      setSelectedOrganizationId(null)
      sessionStorage.removeItem('docsync_org')
      return
    }
    if (!selectedOrganizationId || !orgs.some((org) => org.id === selectedOrganizationId)) {
      setSelectedOrganizationId(orgs[0].id)
      sessionStorage.setItem('docsync_org', orgs[0].id)
    }
  }

  const selectOrganization = (id: string) => {
    setSelectedOrganizationId(id)
    sessionStorage.setItem('docsync_org', id)
  }

  const createOrganization = async (name: string, slug: string) => {
    const org = await organizationApi.create({ name, slug })
    await refreshOrganizations()
    selectOrganization(org.id)
    return org
  }

  useEffect(() => {
    if (token) refreshOrganizations().catch(() => undefined)
    else setOrganizations([])
  }, [token])

  const selectedOrganization = organizations.find((org) => org.id === selectedOrganizationId) ?? null

  return (
    <OrgContext.Provider
      value={{ organizations, selectedOrganizationId, selectedOrganization, refreshOrganizations, selectOrganization, createOrganization }}
    >
      {children}
    </OrgContext.Provider>
  )
}

export function useOrg() {
  const context = useContext(OrgContext)
  if (!context) throw new Error('useOrg must be used within OrgProvider')
  return context
}
