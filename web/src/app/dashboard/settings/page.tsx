'use client';

import { useState } from 'react';
import { useAuth } from '@/lib/auth-context';
import { useLinkedPatients, generateLinkingCode } from '@/lib/careloop-service';
import { isFirebaseConfigured } from '@/lib/firebase';

/**
 * Settings: linking, account, and an honest account of what is connected.
 *
 * The linking flow is the interesting part. A code that grants read access to
 * someone's health record is generated here, read aloud over the phone, and typed
 * into the elder's device. The alphabet excludes O, 0, I, 1 and L because
 * somebody is going to read this to a 78-year-old and "was that an O or a zero"
 * is a real failure, not a hypothetical one.
 */
export default function SettingsPage() {
  const { displayName, isDemo, signOut } = useAuth();
  const { patients } = useLinkedPatients();
  const patient = patients[0];

  const [code, setCode] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  async function handleGenerate() {
    if (!patient) return;
    setGenerating(true);
    const result = await generateLinkingCode(patient.id);
    setCode(result?.code ?? 'DEMO-MODE');
    setGenerating(false);
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
        <h3 className="font-display text-xl text-ink">Connect a phone</h3>
        <p className="mt-3 max-w-xl leading-relaxed text-slate-ink">
          To start receiving check-ins, generate a code and read it to{' '}
          {patient?.firstName ?? 'your parent'} over the phone. They type it into
          CareLoop on their own device, once.
        </p>

        {code ? (
          <div className="mt-6 rounded-2xl bg-navy px-7 py-6 text-center">
            <p className="text-xs tracking-[0.16em] text-white/50 uppercase">
              Read this out
            </p>
            <p className="mt-3 font-display text-4xl tracking-[0.25em] text-gold">
              {code}
            </p>
            <p className="mt-4 text-sm text-white/60">
              Valid for 24 hours, and it can only be used once.
            </p>
          </div>
        ) : (
          <button
            onClick={handleGenerate}
            disabled={generating}
            className="mt-6 rounded-xl bg-navy px-6 py-3.5 font-semibold text-white transition hover:bg-navy-soft disabled:opacity-70"
          >
            {generating ? 'Generating…' : 'Generate a linking code'}
          </button>
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
