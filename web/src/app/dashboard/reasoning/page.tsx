'use client';

import { useState } from 'react';
import { useLinkedPatients, useEscalations, acknowledgeEscalation } from '@/lib/careloop-service';
import { confidencePhrase, type Escalation } from '@/lib/demo-data';

/**
 * "Why Cara called."
 *
 * The most important page in the dashboard, because it is where trust is either
 * earned or lost. An agent that acts autonomously on someone's health and cannot
 * show its working is just a black box making decisions about your mother.
 *
 * Three rules from explainability research shape it:
 *   - at most three reasons, each tied to specific evidence
 *   - confidence in words, never a percentage
 *   - what was rejected is shown too, because that is what distinguishes a
 *     judgement from a threshold being crossed
 */
export default function ReasoningPage() {
  const { patients } = useLinkedPatients();
  const patientId = patients[0]?.id ?? '';
  const { data: escalations } = useEscalations(patientId);

  if (!escalations.length) {
    return (
      <div className="rounded-3xl border border-ink/10 bg-white p-12 text-center">
        <h2 className="font-display text-2xl text-ink">Cara has not needed to get in touch</h2>
        <p className="mx-auto mt-3 max-w-md leading-relaxed text-slate-ink">
          When she does, you will find the full reasoning here: what she noticed,
          which day she noticed it, and what she considered before deciding to tell you.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="font-display text-3xl text-ink">Why Cara got in touch</h2>
        <p className="mt-2 max-w-2xl leading-relaxed text-slate-ink">
          Every decision Cara makes on her own is written down here, with the
          evidence behind it and the options she weighed against.
        </p>
      </div>

      {escalations.map((escalation) => (
        <EscalationCard
          key={escalation.id}
          escalation={escalation}
          patientId={patientId}
        />
      ))}
    </div>
  );
}

function EscalationCard({
  escalation,
  patientId,
}: {
  escalation: Escalation;
  patientId: string;
}) {
  const [acknowledged, setAcknowledged] = useState(escalation.acknowledged);
  const [showAlternatives, setShowAlternatives] = useState(false);

  async function handleAcknowledge() {
    setAcknowledged(true);
    await acknowledgeEscalation(patientId, escalation.id);
  }

  const isUrgent = escalation.severity === 'urgent';
  const isConcern = escalation.severity === 'concern';

  return (
    <article className="overflow-hidden rounded-3xl border border-ink/10 bg-white">
      <div className="border-b border-ink/10 bg-navy px-8 py-7 md:px-10">
        <div className="flex flex-wrap items-center gap-3">
          <span
            className={`rounded-full px-3 py-1 text-xs font-semibold tracking-wide uppercase ${
              isUrgent
                ? 'bg-urgent-surface text-urgent'
                : isConcern
                  ? 'bg-concern-surface text-concern'
                  : 'bg-white/10 text-white/70'
            }`}
          >
            {isUrgent ? 'Urgent' : isConcern ? 'Needs attention' : 'Worth knowing'}
          </span>
          <span className="text-sm text-white/50">{escalation.raisedAt}</span>
          {acknowledged && (
            <span className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/60">
              Seen
            </span>
          )}
        </div>
        <h3 className="mt-4 font-display text-2xl leading-snug text-white md:text-3xl">
          {escalation.headline}
        </h3>
      </div>

      <div className="grid gap-10 px-8 py-9 md:grid-cols-5 md:px-10">
        <div className="md:col-span-3">
          <p className="text-xs font-semibold tracking-[0.16em] text-slate-ink uppercase">
            What Cara said
          </p>
          <div className="mt-5 space-y-4">
            {escalation.explanation.split('\n\n').map((para, i) => (
              <p key={i} className="leading-relaxed text-ink">
                {para}
              </p>
            ))}
          </div>

          {escalation.elderResponse === 'confirmed' && (
            <div className="mt-8 rounded-2xl bg-good-surface px-5 py-4">
              <p className="text-sm font-semibold text-good">She confirmed this</p>
              <p className="mt-1 text-sm leading-relaxed text-ink/70">
                She saw the same message in her own app and agreed with it.
              </p>
            </div>
          )}

          {escalation.elderResponse === 'disputed' && escalation.elderNote && (
            <div className="mt-8 rounded-2xl bg-concern-surface px-5 py-4">
              <p className="text-sm font-semibold text-concern">She added something</p>
              <p className="mt-2 leading-relaxed text-ink">
                &ldquo;{escalation.elderNote}&rdquo;
              </p>
            </div>
          )}

          {!acknowledged && (
            <button
              onClick={handleAcknowledge}
              className="mt-8 rounded-xl border border-ink/20 px-5 py-3 text-sm font-semibold text-ink transition hover:bg-cloud"
            >
              Mark as seen
            </button>
          )}
        </div>

        <div className="md:col-span-2">
          <p className="text-xs font-semibold tracking-[0.16em] text-slate-ink uppercase">
            How she got there
          </p>

          <ol className="mt-5 space-y-5">
            {escalation.reasoning.slice(0, 3).map((step, i) => (
              <li key={i} className="relative pl-8">
                <span className="absolute top-0.5 left-0 flex h-5 w-5 items-center justify-center rounded-full bg-navy text-[11px] font-semibold text-white">
                  {i + 1}
                </span>
                <p className="font-medium text-ink">{step.observation}</p>
                <p className="mt-1 text-sm text-slate-ink">{step.evidence}</p>
              </li>
            ))}
          </ol>

          <div className="mt-7 border-t border-ink/10 pt-5">
            <p className="text-sm text-slate-ink">
              Cara&apos;s confidence:{' '}
              <span className="font-semibold text-ink">
                {confidencePhrase[escalation.confidence]}
              </span>
            </p>
          </div>

          {escalation.alternativesConsidered.length > 0 && (
            <div className="mt-5">
              <button
                onClick={() => setShowAlternatives((v) => !v)}
                className="text-sm font-semibold text-navy underline decoration-gold decoration-2 underline-offset-4"
                aria-expanded={showAlternatives}
              >
                What she considered instead
              </button>

              {showAlternatives && (
                <ul className="mt-4 space-y-3">
                  {escalation.alternativesConsidered.map((alt, i) => (
                    <li
                      key={i}
                      className="rounded-xl bg-cloud px-4 py-3 text-sm leading-relaxed text-ink/80"
                    >
                      {alt}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>
      </div>
    </article>
  );
}
