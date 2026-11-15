import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { EmptyState, Spinner } from './components/ui';
import { UsersPage } from './features/admin/UsersPage';
import { CatalogPage, SlaPoliciesPage } from './features/admin/CatalogPages';
import { AuditLogPage, DeliveryLogPage, StrategySettingsPage } from './features/admin/OversightPages';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { OperationsDashboard } from './features/dashboard/OperationsDashboard';
import { QueuePage, WorkloadPage } from './features/dashboard/WorkPages';
import { IncidentDetailPage } from './features/incidents/IncidentDetailPage';
import { IncidentListPage } from './features/incidents/IncidentListPage';
import { ReportIncidentPage } from './features/incidents/ReportIncidentPage';
import { IntelligencePage } from './features/intelligence/IntelligencePage';
import { NotificationsPage } from './features/notifications/NotificationsPage';
import { ProfilePage } from './features/profile/ProfilePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import type { Permission } from './types';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Spinner label="Loading your workspace..." />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

/** Client-side route guard mirroring backend authorization (the backend remains the source of truth). */
function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const { can } = useAuth();
  if (!can(permission)) return <EmptyState title="Access denied">You do not have permission to view this page.</EmptyState>;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<DashboardPage />} />
        <Route path="incidents" element={<IncidentListPage />} />
        <Route path="incidents/new" element={<RequirePermission permission="REPORT_INCIDENT"><ReportIncidentPage /></RequirePermission>} />
        <Route path="incidents/:id" element={<IncidentDetailPage />} />
        <Route path="queue" element={<RequirePermission permission="WORK_INCIDENT"><QueuePage /></RequirePermission>} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="profile" element={<ProfilePage />} />
        <Route path="intelligence" element={<RequirePermission permission="VIEW_ALL_INCIDENTS"><IntelligencePage /></RequirePermission>} />
        <Route path="workload" element={<RequirePermission permission="VIEW_TEAM_WORKLOAD"><WorkloadPage /></RequirePermission>} />
        <Route path="reports" element={<RequirePermission permission="VIEW_REPORTS"><OperationsDashboard title="Reports & analytics" /></RequirePermission>} />
        <Route path="admin/users" element={<RequirePermission permission="MANAGE_USERS"><UsersPage /></RequirePermission>} />
        <Route path="admin/catalog" element={<RequirePermission permission="MANAGE_CATALOG"><CatalogPage /></RequirePermission>} />
        <Route path="admin/sla" element={<RequirePermission permission="MANAGE_SLA"><SlaPoliciesPage /></RequirePermission>} />
        <Route path="admin/settings" element={<RequirePermission permission="CONFIGURE_STRATEGY"><StrategySettingsPage /></RequirePermission>} />
        <Route path="admin/audit" element={<RequirePermission permission="VIEW_AUDIT"><AuditLogPage /></RequirePermission>} />
        <Route path="admin/deliveries" element={<RequirePermission permission="MANAGE_USERS"><DeliveryLogPage /></RequirePermission>} />
        <Route path="*" element={<EmptyState title="Page not found">The page you are looking for does not exist.</EmptyState>} />
      </Route>
    </Routes>
  );
}
