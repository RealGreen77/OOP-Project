import { type FormEvent, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { errorMessage, problemOf } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorBox, Field, Input } from '../components/ui';

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', department: '', phone: '' });
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await register(form);
      navigate('/', { replace: true });
    } catch (err) {
      setFieldErrors(problemOf(err)?.fieldErrors ?? {});
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center p-6">
      <form onSubmit={submit} className="surface w-full max-w-md space-y-4 rounded-xl border border-base p-6 shadow-sm">
        <div>
          <h1 className="text-xl font-semibold">Create your account</h1>
          <p className="text-sm text-muted">Employees can self-register to report and track incidents.</p>
        </div>
        {error && <ErrorBox message={error} />}
        <Field label="Full name" required error={fieldErrors.fullName}><Input required value={form.fullName} onChange={set('fullName')} /></Field>
        <Field label="Work email" required error={fieldErrors.email}><Input type="email" required value={form.email} onChange={set('email')} /></Field>
        <Field label="Password" required hint="8+ characters with at least one letter and one digit" error={fieldErrors.password}>
          <Input type="password" required value={form.password} onChange={set('password')} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Department"><Input value={form.department} onChange={set('department')} /></Field>
          <Field label="Phone" error={fieldErrors.phone}><Input value={form.phone} onChange={set('phone')} placeholder="+91 ..." /></Field>
        </div>
        <Button type="submit" className="w-full" loading={busy}>Create account</Button>
        <p className="text-center text-sm text-muted">Already registered? <Link to="/login" className="font-medium text-brand-600 hover:underline">Sign in</Link></p>
      </form>
    </div>
  );
}
