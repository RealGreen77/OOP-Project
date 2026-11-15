import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { api } from '../../api/endpoints';
import { Card, PageHeader, Spinner } from '../../components/ui';
import { IncidentTable } from '../incidents/IncidentTable';

export function QueuePage() {
  const queue = useQuery({ queryKey: ['queue'], queryFn: api.incidents.myQueue });
  return (
    <>
      <PageHeader title="My queue" subtitle="Active incidents assigned to you, ordered by priority then age." />
      <Card padded={false}>{queue.data ? <IncidentTable incidents={queue.data} emptyText="Your queue is empty" /> : <Spinner />}</Card>
    </>
  );
}

export function WorkloadPage() {
  const techs = useQuery({ queryKey: ['technicians'], queryFn: api.technicians.list, refetchInterval: 30000 });
  if (!techs.data) return <Spinner />;
  return (
    <>
      <PageHeader title="Team workload" subtitle="Live capacity of every technician - used by the load-based and smart assignment strategies." />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {techs.data.map((t) => {
          const pct = Math.min(100, t.loadPercent);
          return (
            <Card key={t.technician.id}>
              <div className="flex items-start justify-between">
                <div>
                  <div className="font-semibold">{t.technician.fullName}</div>
                  <div className="text-xs text-muted">{t.technician.userCode} · {t.experienceYears} yrs{t.teamLead ? ` · team ${t.teamLead.fullName}` : ''}</div>
                </div>
                <span className={clsx('rounded-full px-2 py-0.5 text-xs font-medium',
                  t.availability === 'AVAILABLE' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
                    : t.availability === 'BUSY' ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                    : 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-400')}>
                  {t.availability.replace('_', ' ').toLowerCase()}
                </span>
              </div>
              <div className="mt-4 flex items-center justify-between text-sm">
                <span className="text-muted">Active incidents</span>
                <span className="font-medium tabular-nums">{t.activeIncidents} / {t.maxConcurrentIncidents}</span>
              </div>
              <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                <div className={clsx('h-full', pct >= 100 ? 'bg-red-500' : pct >= 70 ? 'bg-amber-500' : 'bg-emerald-500')} style={{ width: `${pct}%` }} />
              </div>
              <div className="mt-3 flex flex-wrap gap-1">
                {t.skills.map((s) => <span key={s} className="rounded-md bg-slate-100 px-1.5 py-0.5 text-xs dark:bg-slate-800">{s}</span>)}
              </div>
            </Card>
          );
        })}
      </div>
    </>
  );
}
