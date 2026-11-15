import { differenceInMinutes, format, formatDistanceToNowStrict } from 'date-fns';
import type { IncidentStatus, Priority, Role, SlaStatus } from '../types';

export const fmtDateTime = (iso?: string) => (iso ? format(new Date(iso), 'dd MMM yyyy, HH:mm') : '—');
export const fmtTime = (iso?: string) => (iso ? format(new Date(iso), 'HH:mm') : '—');
export const fmtAgo = (iso?: string) => (iso ? `${formatDistanceToNowStrict(new Date(iso))} ago` : '—');

/** "in 25m" / "overdue 1h 5m" relative to now. */
export function fmtCountdown(iso?: string): { label: string; overdue: boolean } {
  if (!iso) return { label: '—', overdue: false };
  const diff = differenceInMinutes(new Date(iso), new Date());
  const abs = Math.abs(diff);
  const text = abs < 60 ? `${abs}m` : abs < 2880 ? `${Math.floor(abs / 60)}h ${abs % 60}m` : `${Math.floor(abs / 1440)}d`;
  return diff >= 0 ? { label: `in ${text}`, overdue: false } : { label: `overdue ${text}`, overdue: true };
}

export function fmtMinutes(m?: number | null): string {
  if (m === undefined || m === null) return '—';
  if (m < 60) return `${Math.round(m)} min`;
  if (m < 1440) return `${(m / 60).toFixed(m % 60 === 0 ? 0 : 1)} h`;
  return `${(m / 1440).toFixed(1)} d`;
}

export const humanize = (s?: string) =>
  s ? s.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase()) : '';

export const ROLE_LABEL: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  TECHNICIAN: 'Technician',
  TEAM_LEAD: 'Team Lead',
  ADMIN: 'Administrator',
  IT_MANAGER: 'IT Manager',
};

export const PRIORITY_LABEL: Record<Priority, string> = { P1: 'Critical', P2: 'High', P3: 'Medium', P4: 'Low' };

export const PRIORITY_STYLE: Record<Priority, string> = {
  P1: 'bg-red-600 text-white',
  P2: 'bg-orange-500 text-white',
  P3: 'bg-amber-400 text-amber-950',
  P4: 'bg-slate-400 text-white dark:bg-slate-600',
};

export const STATUS_STYLE: Record<IncidentStatus, string> = {
  NEW: 'bg-sky-100 text-sky-800 dark:bg-sky-950 dark:text-sky-300',
  CLASSIFIED: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950 dark:text-indigo-300',
  ASSIGNED: 'bg-violet-100 text-violet-800 dark:bg-violet-950 dark:text-violet-300',
  ACKNOWLEDGED: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
  IN_PROGRESS: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-950 dark:text-cyan-300',
  PENDING_INFO: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  RESOLVED: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300',
  VERIFIED: 'bg-teal-100 text-teal-800 dark:bg-teal-950 dark:text-teal-300',
  CLOSED: 'bg-slate-200 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
};

export const SLA_STYLE: Record<SlaStatus, string> = {
  ON_TRACK: 'text-emerald-600 dark:text-emerald-400',
  AT_RISK: 'text-amber-600 dark:text-amber-400',
  PAUSED: 'text-sky-600 dark:text-sky-400',
  BREACHED: 'text-red-600 dark:text-red-400',
  MET: 'text-emerald-600 dark:text-emerald-400',
  NOT_APPLICABLE: 'text-muted',
};

export const LIFECYCLE: IncidentStatus[] = [
  'NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED',
];

/**
 * Validated categorical palette (fixed slot order, CVD-checked) - light and dark steps of the same hues.
 * Assign in order, never cycle: a series keeps its slot regardless of rank.
 */
export const SERIES_LIGHT = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'];
export const SERIES_DARK = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#0ca30c', '#9085e9', '#e66767'];
/** Priority is a severity scale, so it uses the reserved status colours (always shown with a label). */
export const PRIORITY_COLORS: Record<string, string> = { P1: '#d03b3b', P2: '#ec835a', P3: '#fab219', P4: '#8a8a85' };

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
