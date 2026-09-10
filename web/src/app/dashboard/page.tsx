'use client';

import Link from 'next/link';
import { useLinkedPatients, useCheckIns, useEscalations, useMedications } from '@/lib/careloop-service';
import { confidencePhrase } from '@/lib/demo-data';

/**
 * Overview.
 *
 * Answers one question in the first screenful: is she alright today? Everything
 * else is a link to somewhere that goes deeper. The temptation with a dashboard
 * is to show everything at once; for a person checking on a parent between
 * meetings, that is noise.
 */
export default function DashboardOverview() {
  const { patients } = useLinkedPatients();
  const patient = patients[0];
  const patientId = patient?.id ?? '';

  const { data: checkIns } = useCheckIns(patientId);
  const { data: escalations } = useEscalations(patientId);
  const { data: medications } = useMedications(patientId);

  const today = checkIns[0];
  const openEscalations = escalations.filter((e) => !e.acknowledged);
  const topEscalation = openEscalations[0] ?? escalations[0];
  const missedDays = checkIns.filter((c) => (c.missed?.length ?? 0) > 0).length;
  const lowest = [...medications].sort(
    (a, b) => (a.daysRemaining ?? 999) - (b.daysRemaining ?? 999),
  )[0];

  return (
    <div className="space-y-8">
      {/* ------------------------------------------------------ Headline */}
      <section className="rounded-3xl border border-ink/10 bg-white p-8 md:p-10">
        <p className="text-xs font-semibold tracking-[0.16em] text-slate-ink uppercase">
          Today
        </p>
        <h2 className="mt-4 font-display text-3xl leading-tight text-ink md:text-4xl">
          {today
            ? (today.missed?.length ?? 0) === 0
              ? `${patient?.firstName} is doing well today.`
              : `${patient?.firstName} missed something today.`
            : `Cara has not called ${patient?.firstName} yet today.`}
        </h2>

        {today && (
          <>
            <p className="mt-4 max-w-2xl text-lg leading-relaxed text-slate-ink">
              {today.caraSummary}
            </p>
            <p className="mt-3 text-sm text-slate-ink">
              Checked in at {today.time}, call lasted{' '}
              {Math.floor(today.durationSeconds / 60)} min {today.durationSeconds % 60}s
            </p>
          </>
        )}
      </section>

      {/* --------------------------------------------------------- Stats */}
      <section className="grid gap-4 sm:grid-cols-3">
        <StatCard
          label="Medications taken today"
          value={`${today?.confirmed?.length ?? 0} of ${medications.length}`}
          href="/dashboard/health"
        />
        <StatCard
          label="Days with a missed dose"
          value={`${missedDays} this week`}
          href="/dashboard/calls"
        />
        <StatCard
          label={lowest ? `${lowest.name} supply` : 'Supply'}
          value={lowest ? `${lowest.daysRemaining} days left` : 'Fine'}
          href="/dashboard/health"
        />
      </section>

      {/* ----------------------------------------------------- Reasoning */}
      {topEscalation && (
        <section className="rounded-3xl border border-ink/10 bg-white p-8 md:p-10">
          <div className="flex flex-wrap items-center gap-3">
            <span
              className={`rounded-full px-3 py-1 text-xs font-semibold tracking-wide uppercase ${
                topEscalation.severity === 'urgent'
                  ? 'bg-urgent-surface text-urgent'
                  : topEscalation.severity === 'concern'
                    ? 'bg-concern-surface text-concern'
                    : 'bg-cloud text-slate-ink'
              }`}
            >
              {topEscalation.severity === 'fyi' ? 'Worth knowing' : 'Needs attention'}
            </span>
            <span className="text-sm text-slate-ink">{topEscalation.raisedAt}</span>
          </div>

          <h3 className="mt-4 font-display text-2xl text-ink">
            {topEscalation.headline}
          </h3>

          <p className="mt-4 max-w-3xl leading-relaxed text-slate-ink">
            {topEscalation.explanation.split('\n\n')[0]}
          </p>

          <div className="mt-6 flex flex-wrap items-center gap-4">
            <Link
              href="/dashboard/reasoning"
              className="rounded-xl bg-navy px-5 py-3 text-sm font-semibold text-white transition hover:bg-navy-soft"
            >
              See how Cara got there
            </Link>
            <span className="text-sm text-slate-ink">
              Cara&apos;s confidence:{' '}
              <span className="font-semibold text-ink">
                {confidencePhrase[topEscalation.confidence]}
              </span>
            </span>
          </div>
        </section>
      )}

      {/* -------------------------------------------------- Recent calls */}
      <section>
        <div className="flex items-baseline justify-between">
          <h3 className="font-display text-2xl text-ink">Recent check-ins</h3>
          <Link
            href="/dashboard/calls"
            className="text-sm font-medium text-navy underline decoration-gold decoration-2 underline-offset-4"
          >
            See all
          </Link>
        </div>

        <div className="mt-5 overflow-hidden rounded-3xl border border-ink/10 bg-white">
          {checkIns.slice(0, 3).map((checkIn, i) => (
            <div
              key={checkIn.id}
              className={`flex flex-col gap-2 p-6 sm:flex-row sm:gap-8 ${
                i > 0 ? 'border-t border-ink/10' : ''
              }`}
            >
              <div className="w-28 shrink-0">
                <p className="font-semibold text-ink">{checkIn.label}</p>
                <p className="text-sm text-slate-ink">{checkIn.time}</p>
              </div>
              <p className="flex-1 leading-relaxed text-slate-ink">
                {checkIn.caraSummary}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function StatCard({
  label,
  value,
  href,
}: {
  label: string;
  value: string;
  href: string;
}) {
  return (
    <Link
      href={href}
      className="group rounded-2xl border border-ink/10 bg-white px-6 py-5 transition hover:border-gold hover:shadow-sm"
    >
      <p className="text-sm text-slate-ink">{label}</p>
      <p className="mt-2 font-display text-2xl text-ink">{value}</p>
    </Link>
  );
}
