import { useMutation } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { toast } from 'sonner';
import { errorMessage } from '../../api/client';
import { api } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { RoleBadge } from '../../components/badges';
import { Button, Card, Checkbox, Field, Input, PageHeader, PasswordInput } from '../../components/ui';
import { humanize } from '../../lib/format';

export function ProfilePage() {
  const { user, refresh } = useAuth();
  const [form, setForm] = useState({
    fullName: user?.fullName ?? '', department: user?.department ?? '', phone: user?.phone ?? '',
    emailNotifications: user?.emailNotifications ?? true, smsNotifications: user?.smsNotifications ?? false,
  });
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const pwMismatch = pw.confirmPassword.length > 0 && pw.newPassword !== pw.confirmPassword;
  const save = useMutation({
    mutationFn: () => api.auth.updateProfile(form),
    onSuccess: async () => { await refresh(); toast.success('Profile saved'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const changePw = useMutation({
    mutationFn: () => api.auth.changePassword({ currentPassword: pw.currentPassword, newPassword: pw.newPassword }),
    onSuccess: () => { setPw({ currentPassword: '', newPassword: '', confirmPassword: '' }); toast.success('Password changed'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (!user) return null;

  return (
    <>
      <PageHeader title="My profile" subtitle={<span className="flex items-center gap-2">{user.userCode} · {user.email} <RoleBadge role={user.role} /></span>} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Profile & notifications">
          <form className="space-y-4" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate(); }}>
            <Field label="Full name"><Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Department"><Input value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} /></Field>
              <Field label="Phone" hint="Needed for SMS alerts"><Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></Field>
            </div>
            <div className="space-y-2">
              <div className="text-sm font-medium">Notification channels</div>
              <Checkbox label="In-app (always on, real-time)" checked onChange={() => {}} />
              <Checkbox label="E-mail" checked={form.emailNotifications} onChange={(v) => setForm({ ...form, emailNotifications: v })} />
              <Checkbox label="SMS for urgent events (P1/P2, SLA breaches, escalations)" checked={form.smsNotifications} onChange={(v) => setForm({ ...form, smsNotifications: v })} />
            </div>
            <Button type="submit" loading={save.isPending}>Save</Button>
          </form>
        </Card>
        <div className="space-y-6">
          <Card title="Change password">
            <form className="space-y-4" onSubmit={(e: FormEvent) => { e.preventDefault(); changePw.mutate(); }}>
              <Field label="Current password"><PasswordInput autoComplete="current-password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} /></Field>
              <Field label="New password" hint="8+ characters, a letter and a digit"><PasswordInput autoComplete="new-password" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} /></Field>
              <Field label="Confirm new password" error={pwMismatch ? 'Passwords do not match' : undefined}><PasswordInput autoComplete="new-password" value={pw.confirmPassword} onChange={(e) => setPw({ ...pw, confirmPassword: e.target.value })} /></Field>
              <Button type="submit" variant="secondary" loading={changePw.isPending} disabled={!pw.currentPassword || !pw.newPassword || pw.newPassword !== pw.confirmPassword}>Change password</Button>
            </form>
          </Card>
          {user.technician && (
            <Card title="Technician profile">
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between"><dt className="text-muted">Experience</dt><dd>{user.technician.experienceYears} years</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Capacity</dt><dd>{user.technician.activeIncidents} / {user.technician.maxConcurrentIncidents} active</dd></div>
                <div className="flex justify-between"><dt className="text-muted">Availability</dt><dd>{humanize(user.technician.availability)}</dd></div>
                <div className="flex justify-between gap-4"><dt className="text-muted">Skills</dt><dd className="text-right">{user.technician.skills.map((s) => s.name).join(', ')}</dd></div>
                {user.technician.teamLead && <div className="flex justify-between"><dt className="text-muted">Team lead</dt><dd>{user.technician.teamLead.fullName}</dd></div>}
              </dl>
            </Card>
          )}
          <Card title="Permissions">
            <div className="flex flex-wrap gap-1">
              {user.permissions.map((p) => <span key={p} className="rounded-md bg-slate-100 px-1.5 py-0.5 font-mono text-[11px] dark:bg-slate-800">{p}</span>)}
            </div>
          </Card>
        </div>
      </div>
    </>
  );
}
