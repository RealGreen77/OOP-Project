import { http } from './client';
import type {
  Analysis, AuditEntry, AuthResponse, Category, CorrelationCluster, Dashboard, IncidentDetail, IncidentSummary,
  Notification, Page, RecommendationResponse, ResolutionSuggestion, Service, SimilarIncident, SlaPolicy, SlaRiskItem,
  StrategySettings, TechnicianWorkload, User, UserRef,
} from '../types';

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);

/** Query-string builder that drops empty values and repeats array params (status=A&status=B). */
export function toQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    if (Array.isArray(v)) v.forEach((item) => sp.append(k, String(item)));
    else sp.append(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export const api = {
  auth: {
    login: (email: string, password: string) => data<AuthResponse>(http.post('/auth/login', { email, password })),
    register: (body: Record<string, unknown>) => data<AuthResponse>(http.post('/auth/register', body)),
    me: () => data<User>(http.get('/auth/me')),
    updateProfile: (body: Record<string, unknown>) => data<User>(http.put('/auth/me', body)),
    changePassword: (body: { currentPassword: string; newPassword: string }) => data(http.post('/auth/me/password', body)),
  },
  catalog: {
    services: () => data<Service[]>(http.get('/catalog/services')),
    categories: () => data<Category[]>(http.get('/catalog/categories')),
  },
  incidents: {
    search: (params: Record<string, unknown>) => data<Page<IncidentSummary>>(http.get(`/incidents${toQuery(params)}`)),
    myQueue: () => data<IncidentSummary[]>(http.get('/incidents/my-queue')),
    get: (id: number) => data<IncidentDetail>(http.get(`/incidents/${id}`)),
    history: (id: number) => data<AuditEntry[]>(http.get(`/incidents/${id}/history`)),
    report: (body: Record<string, unknown>) => data<IncidentDetail>(http.post('/incidents', body)),
    update: (id: number, body: Record<string, unknown>) => data<IncidentDetail>(http.put(`/incidents/${id}`, body)),
    action: (id: number, action: string, body?: Record<string, unknown>) =>
      data<IncidentDetail>(http.post(`/incidents/${id}/${action}`, body ?? {})),
    exportCsv: (params: Record<string, unknown>) =>
      http.get(`/incidents/export${toQuery(params)}`, { responseType: 'blob' }).then((r) => r.data as Blob),
  },
  intelligence: {
    analyze: (body: Record<string, unknown>) => data<Analysis>(http.post('/intelligence/analyze', body)),
    similar: (id: number) => data<SimilarIncident[]>(http.get(`/intelligence/incidents/${id}/similar`)),
    resolutions: (id: number) => data<ResolutionSuggestion[]>(http.get(`/intelligence/incidents/${id}/resolutions`)),
    recommendations: (id: number) => data<RecommendationResponse>(http.get(`/intelligence/incidents/${id}/recommendations`)),
    correlations: () => data<CorrelationCluster[]>(http.get('/intelligence/correlations')),
    slaRisks: () => data<SlaRiskItem[]>(http.get('/intelligence/sla-risks')),
  },
  technicians: {
    list: () => data<TechnicianWorkload[]>(http.get('/technicians')),
    setAvailability: (availability: string) => data<User>(http.patch('/technicians/me/availability', { availability })),
  },
  notifications: {
    inbox: (page = 0, size = 20) => data<Page<Notification>>(http.get(`/notifications?page=${page}&size=${size}`)),
    unread: () => data<{ unread: number }>(http.get('/notifications/unread-count')),
    markRead: (id: number) => data<Notification>(http.post(`/notifications/${id}/read`)),
    markAllRead: () => data(http.post('/notifications/read-all')),
  },
  reports: {
    dashboard: () => data<Dashboard>(http.get('/reports/dashboard')),
  },
  admin: {
    users: (q?: string) => data<User[]>(http.get(`/admin/users${toQuery({ q })}`)),
    createUser: (body: Record<string, unknown>) => data<User>(http.post('/admin/users', body)),
    updateUser: (id: number, body: Record<string, unknown>) => data<User>(http.put(`/admin/users/${id}`, body)),
    setActive: (id: number, value: boolean) => data<User>(http.patch(`/admin/users/${id}/active?value=${value}`)),
    teamLeads: () => data<UserRef[]>(http.get('/admin/team-leads')),
    categories: () => data<Category[]>(http.get('/admin/categories')),
    saveCategory: (body: Record<string, unknown>, id?: number) =>
      data<Category>(id ? http.put(`/admin/categories/${id}`, body) : http.post('/admin/categories', body)),
    services: () => data<Service[]>(http.get('/admin/services')),
    saveService: (body: Record<string, unknown>, id?: number) =>
      data<Service>(id ? http.put(`/admin/services/${id}`, body) : http.post('/admin/services', body)),
    slaPolicies: () => data<SlaPolicy[]>(http.get('/admin/sla-policies')),
    saveSlaPolicy: (body: Record<string, unknown>, id?: number) =>
      data<SlaPolicy>(id ? http.put(`/admin/sla-policies/${id}`, body) : http.post('/admin/sla-policies', body)),
    runSlaScan: () => data<{ scanned: number; warnings: number; breaches: number; escalations: number }>(http.post('/admin/sla/scan')),
    strategies: () => data<StrategySettings>(http.get('/admin/settings/strategies')),
    saveStrategies: (body: { priorityStrategy: string; assignmentStrategy: string; autoAssign: boolean }) =>
      data<StrategySettings>(http.put('/admin/settings/strategies', body)),
    audit: (params: Record<string, unknown>) => data<Page<AuditEntry>>(http.get(`/admin/audit${toQuery(params)}`)),
    deliveryLog: (page = 0, size = 50) => data<Page<Notification>>(http.get(`/admin/notifications?page=${page}&size=${size}`)),
  },
};
