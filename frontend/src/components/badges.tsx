import clsx from 'clsx';
import { AlertTriangle, CheckCircle2, Clock, PauseCircle, XCircle } from 'lucide-react';
import { PRIORITY_LABEL, PRIORITY_STYLE, ROLE_LABEL, SLA_STYLE, STATUS_STYLE, humanize } from '../lib/format';
import type { IncidentStatus, Priority, Role, SlaStatus } from '../types';

export function PriorityBadge({ priority, showLabel }: { priority?: Priority; showLabel?: boolean }) {
  if (!priority) return <span className="text-xs text-muted">—</span>;
  return (
    <span
      title={`${priority} - ${PRIORITY_LABEL[priority]}`}
      className={clsx('inline-flex items-center rounded-md px-1.5 py-0.5 font-mono text-xs font-semibold', PRIORITY_STYLE[priority])}
    >
      {priority}
      {showLabel && <span className="ml-1 font-sans font-medium">{PRIORITY_LABEL[priority]}</span>}
    </span>
  );
}

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <span className={clsx('inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', STATUS_STYLE[status])}>
      {humanize(status)}
    </span>
  );
}

const SLA_ICON: Record<SlaStatus, typeof Clock> = {
  ON_TRACK: Clock, AT_RISK: AlertTriangle, PAUSED: PauseCircle, BREACHED: XCircle, MET: CheckCircle2, NOT_APPLICABLE: Clock,
};

export function SlaBadge({ status }: { status: SlaStatus }) {
  const Icon = SLA_ICON[status];
  return (
    <span className={clsx('inline-flex items-center gap-1 whitespace-nowrap text-xs font-medium', SLA_STYLE[status])}>
      <Icon className="size-3.5" /> {status === 'NOT_APPLICABLE' ? 'No SLA' : humanize(status)}
    </span>
  );
}

export function RoleBadge({ role }: { role: Role }) {
  const style: Record<Role, string> = {
    EMPLOYEE: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
    TECHNICIAN: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-300',
    TEAM_LEAD: 'bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300',
    ADMIN: 'bg-brand-100 text-brand-700 dark:bg-brand-900 dark:text-brand-200',
    IT_MANAGER: 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300',
  };
  return <span className={clsx('inline-flex rounded-full px-2 py-0.5 text-xs font-medium', style[role])}>{ROLE_LABEL[role]}</span>;
}

export function Pill({ children, tone = 'slate' }: { children: React.ReactNode; tone?: 'slate' | 'red' | 'amber' | 'green' | 'brand' }) {
  const tones = {
    slate: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
    red: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
    amber: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
    green: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
    brand: 'bg-brand-100 text-brand-700 dark:bg-brand-900 dark:text-brand-200',
  };
  return <span className={clsx('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium', tones[tone])}>{children}</span>;
}
