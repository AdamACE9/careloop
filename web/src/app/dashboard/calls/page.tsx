'use client';

import { useState } from 'react';
import { useLinkedPatients, useCheckIns } from '@/lib/careloop-service';
import type { AgentDecision } from '@/lib/demo-data';

/**
 * Check-in history.
 *
 * Deliberately readable as a story rather than a table. A caretaker scanning this
 * is looking for "is this getting better or worse", which a list of prose
 * summaries answers far better than a grid of ticks and crosses.
 *
 * Status is never carried by colour alone. Every row states its outcome in words,
 * because a red dot means nothing to someone who has not learned the legend.
 */
export default function CallsPage() {
  const { patients } = useLinkedPatients();
  const patient = patients[0];
  const patientId = patient?.id ?? '';
  const { data: checkIns, loading } = useCheckIns(patientId);
  const [filter, setFilter] = useState<'all' | 'missed'>('all');

  const visible = filter === 'all'
    ? checkIns
    : checkIns.filter((c) => (c.missed?.length ?? 0) > 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="font-display text-3xl text-ink">Check-ins</h2>
          <p className="mt-2 leading-relaxed text-slate-ink">
            Every call Cara has made, and what came of it.
            {checkIns.length > 0 && (
              <>
                {' '}
                Showing {visible.length} of {checkIns.length}.
              </>
            )}
          </p>
        </div>

        <div className="flex gap-1 rounded-xl bg-cloud p-1">
          {(['all', 'missed'] as const).map((option) => (
            <button
              key={option}
              onClick={() => setFilter(option)}
              className={`rounded-lg px-4 py-2 text-sm font-medium transition ${
                filter === option
                  ? 'bg-white text-ink shadow-sm'
                  : 'text-slate-ink hover:text-ink'
              }`}
            >
              {option === 'all' ? 'All' : 'With a missed dose'}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-28 animate-pulse rounded-3xl bg-cloud" />
          ))}
        </div>
      ) : visible.length === 0 ? (
        // Three different nothings, and telling them apart matters. "Nothing
        // missed" shown to someone whose first call has not happened yet reads
        // as a clean bill of health for a week that never took place.
        <div className="rounded-3xl border border-ink/10 bg-white p-12 text-center">
          {checkIns.length === 0 ? (
            <>
              <p className="font-display text-xl text-ink">No check-ins yet</p>
              <p className="mx-auto mt-2 max-w-md leading-relaxed text-slate-ink">
                Cara calls at {patient?.checkInTime ?? 'the scheduled time'} each
                day. The first one will appear here once it has happened, or you
                can ask her to call now from the overview.
              </p>
            </>
          ) : (
            <>
              <p className="font-display text-xl text-ink">Nothing missed</p>
              <p className="mt-2 text-slate-ink">
                Every medication was taken across{' '}
                {checkIns.length === 1 ? 'this check-in' : `these ${checkIns.length} check-ins`}.
              </p>
            </>
          )}
        </div>
      ) : (
        <div className="overflow-hidden rounded-3xl border border-ink/10 bg-white">
          {visible.map((checkIn, i) => (
            <div
              key={checkIn.id}
              className={`flex flex-col gap-4 p-6 md:flex-row md:gap-8 md:p-7 ${
                i > 0 ? 'border-t border-ink/10' : ''
              }`}
            >
              <div className="w-32 shrink-0">
                <p className="font-semibold text-ink">{checkIn.label}</p>
                <p className="text-sm text-slate-ink">{checkIn.time}</p>
                <p className="mt-1 text-xs text-slate-ink">
                  {Math.floor(checkIn.durationSeconds / 60)}m {checkIn.durationSeconds % 60}s
                </p>
              </div>

              <div className="flex-1">
                <StatusChip
                  status={checkIn.status}
                  establishedNothing={
                    checkIn.confirmed.length === 0 && checkIn.missed.length === 0
                  }
                />
                <p className="mt-3 leading-relaxed text-slate-ink">{checkIn.caraSummary}</p>

                {checkIn.conversationSummary && (
                  <p className="mt-2 leading-relaxed text-ink">{checkIn.conversationSummary}</p>
                )}

                {checkIn.agentDecision && <DecisionNote decision={checkIn.agentDecision} />}

                {(checkIn.missed?.length ?? 0) > 0 && (
                  <p className="mt-3 text-sm text-ink">
                    Missed:{' '}
                    <span className="font-medium">{checkIn.missed.join(', ')}</span>
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * What Cara decided after the call, in her own words, with how close it was.
 *
 * This exists because deciding NOT to tell the family is a decision too, and
 * it used to leave nothing behind. Someone who said "no, I didn't take it" on
 * a call saw no sign anywhere that it had been weighed. The score is shown
 * against the threshold so a reader can see the judgement rather than take it
 * on trust.
 */
function DecisionNote({ decision }: { decision: AgentDecision }) {
  const escalated = decision.action === 'escalate';
  const follow = decision.followUpAt ? new Date(decision.followUpAt) : null;
  return (
    <div
      className={`mt-4 rounded-2xl px-5 py-4 ${
        escalated ? 'bg-concern-surface' : 'border border-ink/10 bg-cloud/60'
      }`}
    >
      <p className={`text-sm font-semibold ${escalated ? 'text-concern' : 'text-ink'}`}>
        {escalated ? 'Cara told you about this' : "Cara's decision"}: {decision.headline}
      </p>
      <p className="mt-1.5 text-sm leading-relaxed text-ink/80">{decision.explanation}</p>
      <p className="mt-2 text-xs text-slate-ink">
        Concern {decision.concernScore.toFixed(2)} of the {decision.threshold.toFixed(1)} at
        which she tells family
        {follow && Number.isFinite(follow.getTime())
          ? ` · calling back around ${follow.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}`
          : ''}
      </p>
    </div>
  );
}

/**
 * `establishedNothing` is not a status, it is a reading of the check-in.
 *
 * A completed call where nothing was confirmed and nothing was missed is not a
 * day everything was taken; it is a day nothing was established. Showing "All
 * taken" for it put a claim in front of the family that nobody had made.
 */
function StatusChip({
  status,
  establishedNothing = false,
}: {
  status: string;
  establishedNothing?: boolean;
}) {
  if (status === 'completed' && establishedNothing) {
    return (
      <span className="inline-flex items-center gap-2 rounded-full bg-cloud px-3 py-1 text-xs font-semibold text-slate-ink">
        <span aria-hidden>–</span>
        Medications not covered
      </span>
    );
  }

  const map: Record<string, { label: string; glyph: string; className: string }> = {
    completed: { label: 'All taken', glyph: '✓', className: 'bg-good-surface text-good' },
    missed_dose: {
      label: 'A dose was missed',
      glyph: '!',
      className: 'bg-concern-surface text-concern',
    },
    escalated: {
      label: 'Cara got in touch',
      glyph: '→',
      className: 'bg-concern-surface text-concern',
    },
    no_answer: { label: 'No answer', glyph: '–', className: 'bg-cloud text-slate-ink' },
  };
  const s = map[status] ?? map.no_answer!;

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${s.className}`}
    >
      <span aria-hidden>{s.glyph}</span>
      {s.label}
    </span>
  );
}
