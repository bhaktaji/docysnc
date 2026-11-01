import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { OrganizationsPage } from './pages/OrganizationsPage'
import { RepositoriesPage } from './pages/RepositoriesPage'
import { ConnectionsPage } from './pages/ConnectionsPage'
import { AnalysisPage } from './pages/AnalysisPage'
import { DocumentationPage } from './pages/DocumentationPage'
import { JobsPage } from './pages/JobsPage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/organizations" element={<OrganizationsPage />} />
          <Route path="/repositories" element={<RepositoriesPage />} />
          <Route path="/connections" element={<ConnectionsPage />} />
          <Route path="/analysis" element={<AnalysisPage />} />
          <Route path="/documentation" element={<DocumentationPage />} />
          <Route path="/jobs" element={<JobsPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
