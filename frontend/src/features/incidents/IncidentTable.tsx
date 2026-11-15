import clsx from 'clsx';
import { Copy, Flame } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { PriorityBadge, SlaBadge, StatusBadge } from '../../components/badges';
import { EmptyState, Table } from '../../components/ui';
import { fmtAgo, fmtCountdown } from '../../lib/format';
import type { IncidentSummary } from '../../types';

export function IncidentTable({ incidents, showReporter = true, emptyText = 'No incidents found' }: {
  incidents: IncidentSummary[]; showReporter?: boolean; emptyText?: string;
}) {
  const navigate = useNavigate();
  if (incidents.length === 0) return <EmptyState title={emptyText} />;
  return (
    <Table>
      <thead>
        <tr>
          <th>Incident</th>
          <th>Priority</th>
          <th>Status</th>
          <th>Service</th>
          {showReporter && <th>Reporter</th>}
          <th>Assignee</th>
          <th>SLA</th>
          <th>Reported</th>
        </tr>
      </thead>
      <tbody>
        {incidents.map((i) => {
          const countdown = fmtCountdown(i.resolutionDeadline);
          const active = !['RESOLVED', 'VERIFIED', 'CLOSED'].includes(i.status);
          return (
            <tr
              key={i.id}
              onClick={() => navigate(`/incidents/${i.id}`)}
              className="cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/50"
            >
              <td className="max-w-md">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs text-muted">{i.number}</span>
                  {i.escalationLevel > 0 && (
                    <span title={`Escalated to level ${i.escalationLevel}`} className="inline-flex items-center text-xs font-medium text-red-600">
                      <Flame className="size-3.5" />L{i.escalationLevel}
                    </span>
                  )}
                  {i.suspectedDuplicate && <span title="Possible duplicate"><Copy className="size-3.5 text-amber-500" /></span>}
                </div>
                <div className="truncate font-medium">{i.title}</div>
                {i.category && <div className="text-xs text-muted">{i.category.name}</div>}
              </td>
              <td><PriorityBadge priority={i.priority} /></td>
              <td><StatusBadge status={i.status} /></td>
              <td className="text-muted">{i.service.name}</td>
              {showReporter && <td className="text-muted">{i.reporter.fullName}</td>}
              <td className={clsx(!i.assignedTo && 'text-muted italic')}>{i.assignedTo?.fullName ?? 'Unassigned'}</td>
              <td>
                <SlaBadge status={i.slaStatus} />
                {active && i.resolutionDeadline && (
                  <div className={clsx('text-xs', countdown.overdue ? 'text-red-600' : 'text-muted')}>{countdown.label}</div>
                )}
              </td>
              <td className="whitespace-nowrap text-muted">{fmtAgo(i.reportedAt)}</td>
            </tr>
          );
        })}
      </tbody>
    </Table>
  );
}
