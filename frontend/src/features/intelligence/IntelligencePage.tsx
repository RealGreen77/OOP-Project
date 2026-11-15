import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { AlertOctagon, GitBranch, TimerReset } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../../api/endpoints';
import { PriorityBadge, StatusBadge } from '../../components/badges';
import { Card, EmptyState, PageHeader, Spinner, Table } from '../../components/ui';
import { fmtAgo, fmtCountdown, fmtDateTime } from '../../lib/format';
import type { ClusterIncident } from '../../types';

/** Correlation / major-incident detection and predictive SLA risk. */
export function IntelligencePage() {
  const clusters = useQuery({ queryKey: ['intelligence', 'correlations'], queryFn: api.intelligence.correlations, refetchInterval: 60000 });
  const risks = useQuery({ queryKey: ['intelligence', 'risks'], queryFn: api.intelligence.slaRisks, refetchInterval: 60000 });

  return (
    <>
      <PageHeader title="Incident intelligence" subtitle="Automatically correlated incidents and incidents predicted to breach their SLA." />

      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold"><GitBranch className="size-4" /> Correlated incidents</h2>
      {!clusters.data ? <Spinner /> : clusters.data.length === 0 ? (
        <Card><EmptyState title="No correlated incidents">Open incidents that share a service or a common dependency appear here.</EmptyState></Card>
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {clusters.data.map((c) => (
            <Card key={c.probableRoot.id}
              className={clsx(c.majorIncident && 'border-red-300 dark:border-red-900')}
              title={<span className="flex items-center gap-2">
                {c.majorIncident && <span className="pulse-ring inline-flex size-2.5 rounded-full bg-red-500" />}
                Probable root: {c.probableRoot.name}
              </span>}
              actions={c.majorIncident
                ? <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-950 dark:text-red-300"><AlertOctagon className="size-3" /> Major incident</span>
                : <span className="text-xs text-muted">{c.size} incidents</span>}>
              <p className="mb-3 text-sm text-muted">{c.explanation}</p>
              {c.directIncidents.length > 0 && <ClusterList title={`On ${c.probableRoot.name}`} items={c.directIncidents} />}
              {c.dependentIncidents.length > 0 && <ClusterList title="On dependent services" items={c.dependentIncidents} />}
            </Card>
          ))}
        </div>
      )}

      <h2 className="mb-3 mt-8 flex items-center gap-2 text-sm font-semibold"><TimerReset className="size-4" /> Predicted SLA breaches</h2>
      <Card padded={false}>
        {!risks.data ? <Spinner /> : risks.data.length === 0 ? <EmptyState title="No incidents at risk" /> : (
          <Table>
            <thead><tr><th>Incident</th><th>Priority</th><th>Status</th><th>Assignee</th><th>Risk</th><th>Deadline</th><th>Predicted finish</th><th>Why</th></tr></thead>
            <tbody>
              {risks.data.map((r) => {
                const due = r.status === 'PENDING_INFO' ? { label: 'clock stopped', overdue: false } : fmtCountdown(r.deadline);
                return (
                  <tr key={r.incidentId}>
                    <td className="max-w-xs"><Link to={`/incidents/${r.incidentId}`} className="font-mono text-xs text-brand-600 hover:underline">{r.number}</Link><div className="truncate font-medium">{r.title}</div></td>
                    <td><PriorityBadge priority={r.priority} /></td>
                    <td><StatusBadge status={r.status} /></td>
                    <td className={clsx(!r.assignedTo && 'italic text-muted')}>{r.assignedTo?.fullName ?? 'Unassigned'}</td>
                    <td>
                      <span className={clsx('font-semibold tabular-nums', r.level === 'BREACHED' || r.level === 'HIGH' ? 'text-red-600' : 'text-amber-600')}>
                        {Math.round(r.riskScore * 100)}%
                      </span>
                      <div className="text-xs text-muted">{r.level.toLowerCase()}</div>
                    </td>
                    <td className={clsx('whitespace-nowrap text-xs', due.overdue && 'text-red-600')}>{fmtDateTime(r.deadline)}<div>{due.label}</div></td>
                    <td className="whitespace-nowrap text-xs">{fmtDateTime(r.predictedResolution)}</td>
                    <td className="max-w-xs text-xs text-muted">{r.factors.slice(1).join(' · ') || r.factors[0]}</td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>
    </>
  );
}

function ClusterList({ title, items }: { title: string; items: ClusterIncident[] }) {
  return (
    <div className="mb-3">
      <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">{title}</div>
      <ul className="space-y-1">
        {items.map((i) => (
          <li key={i.id} className="flex items-center gap-2 text-sm">
            <PriorityBadge priority={i.priority} />
            <Link to={`/incidents/${i.id}`} className="font-mono text-xs text-brand-600 hover:underline">{i.number}</Link>
            <span className="flex-1 truncate">{i.title}</span>
            <span className="text-xs text-muted">{fmtAgo(i.reportedAt)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
