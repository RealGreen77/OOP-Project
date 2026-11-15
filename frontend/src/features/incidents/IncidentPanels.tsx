import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { BrainCircuit, Copy, Gauge, History, UserCheck, Wand2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../../api/endpoints';
import { SlaBadge, StatusBadge } from '../../components/badges';
import { Button, Card, EmptyState, Spinner } from '../../components/ui';
import { fmtCountdown, fmtDateTime, fmtMinutes, fmtTime, humanize } from '../../lib/format';
import type { AuditEntry, IncidentDetail } from '../../types';

/** SLA clock: progress bar, deadlines, breach flags and the predictive risk assessment. */
export function SlaPanel({ incident }: { incident: IncidentDetail }) {
  const sla = incident.sla;
  if (!sla) return <Card title="SLA"><p className="text-sm text-muted">No SLA applied yet - it starts once the incident is prioritised.</p></Card>;
  const active = !incident.resolvedAt;
  const pct = Math.min(100, sla.elapsedPercent);
  const bar = sla.status === 'BREACHED' ? 'bg-red-500' : sla.status === 'AT_RISK' ? 'bg-amber-500' : sla.status === 'PAUSED' ? 'bg-sky-500' : 'bg-emerald-500';
  const resp = sla.paused ? { label: 'paused', overdue: false } : fmtCountdown(sla.responseDeadline);
  const reso = sla.paused ? { label: 'clock stopped', overdue: false } : fmtCountdown(sla.resolutionDeadline);
  const risk = incident.slaRisk;
  return (
    <Card title={<span className="flex items-center gap-2"><Gauge className="size-4" /> Service level</span>} actions={<SlaBadge status={sla.status} />}>
      <div className="space-y-3 text-sm">
        <div>
          <div className="mb-1 flex justify-between text-xs text-muted"><span>{sla.policyName}</span><span>{sla.elapsedPercent}% used</span></div>
          <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700"><div className={clsx('h-full transition-all', bar)} style={{ width: `${pct}%` }} /></div>
        </div>
        <dl className="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
          <dt className="text-muted">Response target</dt><dd className="text-right">{fmtMinutes(sla.responseTargetMinutes)}</dd>
          <dt className="text-muted">Responded</dt>
          <dd className={clsx('text-right', sla.responseBreached && 'font-medium text-red-600')}>
            {sla.respondedAt ? fmtTime(sla.respondedAt) : active ? <span className={resp.overdue ? 'text-red-600' : ''}>due {resp.label}</span> : '—'}
            {sla.responseBreached && ' (late)'}
          </dd>
          <dt className="text-muted">Resolution target</dt><dd className="text-right">{fmtMinutes(sla.resolutionTargetMinutes)}</dd>
          <dt className="text-muted">Resolution deadline</dt>
          <dd className={clsx('text-right', reso.overdue && active && 'font-medium text-red-600')}>
            {fmtDateTime(sla.resolutionDeadline)}{active && <div>{reso.label}</div>}
          </dd>
        </dl>
        {sla.paused && <p className="rounded-md bg-sky-50 px-2 py-1 text-xs text-sky-800 dark:bg-sky-950/50 dark:text-sky-300">Clock paused while waiting for the reporter.</p>}
        {risk && active && (
          <div className={clsx('rounded-lg border p-2.5 text-xs',
            risk.level === 'HIGH' || risk.level === 'BREACHED' ? 'border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/40'
              : risk.level === 'MEDIUM' ? 'border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/40' : 'border-base')}>
            <div className="mb-1 flex items-center justify-between font-medium">
              <span className="flex items-center gap-1"><BrainCircuit className="size-3.5" /> Breach risk: {humanize(risk.level)}</span>
              <span className="tabular-nums">{Math.round(risk.riskScore * 100)}%</span>
            </div>
            <p className="text-muted">Predicted resolution {fmtDateTime(risk.predictedResolution)}</p>
            <ul className="mt-1 list-inside list-disc text-muted">{risk.factors.map((f) => <li key={f}>{f}</li>)}</ul>
          </div>
        )}
      </div>
    </Card>
  );
}

/** Staff-only insights: smart technician recommendations, similar incidents, suggested resolutions. */
export function IntelligencePanel({ incident, canAssign, canResolve, canMerge, onAssign, onUseResolution, onMerge }: {
  incident: IncidentDetail; canAssign: boolean; canResolve: boolean; canMerge: boolean;
  onAssign: (technicianId: number) => void;
  onUseResolution: (summary: string, rootCause?: string) => void;
  onMerge: (parentId: number) => void;
}) {
  const active = !['RESOLVED', 'VERIFIED', 'CLOSED'].includes(incident.status);
  const recs = useQuery({ queryKey: ['intelligence', 'recs', incident.id], queryFn: () => api.intelligence.recommendations(incident.id), enabled: active && canAssign });
  const similar = useQuery({ queryKey: ['intelligence', 'similar', incident.id], queryFn: () => api.intelligence.similar(incident.id), enabled: active });
  const resolutions = useQuery({ queryKey: ['intelligence', 'resolutions', incident.id], queryFn: () => api.intelligence.resolutions(incident.id), enabled: active });
  if (!active) return null;

  return (
    <>
      {canAssign && (
        <Card title={<span className="flex items-center gap-2"><UserCheck className="size-4" /> Recommended technicians</span>}
          actions={recs.data && <span className="text-xs text-muted">{recs.data.strategyName}</span>}>
          {!recs.data ? <Spinner /> : recs.data.recommendations.length === 0 ? <p className="text-sm text-muted">Nobody with free capacity right now.</p> : (
            <ul className="space-y-2">
              {recs.data.recommendations.slice(0, 4).map((r, idx) => (
                <li key={r.technician.id} className="rounded-lg border border-base p-2.5 text-sm">
                  <div className="flex items-center justify-between gap-2">
                    <div className="font-medium">{idx === 0 && '★ '}{r.technician.fullName} <span className="text-xs text-muted">{r.technician.userCode}</span></div>
                    <span className="text-xs tabular-nums text-muted">{Math.round(r.score * 100)}</span>
                  </div>
                  <div className="mt-1 text-xs text-muted">{r.reasons.join(' · ')}</div>
                  <Button size="sm" variant="secondary" className="mt-2" onClick={() => onAssign(r.technician.id)}>Assign</Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      <Card title={<span className="flex items-center gap-2"><Copy className="size-4" /> Similar open incidents</span>}>
        {!similar.data ? <Spinner /> : similar.data.length === 0 ? <p className="text-sm text-muted">No similar open incidents.</p> : (
          <ul className="space-y-2">
            {similar.data.slice(0, 4).map((s) => (
              <li key={s.id} className="rounded-lg border border-base p-2.5 text-sm">
                <div className="flex items-center justify-between">
                  <Link to={`/incidents/${s.id}`} className="font-mono text-xs text-brand-600 hover:underline">{s.number}</Link>
                  <span className="text-xs font-medium text-amber-600">{Math.round(s.score * 100)}%</span>
                </div>
                <div className="font-medium">{s.title}</div>
                <div className="mt-1 flex items-center gap-2 text-xs text-muted"><StatusBadge status={s.status} /> {s.service.name}</div>
                <div className="mt-1 text-xs text-muted">{s.reasons[0]}</div>
                {canMerge && <Button size="sm" variant="secondary" className="mt-2" onClick={() => onMerge(s.id)}>Merge into {s.number}</Button>}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card title={<span className="flex items-center gap-2"><Wand2 className="size-4" /> Suggested resolutions</span>}>
        {!resolutions.data ? <Spinner /> : resolutions.data.length === 0 ? <p className="text-sm text-muted">No similar resolved incidents in history.</p> : (
          <ul className="space-y-2">
            {resolutions.data.map((r) => (
              <li key={r.incidentId} className="rounded-lg border border-base p-2.5 text-sm">
                <div className="flex items-center justify-between">
                  <Link to={`/incidents/${r.incidentId}`} className="font-mono text-xs text-brand-600 hover:underline">{r.incidentNumber}</Link>
                  <span className="text-xs text-muted">{Math.round(r.similarity * 100)}% similar</span>
                </div>
                <div className="text-xs text-muted">{r.title}</div>
                <p className="mt-1">{r.summary}</p>
                {r.rootCause && <p className="mt-0.5 text-xs text-muted">Root cause: {r.rootCause}</p>}
                {canResolve && incident.allowedActions.includes('RESOLVE') && (
                  <Button size="sm" variant="secondary" className="mt-2" onClick={() => onUseResolution(r.summary, r.rootCause)}>Use this resolution</Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}

/** Audit trail rendered as a vertical timeline. */
export function HistoryTimeline({ entries }: { entries?: AuditEntry[] }) {
  if (!entries) return <Spinner />;
  if (entries.length === 0) return <EmptyState icon={<History />} title="No history yet" />;
  return (
    <ol className="relative ml-2 border-l border-base">
      {entries.map((e) => (
        <li key={e.id} className="mb-4 ml-4">
          <span className={clsx('absolute -left-1.5 mt-1.5 size-3 rounded-full border-2 border-[var(--surface)]',
            e.action.includes('BREACH') || e.action === 'ESCALATED' ? 'bg-red-500'
              : e.action.includes('WARNING') || e.action.includes('DUPLICATE') ? 'bg-amber-500'
              : e.action.includes('RESOLVED') || e.action.includes('CLOSED') ? 'bg-emerald-500' : 'bg-brand-500')} />
          <div className="flex flex-wrap items-baseline gap-x-2 text-sm">
            <time className="font-mono text-xs text-muted">{fmtDateTime(e.occurredAt)}</time>
            <span className="font-medium">{e.description}</span>
          </div>
          <div className="text-xs text-muted">
            {e.actorName}{e.previousState || e.newState ? ` · ${e.previousState ?? '∅'} → ${e.newState ?? '∅'}` : ''}
          </div>
        </li>
      ))}
    </ol>
  );
}
