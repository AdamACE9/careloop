'use client';

import { useState } from 'react';
import { requestCheckInCall } from '@/lib/careloop-service';

type State =
  | { kind: 'idle' }
  | { kind: 'sending' }
  | { kind: 'sent'; demo: boolean }
  | { kind: 'failed'; reason: string };

/**
 * Manual "check on her now".
 *
 * Hits the same Cloud Function the scheduler uses, so this exercises the real
 * delivery path rather than a demo shortcut. If the button worked and the
 * scheduled path did not, we would not find out until it mattered.
 *
 * ## Why the copy is about her phone, not our system
 *
 * Confirmation says "her phone is ringing", not "request queued". The person
 * pressing this is picturing their mother's kitchen, not a message bus.
 *
 * It also sets an honest expectation that she may not answer straight away, and
 * that Cara decides whether to try again. Promising an instant answer would make
 * a completely normal outcome feel like a failure, and would misrepresent what
 * the agent actually does next.
 *
 * Failures say what happened in human terms. "Her phone appears to be off" is
 * actionable; a Firebase error code is not.
 */
export default function CheckNowButton({
  patientId,
  patientName,
}: {
  patientId: string;
  patientName: string;
}) {
  const [state, setState] = useState<State>({ kind: 'idle' });

  async function handleClick() {
    setState({ kind: 'sending' });
    const result = await requestCheckInCall(patientId);

    if (result.ok) {
      setState({ kind: 'sent', demo: result.demo });
      window.setTimeout(() => setState({ kind: 'idle' }), 8000);
    } else {
      setState({ kind: 'failed', reason: result.reason });
      window.setTimeout(() => setState({ kind: 'idle' }), 8000);
    }
  }

  const label =
    state.kind === 'sending'
      ? 'Calling…'
      : state.kind === 'sent'
        ? `Cara is calling ${patientName}`
        : 'Check on her now';

  return (
    <div className="flex flex-col items-start gap-2 sm:items-end">
      <button
        onClick={handleClick}
        disabled={state.kind === 'sending' || state.kind === 'sent'}
        className="w-full rounded-2xl bg-navy px-7 py-3.5 font-semibold text-white transition hover:bg-navy-soft disabled:cursor-not-allowed disabled:opacity-70 sm:w-auto"
      >
        {label}
      </button>

      {state.kind === 'sent' && (
        <p className="max-w-xs text-sm leading-relaxed text-slate-ink sm:text-right">
          {state.demo
            ? 'Demo mode, so no real call was sent. With a linked phone this rings within seconds.'
            : `Her phone is ringing now. If she does not pick up, Cara decides whether to try again shortly.`}
        </p>
      )}

      {state.kind === 'failed' && (
        <p className="max-w-xs text-sm leading-relaxed text-urgent sm:text-right">
          {state.reason}
        </p>
      )}
    </div>
  );
}
