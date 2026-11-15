import { useQuery } from '@tanstack/react-query';
import { Download, Filter, Plus, RotateCcw, Search } from 'lucide-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { Button, Card, ErrorBox, Input, PageHeader, Select, Spinner } from '../../components/ui';
import { downloadBlob, humanize } from '../../lib/format';
import type { IncidentStatus, Priority, SlaStatus } from '../../types';
import { IncidentTable } from './IncidentTable';

const STATUSES: IncidentStatus[] = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'PENDING_INFO', 'RESOLVED', 'VERIFIED', 'CLOSED'];
const PRIORITIES: Priority[] = ['P1', 'P2', 'P3', 'P4'];
const SLA: SlaStatus[] = ['ON_TRACK', 'AT_RISK', 'PAUSED', 'BREACHED', 'MET'];
const FILTER_KEYS = ['q', 'status', 'priority', 'categoryId', 'serviceId', 'technicianId', 'slaStatus', 'from', 'to', 'escalated', 'unassigned'];

export function IncidentListPage() {
  const { can } = useAuth();
  const staff = can('VIEW_ALL_INCIDENTS');
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const [showFilters, setShowFilters] = useState(staff);
  const page = Number(params.get('page') ?? 0);

  const filters: Record<string, unknown> = {};
  FILTER_KEYS.forEach((k) => {
    const v = params.get(k);
    if (v) filters[k] = v;
  });
  const query = useQuery({
    queryKey: ['incidents', 'search', filters, page],
    queryFn: () => api.incidents.search({ ...filters, page, size: 20, sort: 'reportedAt,desc' }),
    placeholderData: (prev) => prev,
  });
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });
  const technicians = useQuery({ queryKey: ['technicians'], queryFn: api.technicians.list, enabled: staff });

  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    next.delete('page');
    setParams(next);
  }

  function goToPage(n: number) {
    const next = new URLSearchParams(params);
    if (n > 0) next.set('page', String(n)); else next.delete('page');
    setParams(next);
  }

  async function exportCsv() {
    try {
      downloadBlob(await api.incidents.exportCsv(filters), `incidents-${new Date().toISOString().slice(0, 10)}.csv`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  const activeFilterCount = FILTER_KEYS.filter((k) => k !== 'q' && params.get(k)).length;

  return (
    <>
      <PageHeader
        title={staff ? 'Incidents' : 'My incidents'}
        subtitle={staff ? 'Search and filter every incident in the organisation.' : 'Everything you have reported and its current status.'}
        actions={
          <>
            {staff && <Button variant="secondary" icon={<Download className="size-4" />} onClick={exportCsv}>Export CSV</Button>}
            {can('REPORT_INCIDENT') && <Link to="/incidents/new"><Button icon={<Plus className="size-4" />}>Report incident</Button></Link>}
          </>
        }
      />
      <Card padded={false}>
        <div className="space-y-3 border-b border-base p-4">
          <div className="flex flex-wrap gap-2">
            <form className="relative min-w-60 flex-1" onSubmit={(e) => { e.preventDefault(); update('q', text.trim()); }}>
              <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
              <Input className="pl-9" placeholder="Search by number (INC1024), title or description..." value={text} onChange={(e) => setText(e.target.value)} />
            </form>
            <Button variant="secondary" icon={<Filter className="size-4" />} onClick={() => setShowFilters((s) => !s)}>
              Filters{activeFilterCount ? ` (${activeFilterCount})` : ''}
            </Button>
            {(activeFilterCount > 0 || params.get('q')) && (
              <Button variant="ghost" icon={<RotateCcw className="size-4" />} onClick={() => { setText(''); setParams(new URLSearchParams()); }}>Reset</Button>
            )}
          </div>
          {showFilters && (
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
              <Select aria-label="Status" value={params.get('status') ?? ''} onChange={(e) => update('status', e.target.value)}>
                <option value="">Any status</option>
                {STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
              </Select>
              <Select aria-label="Priority" value={params.get('priority') ?? ''} onChange={(e) => update('priority', e.target.value)}>
                <option value="">Any priority</option>
                {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
              </Select>
              <Select aria-label="Category" value={params.get('categoryId') ?? ''} onChange={(e) => update('categoryId', e.target.value)}>
                <option value="">Any category</option>
                {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </Select>
              <Select aria-label="Service" value={params.get('serviceId') ?? ''} onChange={(e) => update('serviceId', e.target.value)}>
                <option value="">Any service</option>
                {services.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </Select>
              <Select aria-label="SLA status" value={params.get('slaStatus') ?? ''} onChange={(e) => update('slaStatus', e.target.value)}>
                <option value="">Any SLA status</option>
                {SLA.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
              </Select>
              {staff && (
                <Select aria-label="Technician" value={params.get('technicianId') ?? ''} onChange={(e) => update('technicianId', e.target.value)}>
                  <option value="">Any technician</option>
                  {technicians.data?.map((t) => <option key={t.technician.id} value={t.technician.id}>{t.technician.fullName}</option>)}
                </Select>
              )}
              <Input aria-label="Reported from" type="date" value={params.get('from') ?? ''} onChange={(e) => update('from', e.target.value)} />
              <Input aria-label="Reported to" type="date" value={params.get('to') ?? ''} onChange={(e) => update('to', e.target.value)} />
              {staff && (
                <>
                  <Select aria-label="Escalation" value={params.get('escalated') ?? ''} onChange={(e) => update('escalated', e.target.value)}>
                    <option value="">Escalated or not</option>
                    <option value="true">Escalated only</option>
                  </Select>
                  <Select aria-label="Assignment" value={params.get('unassigned') ?? ''} onChange={(e) => update('unassigned', e.target.value)}>
                    <option value="">Assigned or not</option>
                    <option value="true">Unassigned only</option>
                  </Select>
                </>
              )}
            </div>
          )}
        </div>
        {query.isError ? <div className="p-4"><ErrorBox message={errorMessage(query.error)} /></div>
          : !query.data ? <Spinner /> : (
            <>
              <IncidentTable incidents={query.data.content} showReporter={staff} />
              <div className="flex items-center justify-between border-t border-base px-4 py-3 text-sm text-muted">
                <span>{query.data.totalElements} incident(s)</span>
                <div className="flex items-center gap-2">
                  <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => goToPage(page - 1)}>Previous</Button>
                  <span>Page {query.data.totalPages === 0 ? 0 : page + 1} of {query.data.totalPages}</span>
                  <Button size="sm" variant="secondary" disabled={page + 1 >= query.data.totalPages} onClick={() => goToPage(page + 1)}>Next</Button>
                </div>
              </div>
            </>
          )}
      </Card>
    </>
  );
}
