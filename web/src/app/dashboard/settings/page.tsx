'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { useLinkedPatients, redeemLinkingCode } from '@/lib/careloop-service';
import { isFirebaseConfigured } from '@/lib/firebase';

/**
 * Settings: linking, account, and an honest account of what is connected.
 *
 * The linking flow runs elder to caretaker, not the other way round. The code is
 * generated on the elder's own phone and read out to whoever is being given
 * access, who types it in here. Access to someone's health record should be
 * granted by them, from a device in their hand, rather than claimed on their
 * behalf and mentioned afterwards.
 *
 * The code alphabet excludes O, 0, I, 1 and L, because somebody is going to read
 * it aloud to a 78-year-old and "was that an O or a zero" is a real failure
 * rather than a hypothetical one. The input below upper-cases as you type for the
 * same reason.
 */
export default function SettingsPage() {
  const { displayName, isDemo, signOut } = useAuth();
  const { patients } = useLinkedPatients();
  const patient = patients[0];

  const [code, setCode] = useState('');
  const [linking, setLinking] = useState(false);
  const [linkedName, setLinkedName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleLink() {
    if (!code.trim()) return;
    setLinking(true);
    setError(null);

    const result = await redeemLinkingCode(code);
    if (result.ok) {
      setLinkedName(result.patientName);
      setCode('');
    } else {
      setError(result.reason);
    }
    setLinking(false);
  }

  return (
    <div className="max-w-3xl space-y-8">
      <div>
        <h2 className="font-display text-3xl text-ink">Settings</h2>
      </div>

      {/* ------------------------------------------------------- Account */}
      <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
        <h3 className="font-display text-xl text-ink">Your account</h3>
        <p className="mt-3 text-slate-ink">
          Signed in as <span className="font-medium text-ink">{displayName}</span>
        </p>
        <button
          onClick={() => void signOut()}
          className="mt-5 rounded-xl border border-ink/20 px-5 py-3 text-sm font-semibold text-ink transition hover:bg-cloud"
        >
          Sign out
        </button>
      </section>

      {/* --------------------------------------------------- Linking code */}
      <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
        <h3 className="font-display text-xl text-ink">Connect to a phone</h3>
        <p className="mt-3 max-w-xl leading-relaxed text-slate-ink">
          Ask {patient?.firstName ?? 'them'} to open CareLoop on their phone and
          read you the code it shows. Type it in below. You only do this once, and
          the code works for 24 hours.
        </p>

        {linkedName ? (
          <div className="mt-6 rounded-2xl border border-good/30 bg-good-surface px-6 py-5">
            <p className="font-medium text-good">Connected to {linkedName}</p>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-ink">
              Their check-ins will appear on your dashboard from now on. They can
              see that you are connected, and can disconnect you at any time.
            </p>
          </div>
        ) : (
          <div className="mt-6">
            <label htmlFor="linkcode" className="block text-sm font-medium text-ink">
              Code from their phone
            </label>
            <div className="mt-2 flex flex-wrap gap-3">
              <input
                id="linkcode"
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') void handleLink();
                }}
                placeholder="XXXXXXXX"
                autoComplete="off"
                spellCheck={false}
                maxLength={16}
                className="w-56 rounded-xl border border-ink/20 bg-white px-4 py-3.5 font-display text-2xl tracking-[0.18em] text-ink uppercase placeholder:text-ink/25 focus:border-gold focus:outline-none"
              />
              <button
                onClick={() => void handleLink()}
                disabled={linking || !code.trim()}
                className="rounded-xl bg-navy px-6 py-3.5 font-semibold text-white transition hover:bg-navy-soft disabled:opacity-50"
              >
                {linking ? 'Connecting' : 'Connect'}
              </button>
            </div>

            {error && (
              <p role="alert" className="mt-3 text-sm leading-relaxed text-urgent">
                {error}
              </p>
            )}
          </div>
        )}
      </section>

      {/* --------------------------------------------------- What she sees */}
      <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
        <h3 className="font-display text-xl text-ink">
          What {patient?.firstName ?? 'she'} can see
        </h3>
        <p className="mt-3 max-w-xl leading-relaxed text-slate-ink">
          Everything on this dashboard. Every time Cara tells you something, she
          tells {patient?.firstName ?? 'her'} that she told you, and gives her the
          chance to correct it.
        </p>
        <p className="mt-3 max-w-xl leading-relaxed text-slate-ink">
          She can also mute routine categories. A genuine emergency still reaches
          you regardless, and she knows that too. Nothing here is hidden from her.
        </p>
      </section>

      {/* ---------------------------------------------------- Connection */}
      <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
        <h3 className="font-display text-xl text-ink">Connection</h3>
        <div className="mt-4 space-y-3">
          <StatusRow
            label="Backend"
            ok={isFirebaseConfigured}
            okText="Connected"
            offText="Running on demo data"
          />
          <StatusRow
            label="Live data"
            ok={!isDemo}
            okText="Reading from your account"
            offText="Example figures, nothing real"
          />
        </div>

        {isDemo && (
          <p className="mt-5 rounded-2xl bg-cloud px-5 py-4 text-sm leading-relaxed text-ink/75">
            This is a fully working dashboard running on an example household, so
            you can see exactly how it behaves before connecting anything.
          </p>
        )}
      </section>
    </div>
  );
}

function StatusRow({
  label,
  ok,
  okText,
  offText,
}: {
  label: string;
  ok: boolean;
  okText: string;
  offText: string;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-ink/5 pb-3 last:border-0">
      <span className="text-slate-ink">{label}</span>
      <span className="flex items-center gap-2 text-sm font-medium text-ink">
        {/* Shape plus words, never colour alone. */}
        <span aria-hidden>{ok ? '●' : '○'}</span>
        {ok ? okText : offText}
      </span>
    </div>
  );
}
