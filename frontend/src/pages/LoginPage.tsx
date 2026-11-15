import { ShieldCheck } from 'lucide-react';
import { type FormEvent, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorBox, Field, Input } from '../components/ui';

const DEMO_ACCOUNTS = [
  { label: 'Employee', email: 'employee@itsm.local', who: 'Asha Rao · reports incidents' },
  { label: 'Technician', email: 'tech@itsm.local', who: 'Arjun Mehta · network & cloud' },
  { label: 'Team Lead', email: 'lead@itsm.local', who: 'Vikram Rao · applications team' },
  { label: 'Administrator', email: 'admin@itsm.local', who: 'Priya Sharma · IT operations' },
  { label: 'IT Manager', email: 'manager@itsm.local', who: 'Rajesh Iyer · final escalation' },
];
const DEMO_PASSWORD = 'Password@123';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  async function submit(e: FormEvent, override?: { email: string; password: string }) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(override?.email ?? email, override?.password ?? password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-full lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-gradient-to-br from-brand-700 via-brand-600 to-indigo-900 p-10 text-white lg:flex">
        <div className="flex items-center gap-2 text-lg font-semibold"><ShieldCheck /> I-ITSM</div>
        <div>
          <h1 className="text-4xl font-semibold leading-tight">Intelligent IT Service Management</h1>
          <p className="mt-4 max-w-md text-brand-100">
            Report, classify, prioritise, assign and resolve IT incidents with automatic SLA tracking,
            duplicate detection, smart technician recommendations and real-time notifications.
          </p>
          <ul className="mt-8 grid max-w-md grid-cols-2 gap-3 text-sm text-brand-100">
            <li>• State-driven lifecycle</li><li>• Pluggable strategies</li>
            <li>• Predictive SLA risk</li><li>• Incident correlation</li>
            <li>• Live dashboards</li><li>• Complete audit trail</li>
          </ul>
        </div>
        <p className="text-xs text-brand-200">CS F213 Object-Oriented Programming · BITS Pilani</p>
      </div>

      <div className="flex items-center justify-center p-6">
        <div className="w-full max-w-md space-y-6">
          <div>
            <h2 className="text-2xl font-semibold">Sign in</h2>
            <p className="mt-1 text-sm text-muted">Use your organisation account.</p>
          </div>
          <form onSubmit={submit} className="space-y-4">
            {error && <ErrorBox message={error} />}
            <Field label="Email">
              <Input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
            <Field label="Password">
              <Input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
            </Field>
            <Button type="submit" className="w-full" loading={busy}>Sign in</Button>
          </form>
          <p className="text-center text-sm text-muted">
            New employee? <Link to="/register" className="font-medium text-brand-600 hover:underline">Create an account</Link>
          </p>

          <div className="surface rounded-xl border border-base p-4">
            <div className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted">Demo accounts <span className="font-mono normal-case tracking-normal">· password {DEMO_PASSWORD}</span></div>
            <div className="grid gap-2">
              {DEMO_ACCOUNTS.map((a) => (
                <button
                  key={a.email}
                  type="button"
                  disabled={busy}
                  onClick={(e) => submit(e, { email: a.email, password: DEMO_PASSWORD })}
                  className="flex items-center justify-between rounded-lg border border-base px-3 py-2 text-left text-sm hover:border-brand-500 hover:bg-brand-50 dark:hover:bg-brand-900/30"
                >
                  <span className="font-medium">{a.label}</span>
                  <span className="text-xs text-muted">{a.who}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
