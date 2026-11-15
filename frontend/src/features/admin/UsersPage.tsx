import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Search } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { RoleBadge } from '../../components/badges';
import { Button, Card, Field, Input, Modal, PageHeader, Select, Spinner, Table } from '../../components/ui';
import { fmtAgo, ROLE_LABEL } from '../../lib/format';
import type { Availability, Role, User } from '../../types';

const ROLES: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'TEAM_LEAD', 'ADMIN', 'IT_MANAGER'];

export function UsersPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const users = useQuery({ queryKey: ['admin', 'users', q], queryFn: () => api.admin.users(q || undefined) });
  const toggle = useMutation({
    mutationFn: (u: User) => api.admin.setActive(u.id, !u.active),
    onSuccess: (u) => { queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }); toast.success(`${u.fullName} ${u.active ? 'activated' : 'deactivated'}`); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <>
      <PageHeader title="Users & technicians" subtitle="Create any type of user. Technician skills and capacity drive automatic assignment."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing('new')}>New user</Button>} />
      <Card padded={false}>
        <div className="border-b border-base p-4">
          <div className="relative max-w-sm">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
            <Input className="pl-9" placeholder="Search name, e-mail or code..." value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
        </div>
        {!users.data ? <Spinner /> : (
          <Table>
            <thead><tr><th>User</th><th>Role</th><th>Department</th><th>Skills / workload</th><th>Last login</th><th>Status</th><th /></tr></thead>
            <tbody>
              {users.data.map((u) => (
                <tr key={u.id}>
                  <td><div className="font-medium">{u.fullName}</div><div className="text-xs text-muted">{u.userCode} · {u.email}</div></td>
                  <td><RoleBadge role={u.role} /></td>
                  <td className="text-muted">{u.department ?? '—'}</td>
                  <td className="text-xs text-muted">
                    {u.technician ? <>{u.technician.skills.map((s) => s.name).join(', ') || '—'}<div>{u.technician.activeIncidents}/{u.technician.maxConcurrentIncidents} active · {u.technician.availability.toLowerCase()}</div></> : '—'}
                  </td>
                  <td className="text-xs text-muted">{u.lastLoginAt ? fmtAgo(u.lastLoginAt) : 'never'}</td>
                  <td>{u.active ? <span className="text-xs font-medium text-emerald-600">Active</span> : <span className="text-xs font-medium text-red-600">Inactive</span>}</td>
                  <td className="whitespace-nowrap text-right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(u)}>Edit</Button>
                    <Button size="sm" variant="ghost" onClick={() => toggle.mutate(u)}>{u.active ? 'Deactivate' : 'Activate'}</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      {editing && <UserDialog user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function UserDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const leads = useQuery({ queryKey: ['admin', 'leads'], queryFn: api.admin.teamLeads });
  const [form, setForm] = useState({
    role: (user?.role ?? 'EMPLOYEE') as Role, fullName: user?.fullName ?? '', email: user?.email ?? '', password: '',
    department: user?.department ?? '', phone: user?.phone ?? '',
    experienceYears: user?.technician?.experienceYears ?? 1, maxConcurrentIncidents: user?.technician?.maxConcurrentIncidents ?? 5,
    availability: (user?.technician?.availability ?? 'AVAILABLE') as Availability,
    skillCategoryIds: user?.technician?.skills.map((s) => s.id) ?? [] as number[],
    serviceIds: user?.technician?.serviceExpertise.map((s) => s.id) ?? [] as number[],
    teamLeadId: user?.technician?.teamLead?.id ? String(user.technician.teamLead.id) : '',
  });
  const isTech = form.role === 'TECHNICIAN' || form.role === 'TEAM_LEAD';
  const save = useMutation({
    mutationFn: () => {
      const tech = isTech ? {
        experienceYears: form.experienceYears, maxConcurrentIncidents: form.maxConcurrentIncidents,
        skillCategoryIds: form.skillCategoryIds, serviceIds: form.serviceIds,
        teamLeadId: form.teamLeadId ? Number(form.teamLeadId) : undefined,
      } : {};
      return user
        ? api.admin.updateUser(user.id, { fullName: form.fullName, department: form.department, phone: form.phone, availability: isTech ? form.availability : undefined, ...tech })
        : api.admin.createUser({ role: form.role, fullName: form.fullName, email: form.email, password: form.password, department: form.department, phone: form.phone, ...tech });
    },
    onSuccess: (u) => { queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }); queryClient.invalidateQueries({ queryKey: ['technicians'] }); toast.success(`${u.fullName} saved (${u.userCode})`); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const toggleId = (key: 'skillCategoryIds' | 'serviceIds', id: number) =>
    setForm((f) => ({ ...f, [key]: f[key].includes(id) ? f[key].filter((x) => x !== id) : [...f[key], id] }));

  return (
    <Modal open onClose={onClose} wide title={user ? `Edit ${user.fullName}` : 'Create user'} footer={
      <><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>
    }>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Role">
          <Select value={form.role} disabled={!!user} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
          </Select>
        </Field>
        <Field label="Full name" required><Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
        {!user && <Field label="E-mail" required><Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>}
        {!user && <Field label="Initial password" required hint="8+ chars, letter + digit"><Input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></Field>}
        <Field label="Department"><Input value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} /></Field>
        <Field label="Phone"><Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
      </div>
      {isTech && (
        <div className="space-y-3 rounded-lg border border-base p-3">
          <div className="text-sm font-semibold">Technician profile</div>
          <div className="grid gap-3 sm:grid-cols-4">
            <Field label="Experience (yrs)"><Input type="number" min={0} max={50} value={form.experienceYears} onChange={(e) => setForm({ ...form, experienceYears: Number(e.target.value) })} /></Field>
            <Field label="Max concurrent"><Input type="number" min={1} max={50} value={form.maxConcurrentIncidents} onChange={(e) => setForm({ ...form, maxConcurrentIncidents: Number(e.target.value) })} /></Field>
            {user && (
              <Field label="Availability">
                <Select value={form.availability} onChange={(e) => setForm({ ...form, availability: e.target.value as Availability })}>
                  {['AVAILABLE', 'BUSY', 'ON_LEAVE', 'OFFLINE'].map((a) => <option key={a}>{a}</option>)}
                </Select>
              </Field>
            )}
            {form.role === 'TECHNICIAN' && (
              <Field label="Team lead">
                <Select value={form.teamLeadId} onChange={(e) => setForm({ ...form, teamLeadId: e.target.value })}>
                  <option value="">None</option>
                  {leads.data?.map((l) => <option key={l.id} value={l.id}>{l.fullName}</option>)}
                </Select>
              </Field>
            )}
          </div>
          <div>
            <div className="mb-1 text-sm font-medium">Skills (categories)</div>
            <div className="flex flex-wrap gap-1.5">
              {categories.data?.map((c) => (
                <button key={c.id} type="button" onClick={() => toggleId('skillCategoryIds', c.id)}
                  className={form.skillCategoryIds.includes(c.id) ? 'rounded-md bg-brand-600 px-2 py-1 text-xs text-white' : 'rounded-md border border-base px-2 py-1 text-xs'}>
                  {c.name}
                </button>
              ))}
            </div>
          </div>
          <div>
            <div className="mb-1 text-sm font-medium">Service expertise</div>
            <div className="flex flex-wrap gap-1.5">
              {services.data?.map((s) => (
                <button key={s.id} type="button" onClick={() => toggleId('serviceIds', s.id)}
                  className={form.serviceIds.includes(s.id) ? 'rounded-md bg-brand-600 px-2 py-1 text-xs text-white' : 'rounded-md border border-base px-2 py-1 text-xs'}>
                  {s.name}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
