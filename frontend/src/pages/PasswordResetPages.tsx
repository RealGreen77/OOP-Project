import { MailCheck } from 'lucide-react';
import { type FormEvent, type ReactNode, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { errorMessage, problemOf } from '../api/client';
import { api } from '../api/endpoints';
import { Button, ErrorBox, Field, Input, PasswordInput } from '../components/ui';

function AuthCard({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="flex min-h-full items-center justify-center p-6">
      <div className="surface w-full max-w-md space-y-4 rounded-xl border border-base p-6 shadow-sm">
        <div>
          <h1 className="text-xl font-semibold">{title}</h1>
          <p className="text-sm text-muted">{subtitle}</p>
        </div>
        {children}
        <p className="text-center text-sm text-muted"><Link to="/login" className="font-medium text-brand-600 hover:underline">Back to sign in</Link></p>
      </div>
    </div>
  );
}

/** Step 1: ask for the account e-mail. The answer is the same whether or not the account exists. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.auth.forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Forgot your password?" subtitle="Enter your account e-mail and we will send you a link to choose a new password.">
      {sent ? (
        <div className="flex gap-3 rounded-lg border border-base p-3 text-sm">
          <MailCheck className="mt-0.5 size-5 shrink-0 text-brand-600" />
          <p>
            If an account exists for <span className="font-medium">{email.trim()}</span>, a reset link is on its way.
            It works once and expires in 30 minutes. Check your spam folder if it does not arrive.
          </p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          {error && <ErrorBox message={error} />}
          <Field label="Email" required>
            <Input type="email" autoComplete="email" required autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Button type="submit" className="w-full" loading={busy}>Send reset link</Button>
        </form>
      )}
    </AuthCard>
  );
}

/** Step 2: opened from the e-mailed link (?token=...). */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const [pw, setPw] = useState({ newPassword: '', confirmPassword: '' });
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [busy, setBusy] = useState(false);
  const mismatch = pw.confirmPassword.length > 0 && pw.newPassword !== pw.confirmPassword;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (pw.newPassword !== pw.confirmPassword) return;
    setBusy(true);
    setError(null);
    setFieldError(undefined);
    try {
      await api.auth.resetPassword(token, pw.newPassword);
      toast.success('Password changed - sign in with your new password');
      navigate('/login', { replace: true });
    } catch (err) {
      setFieldError(problemOf(err)?.fieldErrors?.newPassword);
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthCard title="Reset link missing" subtitle="Open the link from the e-mail we sent you, or request a new one.">
        <Button className="w-full" onClick={() => navigate('/forgot-password')}>Request a new link</Button>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose a new password" subtitle="Your new password replaces the old one immediately and unlocks the account.">
      <form onSubmit={submit} className="space-y-4">
        {error && (
          <div className="space-y-2">
            <ErrorBox message={error} />
            <Link to="/forgot-password" className="text-sm font-medium text-brand-600 hover:underline">Request a new link</Link>
          </div>
        )}
        <Field label="New password" required hint="8+ characters with at least one letter and one digit" error={fieldError}>
          <PasswordInput autoComplete="new-password" required autoFocus value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} />
        </Field>
        <Field label="Confirm new password" required error={mismatch ? 'Passwords do not match' : undefined}>
          <PasswordInput autoComplete="new-password" required value={pw.confirmPassword} onChange={(e) => setPw({ ...pw, confirmPassword: e.target.value })} />
        </Field>
        <Button type="submit" className="w-full" loading={busy} disabled={mismatch || !pw.newPassword || !pw.confirmPassword}>Set new password</Button>
      </form>
    </AuthCard>
  );
}
