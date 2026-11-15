import { useQuery } from '@tanstack/react-query';
import { AlertOctagon, Clock, Flame, Gauge, Inbox, Star, UserX } from 'lucide-react';
import { api } from '../../api/endpoints';
import { Card, PageHeader, Spinner, StatCard, Table } from '../../components/ui';
import { fmtMinutes, PRIORITY_COLORS } from '../../lib/format';
import { ChartCard, ColoredBarChart, HBarChart, TrendChart } from './charts';

/** Real-time operational dashboard for administrators, IT managers and team leads. */
export function OperationsDashboard({ title = 'Operations dashboard' }: { title?: string }) {
  const { data, isLoading } = useQuery({ queryKey: ['dashboard'], queryFn: api.reports.dashboard, refetchInterval: 60000 });
  if (isLoading || !data) return <Spinner />;
  const o = data.overview;
  const priorities = ['P1', 'P2', 'P3', 'P4'].map((p) => ({ label: p, value: data.byPriority.find((x) => x.label === p)?.count ?? 0 }));
  const compliance = data.slaCompliance.map((c) => ({ label: c.priority, value: c.compliancePercent }));

  return (
    <>
      <PageHeader title={title} subtitle="Live view of incident volume, SLA health and team workload. Updates automatically." />
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4 xl:grid-cols-8">
        <StatCard label="Total" value={o.totalIncidents} icon={<Inbox className="size-4" />} />
        <StatCard label="Open" value={o.openIncidents} />
        <StatCard label="Critical open" value={o.criticalOpen} tone={o.criticalOpen ? 'danger' : 'default'} icon={<AlertOctagon className="size-4" />} />
        <StatCard label="Resolved" value={o.resolvedIncidents} tone="success" />
        <StatCard label="SLA violations" value={o.slaViolations} tone={o.slaViolations ? 'danger' : 'default'} />
        <StatCard label="Escalated" value={o.escalatedOpen} tone={o.escalatedOpen ? 'warning' : 'default'} icon={<Flame className="size-4" />} />
        <StatCard label="Unassigned" value={o.unassignedOpen} tone={o.unassignedOpen ? 'warning' : 'default'} icon={<UserX className="size-4" />} />
        <StatCard label="SLA compliance" value={o.slaCompliancePercent != null ? `${o.slaCompliancePercent}%` : '—'} icon={<Gauge className="size-4" />}
          tone={(o.slaCompliancePercent ?? 100) >= 90 ? 'success' : 'warning'} />
      </div>
      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Mean time to acknowledge" value={fmtMinutes(o.meanTimeToAcknowledgeMinutes)} icon={<Clock className="size-4" />} hint="Last 90 days" />
        <StatCard label="Mean time to resolve" value={fmtMinutes(o.meanTimeToResolveMinutes)} icon={<Clock className="size-4" />} hint="Last 90 days" />
        <StatCard label="Average satisfaction" value={o.averageRating != null ? `${o.averageRating} / 5` : '—'} icon={<Star className="size-4" />} hint="Reporter feedback" />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <ChartCard title="Incidents reported vs resolved (14 days)" columns={['Date', 'Reported', 'Resolved']}
          rows={data.trend.map((t) => [t.date, t.reported, t.resolved])}>
          <TrendChart data={data.trend} />
        </ChartCard>
        <ChartCard title="Incidents by priority" columns={['Priority', 'Incidents']} rows={priorities.map((p) => [p.label, p.value])}>
          <ColoredBarChart data={priorities} colors={PRIORITY_COLORS} />
        </ChartCard>
        <ChartCard title="Incidents by service" columns={['Service', 'Incidents']} rows={data.byService.map((s) => [s.label, s.count])}>
          <HBarChart data={data.byService.slice(0, 8)} />
        </ChartCard>
        <ChartCard title="Incidents by category" columns={['Category', 'Incidents']} rows={data.byCategory.map((s) => [s.label, s.count])}>
          <HBarChart data={data.byCategory.slice(0, 8)} />
        </ChartCard>
        <ChartCard title="SLA compliance by priority (resolved incidents)" columns={['Priority', 'Resolved', 'Breached', 'Compliance']}
          rows={data.slaCompliance.map((c) => [c.priority, c.resolved, c.breached, `${c.compliancePercent}%`])}>
          <ColoredBarChart data={compliance} colors={PRIORITY_COLORS} unit="%" domainMax={100} />
        </ChartCard>
        <ChartCard title="Incidents by status" columns={['Status', 'Incidents']} rows={data.byStatus.map((s) => [s.label, s.count])}>
          <HBarChart data={data.byStatus.map((s) => ({ ...s, label: s.label.replace('_', ' ').toLowerCase() }))} />
        </ChartCard>
      </div>

      <Card title="Technician workload" className="mt-6" padded={false}>
        <Table>
          <thead><tr><th>Technician</th><th>Availability</th><th>Active</th><th>Load</th><th>Resolved (total)</th><th>Avg rating</th></tr></thead>
          <tbody>
            {data.technicianWorkload.map((t) => {
              const pct = Math.min(100, Math.round((t.activeIncidents / t.maxIncidents) * 100));
              return (
                <tr key={t.technician.id}>
                  <td><div className="font-medium">{t.technician.fullName}</div><div className="text-xs text-muted">{t.technician.userCode}</div></td>
                  <td className="text-muted">{t.availability.replace('_', ' ').toLowerCase()}</td>
                  <td className="tabular-nums">{t.activeIncidents} / {t.maxIncidents}</td>
                  <td className="w-48">
                    <div className="h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                      <div className={pct >= 100 ? 'h-full bg-red-500' : pct >= 70 ? 'h-full bg-amber-500' : 'h-full bg-emerald-500'} style={{ width: `${pct}%` }} />
                    </div>
                  </td>
                  <td className="tabular-nums">{t.resolvedTotal}</td>
                  <td className="tabular-nums">{t.averageRating ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </Table>
      </Card>
    </>
  );
}
