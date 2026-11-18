import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Star, Wand2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/endpoints';
import { Button, Checkbox, Field, Input, Modal, Select, Textarea } from '../../components/ui';
import type { IncidentDetail, Level } from '../../types';

export type DialogKind =
  | 'resolve' | 'request-info' | 'provide-info' | 'reopen' | 'close' | 'assign' | 'classify' | 'duplicate' | 'feedback' | 'edit'
  | null;

interface Props {
  kind: DialogKind;
  incident: IncidentDetail;
  busy: boolean;
  prefill?: { summary?: string; rootCause?: string; parentId?: number; technicianId?: number };
  onClose: () => void;
  onSubmit: (action: string, body?: Record<string, unknown>) => void;
  onUpdate: (body: Record<string, unknown>) => void;
}

/** All modal dialogs used to drive incident lifecycle actions. */
export function IncidentActionDialogs({ kind, incident, busy, prefill, onClose, onSubmit, onUpdate }: Props) {
  switch (kind) {
    case 'resolve': return <ResolveDialog busy={busy} prefill={prefill} onClose={onClose} onSubmit={(b) => onSubmit('resolve', b)} />;
    case 'request-info':
      return <CommentDialog title="Request information from the reporter" label="Question for the reporter" cta="Send request"
        hint="The SLA clock pauses until the reporter responds." busy={busy} onClose={onClose} onSubmit={(c) => onSubmit('request-info', { comment: c })} />;
    case 'provide-info':
      return <CommentDialog title="Provide the requested information" label="Your answer" cta="Send answer"
        busy={busy} onClose={onClose} onSubmit={(c) => onSubmit('provide-info', { comment: c })} />;
    case 'reopen':
      return <CommentDialog title="Reopen incident" label="Why is the problem not fixed?" cta="Reopen" danger
        busy={busy} onClose={onClose} onSubmit={(c) => onSubmit('reopen', { comment: c })} />;
    case 'close': return <CloseDialog busy={busy} onClose={onClose} onSubmit={(b) => onSubmit('close', b)} />;
    case 'assign': return <AssignDialog incident={incident} busy={busy} preselect={prefill?.technicianId} onClose={onClose} onSubmit={(b) => onSubmit('assign', b)} />;
    case 'classify': return <ClassifyDialog incident={incident} busy={busy} onClose={onClose} onSubmit={(b) => onSubmit('classify', b)} />;
    case 'duplicate': return <DuplicateDialog busy={busy} parentId={prefill?.parentId} onClose={onClose} onSubmit={(b) => onSubmit('duplicate', b)} />;
    case 'feedback': return <FeedbackDialog busy={busy} onClose={onClose} onSubmit={(b) => onSubmit('feedback', b)} />;
    case 'edit': return <EditDialog incident={incident} busy={busy} onClose={onClose} onSubmit={onUpdate} />;
    default: return null;
  }
}

function ResolveDialog({ busy, prefill, onClose, onSubmit }: {
  busy: boolean; prefill?: Props['prefill']; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void;
}) {
  const [summary, setSummary] = useState(prefill?.summary ?? '');
  const [rootCause, setRootCause] = useState(prefill?.rootCause ?? '');
  const [workaround, setWorkaround] = useState(false);
  return (
    <Modal open onClose={onClose} title="Resolve incident" footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant="success" loading={busy} disabled={summary.trim().length < 10} onClick={() => onSubmit({ summary, rootCause, workaround })}>Mark resolved</Button>
      </>
    }>
      {prefill?.summary && <p className="flex items-center gap-1.5 text-xs text-brand-600"><Wand2 className="size-3.5" /> Pre-filled from a similar past incident - adapt it to this case.</p>}
      <Field label="Resolution summary" required hint="Visible to the reporter. Minimum 10 characters.">
        <Textarea rows={4} value={summary} onChange={(e) => setSummary(e.target.value)} />
      </Field>
      <Field label="Root cause"><Input value={rootCause} onChange={(e) => setRootCause(e.target.value)} /></Field>
      <Checkbox label="This is a temporary workaround" checked={workaround} onChange={setWorkaround} />
    </Modal>
  );
}

function CommentDialog({ title, label, cta, hint, danger, busy, onClose, onSubmit }: {
  title: string; label: string; cta: string; hint?: string; danger?: boolean; busy: boolean; onClose: () => void; onSubmit: (c: string) => void;
}) {
  const [comment, setComment] = useState('');
  return (
    <Modal open onClose={onClose} title={title} footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant={danger ? 'danger' : 'primary'} loading={busy} disabled={comment.trim().length < 3} onClick={() => onSubmit(comment)}>{cta}</Button>
      </>
    }>
      <Field label={label} hint={hint}><Textarea rows={4} autoFocus value={comment} onChange={(e) => setComment(e.target.value)} /></Field>
    </Modal>
  );
}

function CloseDialog({ busy, onClose, onSubmit }: { busy: boolean; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void }) {
  const [notes, setNotes] = useState('');
  const [code, setCode] = useState('RESOLVED');
  return (
    <Modal open onClose={onClose} title="Close incident" footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} onClick={() => onSubmit({ notes, closureCode: code })}>Close incident</Button>
      </>
    }>
      <Field label="Closure code">
        <Select value={code} onChange={(e) => setCode(e.target.value)}>
          <option value="RESOLVED">Resolved</option>
          <option value="WORKAROUND_PROVIDED">Workaround provided</option>
          <option value="NO_FAULT_FOUND">No fault found</option>
        </Select>
      </Field>
      <Field label="Closure notes"><Textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
    </Modal>
  );
}

function AssignDialog({ incident, busy, preselect, onClose, onSubmit }: {
  incident: IncidentDetail; busy: boolean; preselect?: number; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void;
}) {
  const technicians = useQuery({ queryKey: ['technicians'], queryFn: api.technicians.list });
  const [technicianId, setTechnicianId] = useState<string>(preselect ? String(preselect) : '');
  return (
    <Modal open onClose={onClose} title={incident.assignedTo ? 'Reassign incident' : 'Assign incident'} wide footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant="secondary" icon={<Wand2 className="size-4" />} loading={busy} onClick={() => onSubmit({})}>Use active strategy</Button>
        <Button loading={busy} disabled={!technicianId} onClick={() => onSubmit({ technicianId: Number(technicianId) })}>Assign selected</Button>
      </>
    }>
      <p className="text-sm text-muted">Pick a technician, or let the configured assignment strategy choose automatically.</p>
      <div className="max-h-80 space-y-1.5 overflow-y-auto">
        {technicians.data?.map((t) => {
          const full = t.activeIncidents >= t.maxConcurrentIncidents || !['AVAILABLE', 'BUSY'].includes(t.availability);
          const isReporter = incident.reporter.id === t.technician.id; // BR-19: never assign your own report
          return (
            <label key={t.technician.id} className={clsx('flex cursor-pointer items-center gap-3 rounded-lg border border-base p-2.5 text-sm',
              String(t.technician.id) === technicianId && 'border-brand-500 bg-brand-50 dark:bg-brand-900/30', (full || isReporter) && 'opacity-60', isReporter && 'cursor-not-allowed')}>
              <input type="radio" name="tech" className="accent-brand-600" checked={String(t.technician.id) === technicianId}
                onChange={() => setTechnicianId(String(t.technician.id))} disabled={incident.assignedTo?.id === t.technician.id || isReporter} />
              <div className="flex-1">
                <div className="font-medium">{t.technician.fullName} <span className="text-xs text-muted">{t.technician.userCode} · {t.experienceYears}y</span>{isReporter && <span className="ml-2 text-xs font-normal text-amber-700 dark:text-amber-400">Reported this incident</span>}</div>
                <div className="text-xs text-muted">{t.skills.join(', ') || 'No skills listed'}</div>
              </div>
              <div className="text-right text-xs">
                <div className={clsx('font-medium', full ? 'text-red-600' : 'text-muted')}>{t.activeIncidents}/{t.maxConcurrentIncidents} active</div>
                <div className="text-muted">{t.availability.replace('_', ' ').toLowerCase()}</div>
              </div>
            </label>
          );
        })}
      </div>
    </Modal>
  );
}

function ClassifyDialog({ incident, busy, onClose, onSubmit }: {
  incident: IncidentDetail; busy: boolean; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void;
}) {
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });
  const [categoryId, setCategoryId] = useState(incident.category ? String(incident.category.id) : '');
  return (
    <Modal open onClose={onClose} title="Classify incident" footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} disabled={!categoryId} onClick={() => onSubmit({ categoryId: Number(categoryId) })}>Save category</Button>
      </>
    }>
      <Field label="Category">
        <Select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">Select...</option>
          {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
      </Field>
    </Modal>
  );
}

function DuplicateDialog({ busy, parentId, onClose, onSubmit }: {
  busy: boolean; parentId?: number; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void;
}) {
  const [number, setNumber] = useState('');
  const [resolved, setResolved] = useState<{ id: number; title: string } | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  useEffect(() => {
    if (parentId) api.incidents.get(parentId).then((p) => { setNumber(p.number); setResolved({ id: p.id, title: p.title }); }).catch(() => {});
  }, [parentId]);
  async function lookup() {
    setLookupError(null);
    setResolved(null);
    try {
      const page = await api.incidents.search({ q: number.trim(), size: 1 });
      const hit = page.content.find((i) => i.number.toLowerCase() === number.trim().toLowerCase());
      if (hit) setResolved({ id: hit.id, title: hit.title }); else setLookupError('No incident with that number');
    } catch {
      setLookupError('Lookup failed');
    }
  }
  return (
    <Modal open onClose={onClose} title="Close as duplicate" footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} disabled={!resolved} onClick={() => onSubmit({ parentIncidentId: resolved!.id })}>Merge into original</Button>
      </>
    }>
      <Field label="Original incident number" hint="This incident will be closed and linked to the original." error={lookupError ?? undefined}>
        <div className="flex gap-2">
          <Input placeholder="INC1024" value={number} onChange={(e) => setNumber(e.target.value)} />
          <Button variant="secondary" onClick={lookup}>Find</Button>
        </div>
      </Field>
      {resolved && <p className="rounded-lg border border-base p-2 text-sm"><span className="font-mono text-xs text-muted">{number}</span> {resolved.title}</p>}
    </Modal>
  );
}

function FeedbackDialog({ busy, onClose, onSubmit }: { busy: boolean; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  return (
    <Modal open onClose={onClose} title="How did we do?" footer={
      <>
        <Button variant="secondary" onClick={onClose}>Later</Button>
        <Button loading={busy} disabled={rating === 0} onClick={() => onSubmit({ rating, comment })}>Submit feedback</Button>
      </>
    }>
      <div className="flex justify-center gap-1">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} aria-label={`${n} stars`} onClick={() => setRating(n)}>
            <Star className={clsx('size-8', n <= rating ? 'fill-amber-400 text-amber-400' : 'text-slate-300')} />
          </button>
        ))}
      </div>
      <Field label="Comment (optional)"><Textarea rows={3} value={comment} onChange={(e) => setComment(e.target.value)} /></Field>
    </Modal>
  );
}

function EditDialog({ incident, busy, onClose, onSubmit }: {
  incident: IncidentDetail; busy: boolean; onClose: () => void; onSubmit: (b: Record<string, unknown>) => void;
}) {
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const [form, setForm] = useState({
    title: incident.title, description: incident.description, serviceId: String(incident.service.id),
    impact: incident.impact, urgency: incident.urgency, affectedUsers: incident.affectedUsers,
  });
  const levels: Level[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  return (
    <Modal open onClose={onClose} title="Edit incident details" wide footer={
      <>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button loading={busy} onClick={() => onSubmit({ ...form, serviceId: Number(form.serviceId) })}>Save changes</Button>
      </>
    }>
      <p className="text-xs text-muted">Changing impact, urgency, users or service re-runs prioritisation and may change the SLA.</p>
      <Field label="Title"><Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></Field>
      <Field label="Description"><Textarea rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
      <div className="grid gap-3 sm:grid-cols-4">
        <Field label="Service">
          <Select value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>
            {services.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </Select>
        </Field>
        <Field label="Impact">
          <Select value={form.impact} onChange={(e) => setForm({ ...form, impact: e.target.value as Level })}>
            {levels.map((l) => <option key={l}>{l}</option>)}
          </Select>
        </Field>
        <Field label="Urgency">
          <Select value={form.urgency} onChange={(e) => setForm({ ...form, urgency: e.target.value as Level })}>
            {levels.map((l) => <option key={l}>{l}</option>)}
          </Select>
        </Field>
        <Field label="Users affected">
          <Input type="number" min={1} value={form.affectedUsers} onChange={(e) => setForm({ ...form, affectedUsers: Number(e.target.value) })} />
        </Field>
      </div>
    </Modal>
  );
}
