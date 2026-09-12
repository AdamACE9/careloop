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
 *
 * No Lenis, no ScrollTrigger here, deliberately. Someone filling in a form
 * wants it to respond immediately, not to feel cinematic. The only motion on
 * this page is the ordinary transition on the submit button and the hover
 * states, both plain CSS.
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
          <p className="mt-3 text-lg leading-relaxed text-slate-ink">
            {mode === 'signin'
              ? 'Sign in to see how your mother is doing.'
              : 'You will be able to connect her phone in a moment.'}
          </p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-5">
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
                className="rounded-xl bg-urgent-surface px-4 py-3 text-sm font-medium text-urgent"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={busy}
              className="flex min-h-[56px] w-full items-center justify-center rounded-2xl bg-navy px-6 py-4 text-lg font-semibold text-white transition hover:bg-navy-soft focus-visible:outline-offset-4 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {busy
                ? 'One moment…'
                : mode === 'signin'
                  ? 'Sign in'
                  : 'Create account'}
            </button>
          </form>

          <button
            type="button"
            onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null); }}
            className="mt-6 min-h-[44px] text-base text-slate-ink underline decoration-ink/20 underline-offset-4 transition hover:text-ink hover:decoration-ink/50"
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
          <p className="text-sm tracking-[0.18em] text-gold uppercase">
            A real morning, from the demo household
          </p>
          <blockquote className="mt-6 max-w-md">
            <p className="font-display text-3xl leading-snug text-white">
              &ldquo;Margaret has missed her warfarin twice this week, and both
              times she wasn&apos;t sure whether she&apos;d taken it.&rdquo;
            </p>
            <footer className="mt-6 text-sm text-white/50">
              Cara, to Sarah, the morning it happened twice
            </footer>
          </blockquote>

          <p className="mt-12 max-w-md text-lg leading-relaxed text-white/60">
            One missed dose is normal. Two, the same way, on the medication that
            matters most, is worth a phone call. CareLoop is built to know the
            difference, and to say exactly why.
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
      <label htmlFor={id} className="block text-base font-medium text-ink">
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
        className="mt-2 min-h-[56px] w-full rounded-2xl border border-ink/15 bg-white px-5 py-4 text-lg text-ink placeholder:text-ink/25 focus:border-navy focus:outline-none"
      />
    </div>
  );
}
