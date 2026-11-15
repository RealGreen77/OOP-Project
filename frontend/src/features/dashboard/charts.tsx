import { Table2 } from 'lucide-react';
import { type ReactNode, useState } from 'react';
import {
  Bar, BarChart, CartesianGrid, Cell, LabelList, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { Card, Table } from '../../components/ui';
import { useChartTheme } from '../../hooks/useChartTheme';

/** Card with a chart/table toggle so every chart has an accessible table view. */
export function ChartCard({ title, rows, columns, children }: {
  title: string; rows: (string | number)[][]; columns: string[]; children: ReactNode;
}) {
  const [table, setTable] = useState(false);
  return (
    <Card title={title} actions={
      <button onClick={() => setTable((t) => !t)} className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs text-muted hover:bg-slate-100 dark:hover:bg-slate-800"
        aria-pressed={table}>
        <Table2 className="size-3.5" /> {table ? 'Chart' : 'Table'}
      </button>
    }>
      {table ? (
        <Table>
          <thead><tr>{columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
          <tbody>{rows.map((r, i) => <tr key={i}>{r.map((v, j) => <td key={j} className={j > 0 ? 'tabular-nums' : ''}>{v}</td>)}</tr>)}</tbody>
        </Table>
      ) : <div className="h-64">{children}</div>}
    </Card>
  );
}

function tooltipStyle(theme: ReturnType<typeof useChartTheme>) {
  return {
    contentStyle: { background: theme.surface, border: `1px solid ${theme.grid}`, borderRadius: 8, color: theme.text, fontSize: 12 },
    labelStyle: { color: theme.text, fontWeight: 600 },
    itemStyle: { color: theme.text },
  };
}

/** Two-series trend (reported vs resolved): 2px lines, crosshair tooltip, legend + one axis. */
export function TrendChart({ data }: { data: { date: string; reported: number; resolved: number }[] }) {
  const theme = useChartTheme();
  const rows = data.map((d) => ({ ...d, day: d.date.slice(5) }));
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={rows} margin={{ top: 8, right: 12, left: -16, bottom: 0 }}>
        <CartesianGrid stroke={theme.grid} vertical={false} />
        <XAxis dataKey="day" tick={{ fill: theme.axis, fontSize: 11 }} tickLine={false} axisLine={{ stroke: theme.grid }} />
        <YAxis allowDecimals={false} tick={{ fill: theme.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
        <Tooltip {...tooltipStyle(theme)} cursor={{ stroke: theme.axis, strokeDasharray: '3 3' }} />
        <Legend iconType="plainline" wrapperStyle={{ fontSize: 12, color: theme.axis }} />
        <Line type="monotone" dataKey="reported" name="Reported" stroke={theme.series[0]} strokeWidth={2} dot={false} activeDot={{ r: 4, stroke: theme.surface, strokeWidth: 2 }} />
        <Line type="monotone" dataKey="resolved" name="Resolved" stroke={theme.series[1]} strokeWidth={2} dot={false} activeDot={{ r: 4, stroke: theme.surface, strokeWidth: 2 }} />
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Single-series horizontal magnitude bars (one hue, no legend - the card title names the series). */
export function HBarChart({ data, color, unit = '' }: { data: { label: string; count: number }[]; color?: string; unit?: string }) {
  const theme = useChartTheme();
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 36, left: 8, bottom: 0 }} barCategoryGap={4}>
        <CartesianGrid stroke={theme.grid} horizontal={false} />
        <XAxis type="number" allowDecimals={false} tick={{ fill: theme.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
        <YAxis type="category" dataKey="label" width={130} tick={{ fill: theme.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
        <Tooltip {...tooltipStyle(theme)} cursor={{ fill: theme.grid, opacity: 0.5 }} formatter={(v) => [`${v}${unit}`, 'Count']} />
        <Bar dataKey="count" fill={color ?? theme.series[0]} radius={[0, 4, 4, 0]} maxBarSize={18}>
          <LabelList dataKey="count" position="right" style={{ fill: theme.text, fontSize: 11 }} formatter={(v: unknown) => `${v}${unit}`} />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Vertical bars where each bar has its own meaningful colour (e.g. priority severity), labelled on the axis. */
export function ColoredBarChart({ data, colors, unit = '', domainMax }: {
  data: { label: string; value: number }[]; colors: Record<string, string>; unit?: string; domainMax?: number;
}) {
  const theme = useChartTheme();
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 20, right: 8, left: -16, bottom: 0 }} barCategoryGap="30%">
        <CartesianGrid stroke={theme.grid} vertical={false} />
        <XAxis dataKey="label" tick={{ fill: theme.axis, fontSize: 12 }} tickLine={false} axisLine={{ stroke: theme.grid }} />
        <YAxis domain={[0, domainMax ?? 'auto']} allowDecimals={false} tick={{ fill: theme.axis, fontSize: 11 }} tickLine={false} axisLine={false} />
        <Tooltip {...tooltipStyle(theme)} cursor={{ fill: theme.grid, opacity: 0.5 }} formatter={(v) => [`${v}${unit}`, '']} />
        <Bar dataKey="value" radius={[4, 4, 0, 0]} maxBarSize={48}>
          {data.map((d) => <Cell key={d.label} fill={colors[d.label] ?? theme.series[0]} />)}
          <LabelList dataKey="value" position="top" style={{ fill: theme.text, fontSize: 11 }} formatter={(v: unknown) => `${v}${unit}`} />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
