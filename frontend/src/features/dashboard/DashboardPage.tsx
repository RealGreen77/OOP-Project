import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2, ClipboardList, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { Button, Card, PageHeader, Select, Spinner, StatCard } from '../../components/ui';
import type { Availability } from '../../types';
import { IncidentTable } from '../incidents/IncidentTable';
import { OperationsDashboard } from './OperationsDashboard';

/** Home page differs by role: employee self-service, technician work queue, or operations dashboard. */
export function DashboardPage() {
  const { user, is } = useAuth();
  if (!user) return null;
  if (is('ADMIN', 'IT_MANAGER')) return <OperationsDashboard />;
  if (is('TECHNICIAN', 'TEAM_LEAD')) return <TechnicianHome />;
  return <EmployeeHome />;
}

function EmployeeHome() {
  const { user } = useAuth();
  const mine = useQuery({ queryKey: ['incidents', 'mine-home'], queryFn: () => api.incidents.search({ size: 50 }) });
  if (!mine.data) return <Spinner />;
  const all = mine.data.content;
  const open = all.filter((i) => !['RESOLVED', 'VERIFIED', 'CLOSED'].includes(i.status));
  const attention = all.filter((i) => i.status === 'PENDING_INFO' || i.status === 'RESOLVED');
  return (
    <>
      <PageHeader title={`Hello, ${user?.fullName.split(' ')[0]}`} subtitle="Report IT problems and follow their progress."
        actions={<Link to="/incidents/new"><Button icon={<Plus className="size-4" />}>Report an incident</Button></Link>} />
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Open incidents" value={open.length} icon={<ClipboardList className="size-4" />} />
        <StatCard label="Need your action" value={attention.length} tone={attention.length ? 'warning' : 'default'} icon={<AlertTriangle className="size-4" />}
          hint="Information requests and fixes to verify" />
        <StatCard label="Resolved" value={all.length - open.length} tone="success" icon={<CheckCircle2 className="size-4" />} />
      </div>
      {attention.length > 0 && (
        <Card title="Waiting for you" className="mt-6" padded={false}>
          <IncidentTable incidents={attention} showReporter={false} />
        </Card>
      )}
      <Card title="My recent incidents" className="mt-6" padded={false} actions={<Link to="/incidents" className="text-sm text-brand-600 hover:underline">View all</Link>}>
        <IncidentTable incidents={all.slice(0, 10)} showReporter={false} emptyText="You have not reported any incidents yet" />
      </Card>
    </>
  );
}

function TechnicianHome() {
  const { user, refresh, is } = useAuth();
  const queryClient = useQueryClient();
  const queue = useQuery({ queryKey: ['queue'], queryFn: api.incidents.myQueue });
  const unassigned = useQuery({
    queryKey: ['incidents', 'unassigned'],
    queryFn: () => api.incidents.search({ unassigned: true, status: ['NEW', 'CLASSIFIED'], size: 10 }),
  });
  const availability = useMutation({
    mutationFn: (a: Availability) => api.technicians.setAvailability(a),
    onSuccess: async () => { await refresh(); queryClient.invalidateQueries({ queryKey: ['technicians'] }); toast.success('Availability updated'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (!queue.data) return <Spinner />;
  const items = queue.data;
  const atRisk = items.filter((i) => i.slaStatus === 'AT_RISK').length;
  const breached = items.filter((i) => i.slaStatus === 'BREACHED').length;
  const toAck = items.filter((i) => i.status === 'ASSIGNED').length;
  const tech = user?.technician;

  return (
    <>
      <PageHeader
        title="My work"
        subtitle={is('TEAM_LEAD') ? 'Your queue plus team oversight - you are the first escalation point.' : 'Incidents assigned to you, most urgent first.'}
        actions={
          <label className="flex items-center gap-2 text-sm">
            <span className="text-muted">Availability</span>
            <Select className="w-36" value={tech?.availability ?? 'AVAILABLE'} disabled={availability.isPending}
              onChange={(e) => availability.mutate(e.target.value as Availability)}>
              <option value="AVAILABLE">Available</option><option value="BUSY">Busy</option>
              <option value="ON_LEAVE">On leave</option><option value="OFFLINE">Offline</option>
            </Select>
          </label>
        }
      />
      <div className="grid gap-4 sm:grid-cols-4">
        <StatCard label="Assigned to me" value={`${items.length} / ${tech?.maxConcurrentIncidents ?? '—'}`} />
        <StatCard label="To acknowledge" value={toAck} tone={toAck ? 'warning' : 'default'} />
        <StatCard label="SLA at risk" value={atRisk} tone={atRisk ? 'warning' : 'default'} />
        <StatCard label="SLA breached" value={breached} tone={breached ? 'danger' : 'default'} />
      </div>
      <Card title="My queue" className="mt-6" padded={false}>
        <IncidentTable incidents={items} emptyText="Your queue is empty - nice work!" />
      </Card>
      <Card title="Unassigned incidents" className="mt-6" padded={false}
        actions={<Link to="/incidents?unassigned=true" className="text-sm text-brand-600 hover:underline">View all</Link>}>
        <IncidentTable incidents={unassigned.data?.content ?? []} emptyText="No unassigned incidents" />
      </Card>
    </>
  );
}
