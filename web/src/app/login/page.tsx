'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useAuth, friendlyAuthError } from '@/lib/auth-context';
import { isFirebaseConfigured } from '@/lib/firebase';
import { LoopGlyph } from '@/components/SiteHeader';

/**
 * Caretaker sign-in.
 *
 * Real Firebase Auth when configured. When it is not, the form explains that
 * plainly and opens the dashboard on demo data rather than staging a fake
 * authentication, which would be a small lie told to someone evaluating the
 * product.
 */
export default function LoginPage() {
  const router = useRouter();
  const { signIn, signUp } = useAuth();

  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    if (!isFirebaseConfigured) {
      router.push('/dashboard');
      return;
    }

    setBusy(true);
    try {
      if (mode === 'signin') {
        await signIn(email, password);
      } else {
        await signUp(email, password, name);
      }
      router.push('/dashboard');
    } catch (err) {
      setError(friendlyAuthError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      {/* ------------------------------------------------------- The form */}
      <div className="flex items-center justify-center bg-cream px-6 py-16">
        <div className="w-full max-w-sm">
          <Link href="/" className="mb-10 flex items-center gap-3 text-navy">
            <span className="text-gold-ink">
              <LoopGlyph className="h-8 w-8" />
            </span>
            <span className="font-display text-xl">CareLoop</span>
          </Link>

          <h1 className="font-display text-4xl text-ink">
            {mode === 'signin' ? 'Welcome back' : 'Create your account'}
          </h1>
          <p className="mt-3 leading-relaxed text-slate-ink">
            {mode === 'signin'
              ? 'Sign in to see how your mother is doing.'
              : 'You will be able to connect her phone in a moment.'}
          </p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-4">
            {mode === 'signup' && (
              <Field
                id="name"
                label="Your name"
                type="text"
                value={name}
                onChange={setName}
                placeholder="Sarah Whitfield-Chen"
                autoComplete="name"
              />
            )}

            <Field
              id="email"
              label="Email address"
              type="email"
              value={email}
              onChange={setEmail}
              placeholder="sarah@example.com"
              autoComplete="email"
            />

            <Field
              id="password"
              label="Password"
              type="password"
              value={password}
              onChange={setPassword}
              placeholder="••••••••"
              autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
            />

            {error && (
              <p
                role="alert"
                className="rounded-xl bg-urgent-surface px-4 py-3 text-sm text-urgent"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="w-full rounded-2xl bg-navy px-6 py-4 font-semibold text-white transition hover:bg-navy-soft disabled:opacity-70"
            >
              {busy
                ? 'One moment…'
                : mode === 'signin'
                  ? 'Sign in'
                  : 'Create account'}
            </button>
          </form>

          <button
            onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null); }}
            className="mt-6 text-sm text-slate-ink transition hover:text-ink"
          >
            {mode === 'signin'
              ? 'No account yet? Create one'
              : 'Already have an account? Sign in'}
          </button>

          {!isFirebaseConfigured && (
            <div className="mt-8 rounded-2xl border border-ink/10 bg-white px-5 py-4">
              <p className="text-sm font-semibold text-ink">Demo mode</p>
              <p className="mt-1 text-sm leading-relaxed text-slate-ink">
                No account system is connected yet, so signing in opens the
                dashboard on an example household. Nothing here is real.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* ------------------------------------------------------- The pitch */}
      <div className="relative hidden overflow-hidden bg-brown lg:block">
        <div
          className="pointer-events-none absolute inset-0"
          style={{
            background:
              'radial-gradient(70% 60% at 60% 30%, rgba(36,55,102,0.75) 0%, transparent 70%)',
          }}
          aria-hidden
        />
        <div className="relative flex h-full flex-col justify-center px-16">
          <blockquote className="max-w-md">
            <p className="font-display text-3xl leading-snug text-white">
              &ldquo;I&apos;m reaching out because your mother missed her warfarin
              twice this week, and both times she wasn&apos;t sure whether
              she&apos;d taken it.&rdquo;
            </p>
            <footer className="mt-6 text-sm text-gold">
              Cara, to Sarah, on a Thursday morning
            </footer>
          </blockquote>

          <p className="mt-12 max-w-md leading-relaxed text-white/60">
            One missed dose is normal. Three in a week is a warning sign. CareLoop
            is built to know the difference, and to tell you why.
          </p>
        </div>
      </div>
    </main>
  );
}

function Field({
  id,
  label,
  type,
  value,
  onChange,
  placeholder,
  autoComplete,
}: {
  id: string;
  label: string;
  type: string;
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  autoComplete: string;
}) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
      </label>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        autoComplete={autoComplete}
        required
        className="mt-2 w-full rounded-2xl border border-ink/15 bg-white px-5 py-4 text-ink placeholder:text-ink/25 focus:border-navy focus:outline-none"
      />
    </div>
  );
}
