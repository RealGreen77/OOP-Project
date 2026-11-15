import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Radar } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { PriorityBadge } from '../../components/badges';
import { Button, Card, Checkbox, Field, Input, Modal, PageHeader, Select, Spinner, Table, Tabs, Textarea } from '../../components/ui';
import { fmtMinutes, humanize } from '../../lib/format';
import type { Category, Criticality, Priority, Service, SlaPolicy } from '../../types';

// ================================================================== services & categories

export function CatalogPage() {
  const [tab, setTab] = useState<'services' | 'categories'>('services');
  const [editService, setEditService] = useState<Service | 'new' | null>(null);
  const [editCategory, setEditCategory] = useState<Category | 'new' | null>(null);
  const services = useQuery({ queryKey: ['admin', 'services'], queryFn: api.admin.services });
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: api.admin.categories });

  return (
    <>
      <PageHeader title="Services & categories" subtitle="New services and categories take effect immediately - no code changes required."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => (tab === 'services' ? setEditService('new') : setEditCategory('new'))}>
          {tab === 'services' ? 'New service' : 'New category'}</Button>} />
      <Card padded={false}>
        <div className="px-4 pt-2"><Tabs value={tab} onChange={setTab} tabs={[{ value: 'services', label: 'IT services' }, { value: 'categories', label: 'Incident categories' }]} /></div>
        {tab === 'services' ? (!services.data ? <Spinner /> : (
          <Table>
            <thead><tr><th>Service</th><th>Criticality</th><th>Owner</th><th>Default category</th><th>Depends on</th><th>Status</th><th /></tr></thead>
            <tbody>
              {services.data.map((s) => (
                <tr key={s.id}>
                  <td><div className="font-medium">{s.name}</div><div className="font-mono text-xs text-muted">{s.code}</div></td>
                  <td>{humanize(s.criticality)}</td>
                  <td className="text-muted">{s.ownerTeam ?? '—'}</td>
                  <td className="text-muted">{s.defaultCategory?.name ?? '—'}</td>
                  <td className="text-xs text-muted">{s.dependencies.map((d) => d.name).join(', ') || '—'}</td>
                  <td>{s.active ? 'Active' : <span className="text-red-600">Inactive</span>}</td>
                  <td><Button size="sm" variant="ghost" onClick={() => setEditService(s)}>Edit</Button></td>
                </tr>
              ))}
            </tbody>
          </Table>
        )) : (!categories.data ? <Spinner /> : (
          <Table>
            <thead><tr><th>Category</th><th>Keywords (drive auto-categorisation)</th><th>Status</th><th /></tr></thead>
            <tbody>
              {categories.data.map((c) => (
                <tr key={c.id}>
                  <td><div className="font-medium">{c.name}</div><div className="font-mono text-xs text-muted">{c.code}</div></td>
                  <td className="text-xs text-muted">{c.keywords.join(', ')}</td>
                  <td>{c.active ? 'Active' : <span className="text-red-600">Inactive</span>}</td>
                  <td><Button size="sm" variant="ghost" onClick={() => setEditCategory(c)}>Edit</Button></td>
                </tr>
              ))}
            </tbody>
          </Table>
        ))}
      </Card>
      {editService && <ServiceDialog service={editService === 'new' ? null : editService} services={services.data ?? []} categories={categories.data ?? []} onClose={() => setEditService(null)} />}
      {editCategory && <CategoryDialog category={editCategory === 'new' ? null : editCategory} onClose={() => setEditCategory(null)} />}
    </>
  );
}

function ServiceDialog({ service, services, categories, onClose }: { service: Service | null; services: Service[]; categories: Category[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    code: service?.code ?? '', name: service?.name ?? '', description: service?.description ?? '',
    criticality: (service?.criticality ?? 'MEDIUM') as Criticality, ownerTeam: service?.ownerTeam ?? '',
    defaultCategoryId: service?.defaultCategory ? String(service.defaultCategory.id) : '',
    dependencyIds: service?.dependencies.map((d) => d.id) ?? [] as number[], active: service?.active ?? true,
  });
  const save = useMutation({
    mutationFn: () => api.admin.saveService({ ...form, defaultCategoryId: form.defaultCategoryId ? Number(form.defaultCategoryId) : undefined }, service?.id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'services'] }); queryClient.invalidateQueries({ queryKey: ['services'] }); toast.success('Service saved'); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal open wide onClose={onClose} title={service ? `Edit ${service.name}` : 'New IT service'} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Code" required><Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} /></Field>
        <Field label="Name" required><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
        <Field label="Business criticality" hint="Feeds the priority calculation">
          <Select value={form.criticality} onChange={(e) => setForm({ ...form, criticality: e.target.value as Criticality })}>
            {['LOW', 'MEDIUM', 'HIGH', 'MISSION_CRITICAL'].map((c) => <option key={c} value={c}>{humanize(c)}</option>)}
          </Select>
        </Field>
        <Field label="Owner team"><Input value={form.ownerTeam} onChange={(e) => setForm({ ...form, ownerTeam: e.target.value })} /></Field>
        <Field label="Default category">
          <Select value={form.defaultCategoryId} onChange={(e) => setForm({ ...form, defaultCategoryId: e.target.value })}>
            <option value="">None</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        </Field>
        <div className="flex items-end pb-2"><Checkbox label="Active" checked={form.active} onChange={(v) => setForm({ ...form, active: v })} /></div>
      </div>
      <Field label="Description"><Textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
      <div>
        <div className="mb-1 text-sm font-medium">Depends on <span className="text-xs font-normal text-muted">(used for incident correlation; cycles are rejected)</span></div>
        <div className="flex flex-wrap gap-1.5">
          {services.filter((s) => s.id !== service?.id).map((s) => (
            <button key={s.id} type="button"
              onClick={() => setForm((f) => ({ ...f, dependencyIds: f.dependencyIds.includes(s.id) ? f.dependencyIds.filter((x) => x !== s.id) : [...f.dependencyIds, s.id] }))}
              className={form.dependencyIds.includes(s.id) ? 'rounded-md bg-brand-600 px-2 py-1 text-xs text-white' : 'rounded-md border border-base px-2 py-1 text-xs'}>
              {s.name}
            </button>
          ))}
        </div>
      </div>
    </Modal>
  );
}

function CategoryDialog({ category, onClose }: { category: Category | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    code: category?.code ?? '', name: category?.name ?? '', description: category?.description ?? '',
    keywords: category?.keywords.join(', ') ?? '', active: category?.active ?? true,
  });
  const save = useMutation({
    mutationFn: () => api.admin.saveCategory({ ...form, keywords: form.keywords.split(',').map((k) => k.trim()).filter(Boolean) }, category?.id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'categories'] }); queryClient.invalidateQueries({ queryKey: ['categories'] }); toast.success('Category saved'); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal open onClose={onClose} title={category ? `Edit ${category.name}` : 'New category'} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Code" required><Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} /></Field>
        <Field label="Name" required><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
      </div>
      <Field label="Description"><Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
      <Field label="Keywords" hint="Comma-separated words or phrases. Matches in a new incident's text suggest this category.">
        <Textarea rows={3} value={form.keywords} onChange={(e) => setForm({ ...form, keywords: e.target.value })} />
      </Field>
      <Checkbox label="Active" checked={form.active} onChange={(v) => setForm({ ...form, active: v })} />
    </Modal>
  );
}

// ================================================================== SLA policies

export function SlaPoliciesPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<SlaPolicy | 'new' | null>(null);
  const policies = useQuery({ queryKey: ['admin', 'sla'], queryFn: api.admin.slaPolicies });
  const scan = useMutation({
    mutationFn: api.admin.runSlaScan,
    onSuccess: (r) => { queryClient.invalidateQueries(); toast.success(`SLA scan: ${r.scanned} active, ${r.warnings} warnings, ${r.breaches} breaches, ${r.escalations} escalations`); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <>
      <PageHeader title="SLA policies" subtitle="Global defaults per priority, overridable per service. The monitor checks deadlines every minute."
        actions={<>
          <Button variant="secondary" icon={<Radar className="size-4" />} loading={scan.isPending} onClick={() => scan.mutate()}>Run SLA scan now</Button>
          <Button icon={<Plus className="size-4" />} onClick={() => setEditing('new')}>New policy</Button>
        </>} />
      <Card padded={false}>
        {!policies.data ? <Spinner /> : (
          <Table>
            <thead><tr><th>Policy</th><th>Priority</th><th>Scope</th><th>Response</th><th>Resolution</th><th>Warn at</th><th>Status</th><th /></tr></thead>
            <tbody>
              {policies.data.map((p) => (
                <tr key={p.id}>
                  <td className="font-medium">{p.name}</td>
                  <td><PriorityBadge priority={p.priority} /></td>
                  <td className="text-muted">{p.service?.name ?? 'All services (default)'}</td>
                  <td className="tabular-nums">{fmtMinutes(p.responseMinutes)}</td>
                  <td className="tabular-nums">{fmtMinutes(p.resolutionMinutes)}</td>
                  <td className="tabular-nums">{p.warningThresholdPercent}%</td>
                  <td>{p.active ? 'Active' : <span className="text-red-600">Inactive</span>}</td>
                  <td><Button size="sm" variant="ghost" onClick={() => setEditing(p)}>Edit</Button></td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      {editing && <SlaDialog policy={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function SlaDialog({ policy, onClose }: { policy: SlaPolicy | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const [form, setForm] = useState({
    name: policy?.name ?? '', priority: (policy?.priority ?? 'P3') as Priority, responseMinutes: policy?.responseMinutes ?? 60,
    resolutionMinutes: policy?.resolutionMinutes ?? 480, warningThresholdPercent: policy?.warningThresholdPercent ?? 75,
    serviceId: policy?.service ? String(policy.service.id) : '', active: policy?.active ?? true,
  });
  const save = useMutation({
    mutationFn: () => api.admin.saveSlaPolicy({ ...form, serviceId: form.serviceId ? Number(form.serviceId) : undefined }, policy?.id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin', 'sla'] }); toast.success('SLA policy saved'); onClose(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal open onClose={onClose} title={policy ? 'Edit SLA policy' : 'New SLA policy'} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={save.isPending} onClick={() => save.mutate()}>Save</Button></>}>
      <Field label="Name" required><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Priority">
          <Select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}>
            {['P1', 'P2', 'P3', 'P4'].map((p) => <option key={p}>{p}</option>)}
          </Select>
        </Field>
        <Field label="Service (blank = global default)">
          <Select value={form.serviceId} disabled={!!policy} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>
            <option value="">All services</option>
            {services.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
        </Field>
        <Field label="Response target (minutes)"><Input type="number" min={1} value={form.responseMinutes} onChange={(e) => setForm({ ...form, responseMinutes: Number(e.target.value) })} /></Field>
        <Field label="Resolution target (minutes)"><Input type="number" min={1} value={form.resolutionMinutes} onChange={(e) => setForm({ ...form, resolutionMinutes: Number(e.target.value) })} /></Field>
        <Field label="Warning threshold (%)" hint="Warn when this share of the window is used"><Input type="number" min={10} max={95} value={form.warningThresholdPercent} onChange={(e) => setForm({ ...form, warningThresholdPercent: Number(e.target.value) })} /></Field>
        <div className="flex items-end pb-2"><Checkbox label="Active" checked={form.active} onChange={(v) => setForm({ ...form, active: v })} /></div>
      </div>
    </Modal>
  );
}
