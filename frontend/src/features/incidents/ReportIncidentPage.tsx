import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertTriangle, Lightbulb, Sparkles } from 'lucide-react';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { errorMessage, problemOf } from '../../api/client';
import { api } from '../../api/endpoints';
import { PriorityBadge, StatusBadge } from '../../components/badges';
import { Button, Card, ErrorBox, Field, Input, PageHeader, Select, Textarea } from '../../components/ui';
import { fmtAgo, fmtMinutes } from '../../lib/format';
import type { Analysis, Level } from '../../types';

const LEVELS: { value: Level; label: string }[] = [
  { value: 'LOW', label: 'Low' }, { value: 'MEDIUM', label: 'Medium' }, { value: 'HIGH', label: 'High' }, { value: 'CRITICAL', label: 'Critical' },
];
const IMPACT_HINT: Record<Level, string> = {
  LOW: 'Only me, a workaround exists', MEDIUM: 'My team or department', HIGH: 'Several departments', CRITICAL: 'Whole organisation / customers',
};
const URGENCY_HINT: Record<Level, string> = {
  LOW: 'Can wait', MEDIUM: 'Needed today', HIGH: 'My work is blocked', CRITICAL: 'Immediate business loss',
};

function useDebounced<T>(value: T, delay = 600): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return v;
}

export function ReportIncidentPage() {
  const navigate = useNavigate();
  const services = useQuery({ queryKey: ['services'], queryFn: api.catalog.services });
  const categories = useQuery({ queryKey: ['categories'], queryFn: api.catalog.categories });
  const [form, setForm] = useState({
    title: '', description: '', serviceId: '', categoryId: '', impact: 'MEDIUM' as Level, urgency: 'MEDIUM' as Level, affectedUsers: 1,
  });
  const [duplicateOf, setDuplicateOf] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  // ---- live intelligent analysis (debounced, nothing is saved)
  const draft = useDebounced({ title: form.title, description: form.description, serviceId: form.serviceId,
    impact: form.impact, urgency: form.urgency, affectedUsers: form.affectedUsers });
  const canAnalyze = !!draft.serviceId && (draft.title.length >= 5 || draft.description.length >= 15);
  const analysis = useQuery<Analysis>({
    queryKey: ['analyze', draft],
    queryFn: () => api.intelligence.analyze({ ...draft, serviceId: Number(draft.serviceId) }),
    enabled: canAnalyze,
    placeholderData: (prev) => prev,
  });

  const submit = useMutation({
    mutationFn: (force: boolean) => api.incidents.report({
      ...form,
      serviceId: Number(form.serviceId),
      categoryId: form.categoryId ? Number(form.categoryId) : undefined,
      affectedUsers: Number(form.affectedUsers) || 1,
      force,
    }),
    onSuccess: (incident) => {
      toast.success(`${incident.number} logged`, {
        description: incident.assignedTo
          ? `Priority ${incident.priority} · assigned to ${incident.assignedTo.fullName}`
          : `Priority ${incident.priority ?? 'pending'} · awaiting assignment`,
      });
      navigate(`/incidents/${incident.id}`);
    },
    onError: (err) => {
      const problem = problemOf(err);
      if (problem?.errorCode === 'DUPLICATE_INCIDENT') setDuplicateOf((problem.existingIncidents as string[]) ?? []);
      else setError(errorMessage(err));
    },
  });

  const selectedService = useMemo(
    () => services.data?.find((s) => String(s.id) === form.serviceId),
    [services.data, form.serviceId],
  );
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm((f) => ({ ...f, [k]: v }));

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setDuplicateOf(null);
    submit.mutate(false);
  }

  const a = analysis.data;
  return (
    <>
      <PageHeader title="Report an incident" subtitle="Describe the problem - the system classifies, prioritises and routes it automatically." />
      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <form onSubmit={onSubmit} className="space-y-5">
          <Card>
            <div className="space-y-4">
              {error && <ErrorBox message={error} />}
              {duplicateOf && (
                <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                  <div className="flex items-center gap-2 font-medium"><AlertTriangle className="size-4" /> You reported something very similar recently ({duplicateOf.join(', ')}).</div>
                  <p className="mt-1">Add details to the existing incident instead, or submit anyway if this is a different problem.</p>
                  <Button type="button" size="sm" variant="secondary" className="mt-2" onClick={() => submit.mutate(true)} loading={submit.isPending}>
                    Submit anyway
                  </Button>
                </div>
              )}
              <Field label="Affected service" required>
                <Select required value={form.serviceId} onChange={(e) => set('serviceId', e.target.value)}>
                  <option value="">Select the service that is affected...</option>
                  {services.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </Select>
              </Field>
              {selectedService?.description && <p className="-mt-2 text-xs text-muted">{selectedService.description}</p>}
              <Field label="Title" required hint="A short summary, e.g. 'Payment application unavailable'">
                <Input required minLength={5} maxLength={200} value={form.title} onChange={(e) => set('title', e.target.value)} />
              </Field>
              <Field label="Description" required hint="What happened, when it started, what you already tried, any error messages.">
                <Textarea required minLength={10} maxLength={4000} rows={5} value={form.description} onChange={(e) => set('description', e.target.value)} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-3">
                <Field label="Impact" hint={IMPACT_HINT[form.impact]}>
                  <Select value={form.impact} onChange={(e) => set('impact', e.target.value as Level)}>
                    {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
                  </Select>
                </Field>
                <Field label="Urgency" hint={URGENCY_HINT[form.urgency]}>
                  <Select value={form.urgency} onChange={(e) => set('urgency', e.target.value as Level)}>
                    {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
                  </Select>
                </Field>
                <Field label="Users affected">
                  <Input type="number" min={1} max={1000000} value={form.affectedUsers} onChange={(e) => set('affectedUsers', Number(e.target.value))} />
                </Field>
              </div>
              <Field label="Category (optional)" hint="Leave empty to let the system categorise it automatically.">
                <Select value={form.categoryId} onChange={(e) => set('categoryId', e.target.value)}>
                  <option value="">Automatic</option>
                  {categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </Select>
              </Field>
            </div>
          </Card>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
            <Button type="submit" loading={submit.isPending} disabled={!form.serviceId}>Submit incident</Button>
          </div>
        </form>

        {/* ---- intelligence side panel */}
        <aside className="space-y-4">
          <Card title={<span className="flex items-center gap-2"><Sparkles className="size-4 text-brand-500" /> Live analysis</span>}>
            {!canAnalyze ? (
              <p className="text-sm text-muted">Choose a service and start describing the problem to see the predicted priority, suggested category and similar open incidents.</p>
            ) : !a ? (
              <p className="text-sm text-muted">Analysing...</p>
            ) : (
              <div className="space-y-4 text-sm">
                {a.predictedPriority && (
                  <div>
                    <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">Predicted priority</div>
                    <div className="flex items-center gap-2"><PriorityBadge priority={a.predictedPriority.priority} showLabel /></div>
                    {a.predictedPriority.resolutionMinutes && (
                      <p className="mt-1 text-xs text-muted">
                        SLA: response within {fmtMinutes(a.predictedPriority.responseMinutes)}, resolution within {fmtMinutes(a.predictedPriority.resolutionMinutes)}
                      </p>
                    )}
                    <p className="mt-1 text-xs text-muted">{a.predictedPriority.rationale}</p>
                  </div>
                )}
                <div>
                  <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">Suggested category</div>
                  {a.categorySuggestions.length === 0 ? <p className="text-xs text-muted">No strong match yet.</p> : (
                    <ul className="space-y-1.5">
                      {a.categorySuggestions.map((s) => (
                        <li key={s.category.id} className="flex items-center justify-between gap-2">
                          <button type="button" onClick={() => set('categoryId', String(s.category.id))}
                            className="rounded-md border border-base px-2 py-0.5 text-xs font-medium hover:border-brand-500">
                            {s.category.name}
                          </button>
                          <div className="flex flex-1 items-center gap-2">
                            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                              <div className="h-full bg-brand-500" style={{ width: `${Math.round(s.confidence * 100)}%` }} />
                            </div>
                            <span className="w-9 text-right text-xs tabular-nums text-muted">{Math.round(s.confidence * 100)}%</span>
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            )}
          </Card>

          {a && a.possibleDuplicates.length > 0 && (
            <Card title={<span className="flex items-center gap-2"><Lightbulb className="size-4 text-amber-500" /> Is it one of these?</span>}>
              <p className="mb-3 text-xs text-muted">Similar open incidents already being worked on. If yours is the same problem, you can follow that incident instead.</p>
              <ul className="space-y-2">
                {a.possibleDuplicates.map((d) => (
                  <li key={d.id} className="rounded-lg border border-base p-2.5 text-sm">
                    <div className="flex items-center justify-between gap-2">
                      <Link to={`/incidents/${d.id}`} className="font-mono text-xs text-brand-600 hover:underline">{d.number}</Link>
                      <span className="text-xs font-medium text-amber-600">{Math.round(d.score * 100)}% match</span>
                    </div>
                    <div className="mt-0.5 font-medium">{d.title}</div>
                    <div className="mt-1 flex items-center gap-2 text-xs text-muted"><StatusBadge status={d.status} /> {fmtAgo(d.reportedAt)}</div>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </aside>
      </div>
    </>
  );
}
