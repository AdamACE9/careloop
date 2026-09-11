'use client';

import { useState } from 'react';
import type { AgentThread } from '@/lib/demo-data';

/**
 * "What Cara is keeping an eye on."
 *
 * This is the clearest evidence in the product that something is actually
 * reasoning rather than running a script. Everything else the agent does happens
 * inside one call: it hears something, looks it up, answers, and the episode
 * ends. A thread is an intention that outlives the episode. Cara opens one
 * herself, chooses how long to leave it, raises it days later, and closes it
 * when it is genuinely settled.
 *
 * Two deliberate choices about what is shown:
 *
 * **Her reason is shown verbatim, not summarised.** "She mentioned it twice
 * without my asking" is the thing that makes this feel like attention rather
 * than logging. Paraphrasing it into "knee pain noted" throws away the only part
 * that demonstrates judgement.
 *
 * **Resolved threads stay.** The instinct is to hide them, but they are most of
 * what makes the open ones credible: they show the agent deciding something is
 * finished, which is the half of memory that separates reasoning from
 * accumulating. An agent that never closes anything is just a growing list.
 */
export default function AgentThreads({ threads }: { threads: AgentThread[] }) {
  const [showResolved, setShowResolved] = useState(false);

  const open = threads.filter((t) => t.status === 'open');
  const resolved = threads.filter((t) => t.status === 'resolved');

  return (
    <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-9">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="font-display text-2xl text-ink">What Cara is keeping an eye on</h2>
          <p className="mt-1.5 max-w-xl text-sm leading-relaxed text-slate-ink">
            Things she decided herself were worth coming back to. She chooses when
            to raise them again, and she closes them when they are settled.
          </p>
        </div>

        {resolved.length > 0 && (
          <button
            type="button"
            onClick={() => setShowResolved((v) => !v)}
            className="rounded-xl border border-ink/15 px-4 py-2 text-sm font-medium text-ink transition-colors hover:bg-ink/5"
          >
            {showResolved ? 'Hide' : 'Show'} {resolved.length} settled
          </button>
        )}
      </div>

      {open.length === 0 ? (
        <p className="mt-6 rounded-2xl bg-bone px-5 py-4 text-sm leading-relaxed text-slate-ink">
          Nothing outstanding at the moment. When something comes up on a call
          that deserves another look, it will appear here.
        </p>
      ) : (
        <ul className="mt-6 space-y-3">
          {open.map((thread) => (
            <ThreadCard key={thread.id} thread={thread} />
          ))}
        </ul>
      )}

      {showResolved && resolved.length > 0 && (
        <ul className="mt-3 space-y-3">
          {resolved.map((thread) => (
            <ThreadCard key={thread.id} thread={thread} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ThreadCard({ thread }: { thread: AgentThread }) {
  const isOpen = thread.status === 'open';

  return (
    <li
      className={`rounded-2xl border p-5 ${
        isOpen ? 'border-gold/35 bg-gold/[0.06]' : 'border-ink/10 bg-bone'
      }`}
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${
            isOpen ? 'bg-gold' : 'bg-good'
          }`}
        />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
            <h3 className="font-medium text-ink">{thread.topic}</h3>
            {/* Status as a word, never colour alone. */}
            <span
              className={`text-xs font-semibold uppercase tracking-wide ${
                isOpen ? 'text-gold-ink' : 'text-good'
              }`}
            >
              {isOpen ? 'Watching' : 'Settled'}
            </span>
          </div>

          <p className="mt-2 text-sm leading-relaxed text-slate-ink">
            <span className="text-ink/50">Why: </span>
            {thread.why}
          </p>

          {thread.resolution && (
            <p className="mt-2.5 rounded-xl bg-white px-4 py-3 text-sm leading-relaxed text-slate-ink">
              <span className="text-ink/50">How it ended: </span>
              {thread.resolution}
            </p>
          )}

          <p className="mt-3 text-xs text-ink/45">
            Noticed {formatDate(thread.raisedAt)}
            {isOpen && thread.timesRaised > 0 && (
              <> · raised again {thread.timesRaised === 1 ? 'once' : `${thread.timesRaised} times`} since</>
            )}
            {!isOpen && thread.resolvedAt && <> · settled {formatDate(thread.resolvedAt)}</>}
          </p>
        </div>
      </div>
    </li>
  );
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return 'recently';
  return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'long' });
}
