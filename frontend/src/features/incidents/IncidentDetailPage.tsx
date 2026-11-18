import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  ArrowLeft, Check, CheckCircle2, Copy, Flame, Lock, MessageSquare, Pencil, Play, RotateCcw, Send, Star, Tag, UserPlus, XCircle,
} from 'lucide-react';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { Pill, PriorityBadge, StatusBadge } from '../../components/badges';
import { Button, Card, Checkbox, ErrorBox, Spinner, Tabs, Textarea } from '../../components/ui';
import { LIFECYCLE, fmtAgo, fmtDateTime, humanize } from '../../lib/format';
import type { IncidentDetail, IncidentStatus, Note } from '../../types';
import { type DialogKind, IncidentActionDialogs } from './IncidentActionDialogs';
import { HistoryTimeline, IntelligencePanel, SlaPanel } from './IncidentPanels';

export function IncidentDetailPage() {
  const id = Number(useParams().id);
  const { user, can, is } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [prefill, setPrefill] = useState<{ summary?: string; rootCause?: string; parentId?: number; technicianId?: number }>();
  const [tab, setTab] = useState<'activity' | 'history'>('activity');

  const incidentQuery = useQuery({ queryKey: ['incident', id], queryFn: () => api.incidents.get(id), enabled: Number.isFinite(id) });
  const history = useQuery({ queryKey: ['incident', id, 'history'], queryFn: () => api.incidents.history(id), enabled: tab === 'history' });

  const onSuccess = (updated: IncidentDetail, message: string) => {
    queryClient.setQueryData(['incident', id], updated);
    queryClient.invalidateQueries({ queryKey: ['incident', id, 'history'] });
    queryClient.invalidateQueries({ queryKey: ['incidents'] });
    queryClient.invalidateQueries({ queryKey: ['queue'] });
    queryClient.invalidateQueries({ queryKey: ['intelligence'] });
    setDialog(null);
    setPrefill(undefined);
    toast.success(message);
  };
  const action = useMutation({
    mutationFn: ({ name, body }: { name: string; body?: Record<string, unknown> }) => api.incidents.action(id, name, body),
    onSuccess: (updated, vars) => onSuccess(updated, ACTION_MESSAGES[vars.name] ?? 'Updated'),
    onError: (err) => toast.error(errorMessage(err)),
  });
  const update = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.incidents.update(id, body),
    onSuccess: (updated) => onSuccess(updated, 'Details updated'),
    onError: (err) => toast.error(errorMessage(err)),
  });

  if (incidentQuery.isError) return <ErrorBox message={errorMessage(incidentQuery.error)} />;
  const incident = incidentQuery.data;
  if (!incident || !user) return <Spinner />;

  // ---- what may this user do right now? (backend enforces the same rules)
  const allowed = new Set(incident.allowedActions);
  const isReporter = incident.reporter.id === user.id;
  const isAssignee = incident.assignedTo?.id === user.id;
  const worker = can('WORK_INCIDENT') && (isAssignee || is('TEAM_LEAD'));
  const staff = can('WORK_INCIDENT') || can('ASSIGN_INCIDENT');
  const canAssign = can('ASSIGN_INCIDENT');
  // Nobody may work an incident they reported (BR-19), so no "Take ownership" on your own report.
  const canSelfAssign = can('WORK_INCIDENT') && !incident.assignedTo && allowed.has('ASSIGN') && incident.reporter.id !== user.id;
  const run = (name: string, body?: Record<string, unknown>) => action.mutate({ name, body });

  const buttons: React.ReactNode[] = [];
  if (worker && allowed.has('ACKNOWLEDGE')) buttons.push(<Button key="ack" icon={<Check className="size-4" />} loading={action.isPending} onClick={() => run('acknowledge')}>Acknowledge</Button>);
  if (worker && allowed.has('START_WORK')) buttons.push(<Button key="start" icon={<Play className="size-4" />} loading={action.isPending} onClick={() => run('start')}>Start investigation</Button>);
  if (worker && allowed.has('RESOLVE')) buttons.push(<Button key="resolve" variant="success" icon={<CheckCircle2 className="size-4" />} onClick={() => setDialog('resolve')}>Resolve</Button>);
  if (worker && allowed.has('REQUEST_INFO')) buttons.push(<Button key="ask" variant="secondary" icon={<MessageSquare className="size-4" />} onClick={() => setDialog('request-info')}>Request info</Button>);
  if (isReporter && allowed.has('PROVIDE_INFO')) buttons.push(<Button key="provide" icon={<Send className="size-4" />} onClick={() => setDialog('provide-info')}>Provide information</Button>);
  if ((isReporter || is('ADMIN', 'IT_MANAGER')) && allowed.has('VERIFY')) buttons.push(<Button key="verify" variant="success" icon={<CheckCircle2 className="size-4" />} loading={action.isPending} onClick={() => run('verify')}>Confirm fix</Button>);
  if ((isReporter || is('ADMIN', 'IT_MANAGER')) && allowed.has('REOPEN')) buttons.push(<Button key="reopen" variant="danger" icon={<RotateCcw className="size-4" />} onClick={() => setDialog('reopen')}>Not fixed - reopen</Button>);
  if ((can('CLOSE_INCIDENT') || isAssignee || is('TEAM_LEAD')) && allowed.has('CLOSE')) buttons.push(<Button key="close" icon={<XCircle className="size-4" />} onClick={() => setDialog('close')}>Close</Button>);
  if (canSelfAssign && !canAssign) buttons.push(<Button key="take" icon={<UserPlus className="size-4" />} loading={action.isPending} onClick={() => run('assign', { technicianId: user.id })}>Take ownership</Button>);
  if (canAssign && (allowed.has('ASSIGN') || allowed.has('REASSIGN'))) buttons.push(<Button key="assign" variant="secondary" icon={<UserPlus className="size-4" />} onClick={() => setDialog('assign')}>{incident.assignedTo ? 'Reassign' : 'Assign'}</Button>);
  if (staff && allowed.has('CLASSIFY')) buttons.push(<Button key="classify" variant="secondary" icon={<Tag className="size-4" />} onClick={() => setDialog('classify')}>Classify</Button>);
  if (staff && allowed.has('MARK_DUPLICATE')) buttons.push(<Button key="dup" variant="secondary" icon={<Copy className="size-4" />} onClick={() => setDialog('duplicate')}>Mark duplicate</Button>);
  if ((isReporter || staff) && allowed.has('UPDATE_DETAILS')) buttons.push(<Button key="edit" variant="ghost" icon={<Pencil className="size-4" />} onClick={() => setDialog('edit')}>Edit</Button>);
  const canFeedback = isReporter && allowed.has('FEEDBACK') && !incident.feedback && incident.closureCode !== 'DUPLICATE';

  return (
    <>
      <button onClick={() => navigate(-1)} className="mb-3 inline-flex items-center gap-1 text-sm text-muted hover:text-[var(--text)]">
        <ArrowLeft className="size-4" /> Back
      </button>

      {/* ---- header */}
      <div className="mb-4 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-mono text-muted">{incident.number}</span>
            <StatusBadge status={incident.status} />
            <PriorityBadge priority={incident.priority} showLabel />
            {incident.escalationLevel > 0 && <Pill tone="red"><Flame className="size-3" /> Escalated L{incident.escalationLevel}</Pill>}
            {incident.reopenCount > 0 && <Pill tone="amber">Reopened ×{incident.reopenCount}</Pill>}
            {incident.duplicateOf && <Pill tone="slate">Duplicate of <Link className="underline" to={`/incidents/${incident.duplicateOf.id}`}>{incident.duplicateOf.code}</Link></Pill>}
            {!incident.duplicateOf && incident.suspectedDuplicateOf && staff && (
              <Pill tone="amber"><Copy className="size-3" /> Possible duplicate of <Link className="underline" to={`/incidents/${incident.suspectedDuplicateOf.id}`}>{incident.suspectedDuplicateOf.code}</Link>
                {incident.suspectedDuplicateScore && ` (${Math.round(incident.suspectedDuplicateScore * 100)}%)`}</Pill>
            )}
          </div>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{incident.title}</h1>
          <p className="mt-1 text-sm text-muted">
            Reported by {incident.reporter.fullName} {fmtAgo(incident.reportedAt)} · {incident.service.name}
            {incident.category && ` · ${incident.category.name}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {buttons}
          {canFeedback && <Button icon={<Star className="size-4" />} variant="secondary" onClick={() => setDialog('feedback')}>Rate resolution</Button>}
        </div>
      </div>

      <LifecycleStepper status={incident.status} />

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          {incident.status === 'PENDING_INFO' && isReporter && (
            <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
              The technician needs more information from you. Use <b>Provide information</b> to reply - the SLA clock is paused until you do.
            </div>
          )}
          {incident.status === 'RESOLVED' && isReporter && (
            <div className="rounded-xl border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200">
              This incident was marked resolved. Please confirm the fix works, or reopen it if the problem persists.
            </div>
          )}

          <Card title="Description">
            <p className="whitespace-pre-wrap text-sm leading-relaxed">{incident.description}</p>
          </Card>

          {incident.resolution && (
            <Card title="Resolution">
              <p className="text-sm">{incident.resolution.summary}</p>
              {incident.resolution.rootCause && <p className="mt-2 text-sm text-muted"><b>Root cause:</b> {incident.resolution.rootCause}</p>}
              {incident.resolution.workaround && <Pill tone="amber">Workaround</Pill>}
              {incident.feedback && (
                <div className="mt-3 flex items-center gap-1 text-sm">
                  {[1, 2, 3, 4, 5].map((n) => <Star key={n} className={clsx('size-4', n <= incident.feedback!.rating ? 'fill-amber-400 text-amber-400' : 'text-slate-300')} />)}
                  {incident.feedback.comment && <span className="ml-2 text-muted">"{incident.feedback.comment}"</span>}
                </div>
              )}
            </Card>
          )}

          <Card padded={false}>
            <div className="px-4 pt-2">
              <Tabs value={tab} onChange={setTab} tabs={[{ value: 'activity', label: `Activity (${incident.notes.length})` }, { value: 'history', label: 'Audit history' }]} />
            </div>
            <div className="p-4">
              {tab === 'activity' ? (
                <>
                  <NoteList notes={incident.notes} />
                  {allowed.has('ADD_NOTE') && (isReporter || staff || can('VIEW_ALL_INCIDENTS')) && (
                    <NoteComposer staff={staff} busy={action.isPending} onSubmit={(content, internal) => run('notes', { content, internal })} />
                  )}
                </>
              ) : <HistoryTimeline entries={history.data} />}
            </div>
          </Card>
        </div>

        <div className="space-y-6">
          <DetailsCard incident={incident} />
          <SlaPanel incident={incident} />
          {staff && (
            <IntelligencePanel
              incident={incident}
              canAssign={canAssign}
              canResolve={worker}
              canMerge={staff && allowed.has('MARK_DUPLICATE')}
              onAssign={(technicianId) => run('assign', { technicianId })}
              onUseResolution={(summary, rootCause) => { setPrefill({ summary, rootCause }); setDialog('resolve'); }}
              onMerge={(parentId) => { setPrefill({ parentId }); setDialog('duplicate'); }}
            />
          )}
        </div>
      </div>

      <IncidentActionDialogs
        kind={dialog}
        incident={incident}
        busy={action.isPending || update.isPending}
        prefill={prefill}
        onClose={() => { setDialog(null); setPrefill(undefined); }}
        onSubmit={(name, body) => run(name, body)}
        onUpdate={(body) => update.mutate(body)}
      />
    </>
  );
}

const ACTION_MESSAGES: Record<string, string> = {
  acknowledge: 'Incident acknowledged', start: 'Investigation started', resolve: 'Incident resolved',
  'request-info': 'Information requested - SLA paused', 'provide-info': 'Information sent', verify: 'Resolution confirmed',
  reopen: 'Incident reopened', close: 'Incident closed', assign: 'Incident assigned', classify: 'Category updated',
  duplicate: 'Closed as duplicate', feedback: 'Thanks for your feedback!', notes: 'Note added',
};

function LifecycleStepper({ status }: { status: IncidentStatus }) {
  const effective: IncidentStatus = status === 'PENDING_INFO' ? 'IN_PROGRESS' : status;
  const current = LIFECYCLE.indexOf(effective);
  return (
    <ol className="surface flex overflow-x-auto rounded-xl border border-base p-2 text-xs">
      {LIFECYCLE.map((s, i) => (
        <li key={s} className="flex min-w-24 flex-1 items-center gap-2 px-2 py-1.5">
          <span className={clsx('grid size-5 shrink-0 place-items-center rounded-full text-[10px] font-semibold',
            i < current ? 'bg-brand-600 text-white' : i === current ? 'bg-brand-600 text-white ring-4 ring-brand-500/20' : 'bg-slate-200 text-slate-500 dark:bg-slate-700')}>
            {i < current ? <Check className="size-3" /> : i + 1}
          </span>
          <span className={clsx('whitespace-nowrap', i === current ? 'font-semibold' : 'text-muted')}>
            {humanize(s)}{i === current && status === 'PENDING_INFO' && ' (awaiting info)'}
          </span>
        </li>
      ))}
    </ol>
  );
}

function DetailsCard({ incident }: { incident: IncidentDetail }) {
  const rows: [string, React.ReactNode][] = [
    ['Service', <>{incident.service.name} <span className="text-xs text-muted">({humanize(incident.serviceCriticality)})</span></>],
    ['Category', incident.category?.name ?? <span className="italic text-muted">Unclassified</span>],
    ['Impact / Urgency', `${humanize(incident.impact)} / ${humanize(incident.urgency)}`],
    ['Users affected', incident.affectedUsers],
    ['Priority score', incident.priorityScore ?? '—'],
    ['Reporter', `${incident.reporter.fullName} (${incident.reporter.userCode})`],
    ['Assigned to', incident.assignedTo ? `${incident.assignedTo.fullName} (${incident.assignedTo.userCode})` : <span className="italic text-muted">Unassigned</span>],
    ['Reported', fmtDateTime(incident.reportedAt)],
    ['Acknowledged', fmtDateTime(incident.acknowledgedAt)],
    ['Resolved', fmtDateTime(incident.resolvedAt)],
    ['Closed', fmtDateTime(incident.closedAt)],
  ];
  return (
    <Card title="Details">
      <dl className="space-y-2 text-sm">
        {rows.map(([k, v]) => (
          <div key={k} className="flex justify-between gap-3"><dt className="text-muted">{k}</dt><dd className="text-right">{v}</dd></div>
        ))}
      </dl>
      {incident.priorityRationale && <p className="mt-3 rounded-md bg-slate-50 p-2 text-xs text-muted dark:bg-slate-800/50">{incident.priorityRationale}</p>}
    </Card>
  );
}

const NOTE_LABEL: Record<Note['type'], string> = {
  COMMENT: 'Comment', INVESTIGATION: 'Investigation', INFO_REQUEST: 'Information requested',
  INFO_RESPONSE: 'Reporter response', RESOLUTION: 'Resolution', SYSTEM: 'System',
};

function NoteList({ notes }: { notes: Note[] }) {
  if (notes.length === 0) return <p className="py-4 text-center text-sm text-muted">No activity yet.</p>;
  return (
    <ul className="space-y-3">
      {notes.map((n) => (
        <li key={n.id} className={clsx('rounded-lg border p-3 text-sm',
          n.internal ? 'border-amber-200 bg-amber-50/60 dark:border-amber-900 dark:bg-amber-950/30' : 'border-base')}>
          <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-muted">
            <span className="font-medium text-[var(--text)]">{n.author?.fullName ?? 'System'}</span>
            <span>{NOTE_LABEL[n.type]}</span>
            {n.internal && <span className="inline-flex items-center gap-1 text-amber-700 dark:text-amber-400"><Lock className="size-3" /> Internal</span>}
            <span className="ml-auto">{fmtDateTime(n.createdAt)}</span>
          </div>
          <p className="whitespace-pre-wrap">{n.content}</p>
        </li>
      ))}
    </ul>
  );
}

function NoteComposer({ staff, busy, onSubmit }: { staff: boolean; busy: boolean; onSubmit: (content: string, internal: boolean) => void }) {
  const [content, setContent] = useState('');
  const [internal, setInternal] = useState(false);
  function submit(e: FormEvent) {
    e.preventDefault();
    if (!content.trim()) return;
    onSubmit(content.trim(), internal);
    setContent('');
  }
  return (
    <form onSubmit={submit} className="mt-4 space-y-2">
      <Textarea rows={3} placeholder={staff ? 'Add an investigation note or a reply to the reporter...' : 'Add a comment for the support team...'}
        value={content} onChange={(e) => setContent(e.target.value)} />
      <div className="flex items-center justify-between">
        {staff ? <Checkbox label="Internal note (hidden from reporter)" checked={internal} onChange={setInternal} /> : <span />}
        <Button type="submit" size="sm" loading={busy} disabled={!content.trim()}>Add note</Button>
      </div>
    </form>
  );
}
