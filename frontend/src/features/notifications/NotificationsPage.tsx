import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { BellOff, CheckCheck } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/endpoints';
import { Button, Card, EmptyState, PageHeader, Spinner } from '../../components/ui';
import { fmtAgo } from '../../lib/format';
import type { Notification } from '../../types';

const URGENT = new Set(['SLA_BREACHED', 'INCIDENT_ESCALATED', 'MAJOR_INCIDENT_DETECTED']);

export function NotificationsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const inbox = useQuery({ queryKey: ['notifications', 'inbox', page], queryFn: () => api.notifications.inbox(page, 25) });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markAll = useMutation({ mutationFn: api.notifications.markAllRead, onSuccess: invalidate });
  const markOne = useMutation({ mutationFn: api.notifications.markRead, onSuccess: invalidate });

  function open(n: Notification) {
    if (!n.read) markOne.mutate(n.id);
    if (n.incidentId) navigate(`/incidents/${n.incidentId}`);
  }

  return (
    <>
      <PageHeader title="Notifications" subtitle="In-app notifications arrive instantly over WebSocket; e-mail and SMS copies are sent per your preferences."
        actions={<Button variant="secondary" icon={<CheckCheck className="size-4" />} loading={markAll.isPending} onClick={() => markAll.mutate()}>Mark all read</Button>} />
      <Card padded={false}>
        {!inbox.data ? <Spinner /> : inbox.data.content.length === 0 ? <EmptyState icon={<BellOff />} title="No notifications yet" /> : (
          <ul>
            {inbox.data.content.map((n) => (
              <li key={n.id}>
                <button onClick={() => open(n)} className={clsx('flex w-full gap-3 border-t border-base px-4 py-3 text-left first:border-t-0 hover:bg-slate-50 dark:hover:bg-slate-800/50',
                  !n.read && 'bg-brand-50/50 dark:bg-brand-900/20')}>
                  <span className={clsx('mt-1.5 size-2 shrink-0 rounded-full', n.read ? 'bg-transparent' : URGENT.has(n.eventType) ? 'bg-red-500' : 'bg-brand-500')} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline gap-x-2">
                      <span className={clsx('text-sm', !n.read && 'font-semibold')}>{n.title}</span>
                      {n.incidentNumber && <span className="font-mono text-xs text-muted">{n.incidentNumber}</span>}
                      <span className="ml-auto text-xs text-muted">{fmtAgo(n.createdAt)}</span>
                    </div>
                    <p className="mt-0.5 text-sm text-muted">{n.message}</p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
        {inbox.data && inbox.data.totalPages > 1 && (
          <div className="flex justify-end gap-2 border-t border-base p-3">
            <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
            <Button size="sm" variant="secondary" disabled={page + 1 >= inbox.data.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
          </div>
        )}
      </Card>
    </>
  );
}
