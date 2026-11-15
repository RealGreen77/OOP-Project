import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  Activity, Bell, BrainCircuit, ClipboardList, Gauge, LayoutDashboard, ListChecks, LogOut, Menu, Moon,
  Plus, ScrollText, Send, Server, Settings2, ShieldCheck, Sun, Timer, UserCircle, Users, X,
} from 'lucide-react';
import { type ReactNode, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { useRealtime } from '../hooks/useRealtime';
import { useTheme } from '../hooks/useTheme';
import { ROLE_LABEL } from '../lib/format';
import type { Permission } from '../types';

interface NavItem { to: string; label: string; icon: ReactNode; permission?: Permission; end?: boolean }
interface NavGroup { title?: string; items: NavItem[] }

/** Navigation differs per role: items are filtered by the permissions the backend reports for the user. */
const NAV: NavGroup[] = [
  {
    items: [
      { to: '/', label: 'Dashboard', icon: <LayoutDashboard className="size-4" />, end: true },
      { to: '/incidents/new', label: 'Report incident', icon: <Plus className="size-4" />, permission: 'REPORT_INCIDENT' },
      { to: '/incidents', label: 'Incidents', icon: <ClipboardList className="size-4" />, end: true },
      { to: '/queue', label: 'My queue', icon: <ListChecks className="size-4" />, permission: 'WORK_INCIDENT' },
      { to: '/notifications', label: 'Notifications', icon: <Bell className="size-4" /> },
    ],
  },
  {
    title: 'Operations',
    items: [
      { to: '/intelligence', label: 'Intelligence', icon: <BrainCircuit className="size-4" />, permission: 'VIEW_ALL_INCIDENTS' },
      { to: '/workload', label: 'Team workload', icon: <Activity className="size-4" />, permission: 'VIEW_TEAM_WORKLOAD' },
      { to: '/reports', label: 'Reports', icon: <Gauge className="size-4" />, permission: 'VIEW_REPORTS' },
    ],
  },
  {
    title: 'Administration',
    items: [
      { to: '/admin/users', label: 'Users & technicians', icon: <Users className="size-4" />, permission: 'MANAGE_USERS' },
      { to: '/admin/catalog', label: 'Services & categories', icon: <Server className="size-4" />, permission: 'MANAGE_CATALOG' },
      { to: '/admin/sla', label: 'SLA policies', icon: <Timer className="size-4" />, permission: 'MANAGE_SLA' },
      { to: '/admin/settings', label: 'Strategies', icon: <Settings2 className="size-4" />, permission: 'CONFIGURE_STRATEGY' },
      { to: '/admin/audit', label: 'Audit log', icon: <ScrollText className="size-4" />, permission: 'VIEW_AUDIT' },
      { to: '/admin/deliveries', label: 'Delivery log', icon: <Send className="size-4" />, permission: 'MANAGE_USERS' },
    ],
  },
];

export function Layout() {
  const { user, logout, can } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const live = useRealtime(!!user, (id) => navigate(`/incidents/${id}`));
  const { data: unread } = useQuery({ queryKey: ['notifications', 'unread'], queryFn: api.notifications.unread, refetchInterval: 60000 });

  if (!user) return null;
  const groups = NAV.map((g) => ({ ...g, items: g.items.filter((i) => !i.permission || can(i.permission)) })).filter((g) => g.items.length);

  const sidebar = (
    <nav className="flex h-full flex-col">
      <div className="flex items-center gap-2 px-4 py-4">
        <div className="grid size-8 place-items-center rounded-lg bg-brand-600 text-white"><ShieldCheck className="size-5" /></div>
        <div>
          <div className="text-sm font-semibold leading-tight">I-ITSM</div>
          <div className="text-[11px] text-muted">Service Desk</div>
        </div>
      </div>
      <div className="flex-1 space-y-5 overflow-y-auto px-3 py-2">
        {groups.map((g, gi) => (
          <div key={gi}>
            {g.title && <div className="mb-1 px-2 text-[11px] font-semibold uppercase tracking-wider text-muted">{g.title}</div>}
            <ul className="space-y-0.5">
              {g.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    onClick={() => setMobileOpen(false)}
                    className={({ isActive }) => clsx(
                      'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium transition-colors',
                      isActive ? 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200' : 'text-muted hover:bg-slate-100 hover:text-[var(--text)] dark:hover:bg-slate-800',
                    )}
                  >
                    {item.icon}
                    <span className="flex-1">{item.label}</span>
                    {item.to === '/notifications' && !!unread?.unread && (
                      <span className="rounded-full bg-red-600 px-1.5 text-[11px] font-semibold text-white">{unread.unread}</span>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-base p-3">
        <NavLink to="/profile" className="flex items-center gap-2 rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-slate-800">
          <UserCircle className="size-8 text-muted" />
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-medium">{user.fullName}</div>
            <div className="truncate text-xs text-muted">{user.userCode} · {ROLE_LABEL[user.role]}</div>
          </div>
        </NavLink>
      </div>
    </nav>
  );

  return (
    <div className="flex h-full">
      <aside className="surface hidden w-64 shrink-0 border-r border-base lg:block">{sidebar}</aside>
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-950/50" onClick={() => setMobileOpen(false)} />
          <aside className="surface absolute inset-y-0 left-0 w-72 border-r border-base">{sidebar}</aside>
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="surface flex h-14 shrink-0 items-center gap-3 border-b border-base px-4">
          <button className="rounded-md p-1.5 lg:hidden" onClick={() => setMobileOpen((o) => !o)} aria-label="Toggle menu">
            {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
          <div className="flex-1" />
          <span
            title={live ? 'Real-time updates connected' : 'Real-time updates reconnecting'}
            className={clsx('hidden items-center gap-1.5 text-xs sm:flex', live ? 'text-emerald-600' : 'text-muted')}
          >
            <span className={clsx('size-2 rounded-full', live ? 'bg-emerald-500' : 'bg-slate-400')} /> {live ? 'Live' : 'Offline'}
          </span>
          <button onClick={() => navigate('/notifications')} className="relative rounded-md p-2 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Notifications">
            <Bell className="size-5" />
            {!!unread?.unread && <span className="absolute right-1 top-1 size-2 rounded-full bg-red-500" />}
          </button>
          <button onClick={toggle} className="rounded-md p-2 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Toggle theme">
            {theme === 'dark' ? <Sun className="size-5" /> : <Moon className="size-5" />}
          </button>
          <button onClick={() => { logout(); navigate('/login'); }} className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm text-muted hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Log out">
            <LogOut className="size-4" /> <span className="hidden sm:inline">Log out</span>
          </button>
        </header>
        <main className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
