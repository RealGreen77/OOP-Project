import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { Button, Card, Checkbox, Input, PageHeader, Select, Spinner, Table } from '../../components/ui';
import { fmtDateTime, humanize } from '../../lib/format';
import type { StrategyOption } from '../../types';

// ================================================================== strategies (runtime Strategy-pattern switch)

export function StrategySettingsPage() {
  const queryClient = useQueryClient();
  const settings = useQuery({ queryKey: ['admin', 'strategies'], queryFn: api.admin.strategies });
  const [form, setForm] = useState({ priorityStrategy: '', assignmentStrategy: '', autoAssign: true });
  useEffect(() => {
    if (settings.data) setForm({ priorityStrategy: settings.data.activePriorityStrategy, assignmentStrategy: settings.data.activeAssignmentStrategy, autoAssign: settings.data.autoAssign });
  }, [settings.data]);
  const save = useMutation({
    mutationFn: () => api.admin.saveStrategies(form),
    onSuccess: (s) => { queryClient.setQueryData(['admin', 'strategies'], s); toast.success('Strategies updated - new incidents use them immediately'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (!settings.data) return <Spinner />;
  return (
    <>
      <PageHeader title="Prioritisation & assignment strategies"
        subtitle="Algorithms are pluggable (Strategy pattern). Switching takes effect immediately without redeploying."
        actions={<Button loading={save.isPending} onClick={() => save.mutate()}>Save changes</Button>} />
      <div className="grid gap-6 lg:grid-cols-2">
        <StrategyGroup title="Priority calculation" options={settings.data.priorityStrategies} value={form.priorityStrategy}
          onChange={(v) => setForm({ ...form, priorityStrategy: v })} />
        <StrategyGroup title="Technician assignment" options={settings.data.assignmentStrategies} value={form.assignmentStrategy}
          onChange={(v) => setForm({ ...form, assignmentStrategy: v })}
          footer={<Checkbox label="Assign automatically when an incident is reported" checked={form.autoAssign} onChange={(v) => setForm({ ...form, autoAssign: v })} />} />
      </div>
    </>
  );
}

function StrategyGroup({ title, options, value, onChange, footer }: {
  title: string; options: StrategyOption[]; value: string; onChange: (v: string) => void; footer?: React.ReactNode;
}) {
  return (
    <Card title={title}>
      <div className="space-y-2">
        {options.map((o) => (
          <label key={o.key} className={clsx('flex cursor-pointer gap-3 rounded-lg border p-3',
            value === o.key ? 'border-brand-500 bg-brand-50 dark:bg-brand-900/30' : 'border-base')}>
            <input type="radio" className="mt-1 accent-brand-600" checked={value === o.key} onChange={() => onChange(o.key)} />
            <div>
              <div className="text-sm font-medium">{o.displayName} <span className="font-mono text-xs text-muted">{o.key}</span></div>
              <div className="text-xs text-muted">{o.description}</div>
            </div>
          </label>
        ))}
      </div>
      {footer && <div className="mt-4">{footer}</div>}
    </Card>
  );
}

// ================================================================== audit log

const ENTITY_TYPES = ['INCIDENT', 'USER', 'CONFIGURATION', 'AUTH'];

export function AuditLogPage() {
  const [filters, setFilters] = useState({ entityType: '', q: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const audit = useQuery({ queryKey: ['admin', 'audit', filters, page], queryFn: () => api.admin.audit({ ...filters, page, size: 50 }), placeholderData: (p) => p });
  return (
    <>
      <PageHeader title="Audit log" subtitle="Append-only record of every significant action: who, what, when, and the state before and after." />
      <Card padded={false}>
        <div className="grid gap-2 border-b border-base p-4 sm:grid-cols-4">
          <div className="relative sm:col-span-2">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
            <Input className="pl-9" placeholder="Search description, actor, reference..." value={filters.q} onChange={(e) => { setPage(0); setFilters({ ...filters, q: e.target.value }); }} />
          </div>
          <Select value={filters.entityType} onChange={(e) => { setPage(0); setFilters({ ...filters, entityType: e.target.value }); }}>
            <option value="">All entity types</option>
            {ENTITY_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </Select>
          <div className="flex gap-2">
            <Input type="date" aria-label="From" value={filters.from} onChange={(e) => setFilters({ ...filters, from: e.target.value })} />
            <Input type="date" aria-label="To" value={filters.to} onChange={(e) => setFilters({ ...filters, to: e.target.value })} />
          </div>
        </div>
        {!audit.data ? <Spinner /> : (
          <>
            <Table>
              <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Change</th><th>Description</th></tr></thead>
              <tbody>
                {audit.data.content.map((a) => (
                  <tr key={a.id}>
                    <td className="whitespace-nowrap font-mono text-xs text-muted">{fmtDateTime(a.occurredAt)}</td>
                    <td className="whitespace-nowrap text-xs">{a.actorName}</td>
                    <td className="whitespace-nowrap text-xs font-medium">{humanize(a.action)}</td>
                    <td className="whitespace-nowrap text-xs">
                      {a.entityType === 'INCIDENT' && a.entityId ? <Link to={`/incidents/${a.entityId}`} className="font-mono text-brand-600 hover:underline">{a.entityRef}</Link>
                        : <span className="text-muted">{humanize(a.entityType)} {a.entityRef ?? ''}</span>}
                    </td>
                    <td className="whitespace-nowrap text-xs text-muted">{a.previousState || a.newState ? `${a.previousState ?? '∅'} → ${a.newState ?? '∅'}` : ''}</td>
                    <td className="text-xs">{a.description}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <div className="flex items-center justify-between border-t border-base px-4 py-3 text-sm text-muted">
              <span>{audit.data.totalElements} entries</span>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
                <Button size="sm" variant="secondary" disabled={page + 1 >= audit.data.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
              </div>
            </div>
          </>
        )}
      </Card>
    </>
  );
}

// ================================================================== notification delivery log

export function DeliveryLogPage() {
  const [page, setPage] = useState(0);
  const log = useQuery({ queryKey: ['admin', 'deliveries', page], queryFn: () => api.admin.deliveryLog(page, 50), refetchInterval: 15000 });
  return (
    <>
      <PageHeader title="Notification delivery log" subtitle="Every notification across in-app, e-mail (SMTP / simulated) and SMS (simulated gateway) channels." />
      <Card padded={false}>
        {!log.data ? <Spinner /> : (
          <>
            <Table>
              <thead><tr><th>When</th><th>Channel</th><th>Recipient</th><th>Destination</th><th>Event</th><th>Title</th><th>Status</th></tr></thead>
              <tbody>
                {log.data.content.map((n) => (
                  <tr key={n.id}>
                    <td className="whitespace-nowrap font-mono text-xs text-muted">{fmtDateTime(n.createdAt)}</td>
                    <td><span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-xs font-medium dark:bg-slate-800">{n.channel.replace('_', '-')}</span></td>
                    <td className="whitespace-nowrap text-xs">{n.recipient?.fullName}</td>
                    <td className="text-xs text-muted">{n.destination ?? 'inbox'}</td>
                    <td className="whitespace-nowrap text-xs">{humanize(n.eventType)}</td>
                    <td className="text-xs">{n.incidentNumber && <span className="mr-1 font-mono text-muted">{n.incidentNumber}</span>}{n.title}</td>
                    <td className={clsx('text-xs font-medium', n.deliveryStatus === 'FAILED' ? 'text-red-600' : n.deliveryStatus === 'SIMULATED' ? 'text-sky-600' : 'text-emerald-600')}
                      title={n.failureReason}>{humanize(n.deliveryStatus)}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <div className="flex justify-end gap-2 border-t border-base p-3">
              <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
              <Button size="sm" variant="secondary" disabled={page + 1 >= log.data.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
            </div>
          </>
        )}
      </Card>
    </>
  );
}
