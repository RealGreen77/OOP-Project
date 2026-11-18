import { MailCheck } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { errorMessage, problemOf } from '../api/client';
import { api } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Button, ErrorBox, Field, Input, PasswordInput } from '../components/ui';

const PASSWORD_HINT = '8+ characters with at least one letter and one digit';

/**
 * Two-step self-registration:
 * 1. details (with password confirmation) -> the server e-mails a one-time 6-digit code;
 * 2. the code -> the account is created. This proves the registrant owns the e-mail address.
 */
export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '', department: '', phone: '' });
  const [step, setStep] = useState<'details' | 'code'>('details');
  const [code, setCode] = useState('');
  const [resendIn, setResendIn] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (resendIn <= 0) return;
    const t = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [resendIn]);

  if (user) return <Navigate to="/" replace />;
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });
  const mismatch = form.confirmPassword.length > 0 && form.password !== form.confirmPassword;

  function fail(err: unknown) {
    setFieldErrors(problemOf(err)?.fieldErrors ?? {});
    setError(errorMessage(err));
  }

  async function sendCode(e?: FormEvent) {
    e?.preventDefault();
    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: 'Passwords do not match' });
      return;
    }
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      const res = await api.auth.requestCode(form.email.trim());
      setStep('code');
      setCode('');
      setResendIn(res.resendAfterSeconds);
    } catch (err) {
      fail(err);
    } finally {
      setBusy(false);
    }
  }

  async function createAccount(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    const { confirmPassword: _confirm, ...details } = form;
    try {
      await register({ ...details, email: form.email.trim(), verificationCode: code.trim() });
      navigate('/', { replace: true });
    } catch (err) {
      const problem = problemOf(err);
      // Field problems belong to the first step; send the user back there to fix them.
      if (problem?.fieldErrors && Object.keys(problem.fieldErrors).length) setStep('details');
      fail(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center p-6">
      <div className="surface w-full max-w-md space-y-4 rounded-xl border border-base p-6 shadow-sm">
        <div>
          <h1 className="text-xl font-semibold">Create your account</h1>
          <p className="text-sm text-muted">
            {step === 'details' ? 'Employees can self-register to report and track incidents.' : 'Step 2 of 2 - verify your e-mail.'}
          </p>
        </div>
        {error && <ErrorBox message={error} />}

        {step === 'details' ? (
          <form onSubmit={sendCode} className="space-y-4">
            <Field label="Full name" required error={fieldErrors.fullName}><Input required value={form.fullName} onChange={set('fullName')} /></Field>
            <Field label="Work email" required hint="We will e-mail a 6-digit code to confirm it is yours" error={fieldErrors.email}>
              <Input type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
            </Field>
            <Field label="Password" required hint={PASSWORD_HINT} error={fieldErrors.password}>
              <PasswordInput autoComplete="new-password" required value={form.password} onChange={set('password')} />
            </Field>
            <Field label="Confirm password" required error={fieldErrors.confirmPassword ?? (mismatch ? 'Passwords do not match' : undefined)}>
              <PasswordInput autoComplete="new-password" required value={form.confirmPassword} onChange={set('confirmPassword')} />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Department"><Input value={form.department} onChange={set('department')} /></Field>
              <Field label="Phone" error={fieldErrors.phone}><Input value={form.phone} onChange={set('phone')} placeholder="+91 ..." /></Field>
            </div>
            <Button type="submit" className="w-full" loading={busy} disabled={mismatch}>Send verification code</Button>
          </form>
        ) : (
          <form onSubmit={createAccount} className="space-y-4">
            <div className="flex gap-3 rounded-lg border border-base p-3 text-sm">
              <MailCheck className="mt-0.5 size-5 shrink-0 text-brand-600" />
              <p>We sent a 6-digit code to <span className="font-medium">{form.email.trim()}</span>. It expires in 10 minutes. Check your spam folder if it does not arrive.</p>
            </div>
            <Field label="Verification code" required>
              <Input
                required
                autoFocus
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                className="text-center font-mono text-lg tracking-[0.5em]"
              />
            </Field>
            <Button type="submit" className="w-full" loading={busy} disabled={code.length !== 6}>Create account</Button>
            <div className="flex items-center justify-between text-sm">
              <button type="button" className="text-brand-600 hover:underline" onClick={() => { setStep('details'); setError(null); }}>
                Change details
              </button>
              <button type="button" className="text-brand-600 hover:underline disabled:cursor-not-allowed disabled:text-muted disabled:no-underline"
                disabled={resendIn > 0 || busy} onClick={() => sendCode()}>
                {resendIn > 0 ? `Resend code in ${resendIn}s` : 'Resend code'}
              </button>
            </div>
          </form>
        )}
        <p className="text-center text-sm text-muted">Already registered? <Link to="/login" className="font-medium text-brand-600 hover:underline">Sign in</Link></p>
      </div>
    </div>
  );
}
