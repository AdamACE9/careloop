import type { Metadata } from "next";
import SiteHeader from "@/components/SiteHeader";
import ReasoningCard from "@/components/ReasoningCard";
import BloodSugarChart from "@/components/dashboard/BloodSugarChart";
import CheckNowButton from "@/components/dashboard/CheckNowButton";
import Reveal from "@/components/Reveal";
import {
  elder,
  caretaker,
  checkIns,
  medications,
  refillEscalation,
  confidencePhrase,
} from "@/lib/demo-data";

export const metadata: Metadata = {
  title: "Margaret — CareLoop",
  description: "Today's check-in, medication history, and Cara's reasoning.",
};

/**
 * The caretaker dashboard.
 *
 * Designed to be **scannable in about five seconds** by a busy adult child between other
 * things. That drove the ordering: the single most important fact — is she alright today —
 * is the largest element and appears before anything else. Detail is available below it,
 * never in front of it.
 *
 * The reasoning card sits high rather than buried in a log, because "why did it tell me
 * this" is the question that determines whether the family trusts the system at all.
 */
export default function DashboardPage() {
  const today = checkIns[0];
  const missedThisWeek = checkIns.filter((c) => c.missed.length > 0).length;

  return (
    <main className="min-h-screen bg-bone">
      <SiteHeader />

      {/* ------------------------------------------------------------ Status */}
      <section className="grain relative overflow-hidden bg-navy-deep">
        <div className="relative mx-auto max-w-7xl px-6 py-14 md:px-10 md:py-16">
          <div className="flex flex-col gap-8 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-sm tracking-[0.16em] text-gold uppercase">
                {caretaker.relationship} view
              </p>
              <h1 className="mt-4 font-display text-4xl text-white md:text-5xl">
                {elder.firstName} is doing well today.
              </h1>
              <p className="mt-4 max-w-xl leading-relaxed text-white/65">
                {today.caraSummary}
              </p>
              <p className="mt-3 text-sm text-white/45">
                Checked in at {today.time} · call lasted{" "}
                {Math.round(today.durationSeconds / 60)} min{" "}
                {today.durationSeconds % 60}s
              </p>
            </div>

            <CheckNowButton />
          </div>

          {/* Quick stats. Numbers a caretaker actually wants, not vanity metrics. */}
          <div className="mt-12 grid gap-4 sm:grid-cols-3">
            <StatTile
              label="Medications taken today"
              value={`${today.confirmed.length} of ${medications.length}`}
            />
            <StatTile
              label="Days with a missed dose"
              value={`${missedThisWeek} this week`}
            />
            <StatTile
              label="Warfarin supply"
              value={`${medications[0].daysRemaining} days left`}
            />
          </div>
        </div>
      </section>

      {/* --------------------------------------------------------- Reasoning */}
      <section className="mx-auto max-w-7xl px-6 py-16 md:px-10">
        <Reveal>
          <div className="flex items-baseline justify-between gap-6">
            <h2 className="font-display text-3xl text-ink">
              Why Cara got in touch
            </h2>
            <span className="text-sm text-slate-ink">1 open · 1 for information</span>
          </div>
        </Reveal>

        <Reveal delay={80}>
          <div className="mt-8">
            <ReasoningCard />
          </div>
        </Reveal>

        {/* Lower-severity item, deliberately quieter than the one above it. */}
        <Reveal delay={140}>
          <div className="mt-6 rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
            <div className="flex flex-wrap items-center gap-3">
              <span className="rounded-full bg-cloud px-3 py-1 text-xs font-semibold tracking-wide text-slate-ink uppercase">
                Worth knowing
              </span>
              <span className="text-sm text-slate-ink">
                {refillEscalation.raisedAt}
              </span>
            </div>
            <h3 className="mt-4 text-xl font-semibold text-ink">
              {refillEscalation.headline}
            </h3>
            <p className="mt-3 max-w-3xl leading-relaxed text-slate-ink">
              {refillEscalation.explanation}
            </p>
            <p className="mt-5 text-sm text-slate-ink">
              Cara&apos;s confidence:{" "}
              <span className="font-semibold text-ink">
                {confidencePhrase[refillEscalation.confidence]}
              </span>
            </p>
          </div>
        </Reveal>
      </section>

      {/* ------------------------------------------------------------ Vitals */}
      <section className="mx-auto max-w-7xl px-6 pb-16 md:px-10">
        <div className="grid gap-6 lg:grid-cols-3">
          <Reveal className="lg:col-span-2">
            <div className="h-full rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
              <h2 className="font-display text-2xl text-ink">
                Blood sugar, last 14 days
              </h2>
              <p className="mt-2 text-sm text-slate-ink">
                The shaded band is her normal range (4.0–7.8 mmol/L).
              </p>
              <div className="mt-6">
                <BloodSugarChart />
              </div>
              <p className="mt-5 rounded-2xl bg-cloud px-5 py-4 text-sm leading-relaxed text-ink/80">
                <strong className="font-semibold">Cara:</strong> Her readings drifted
                above the usual range mid-week and have come back down over the last two
                days. Worth keeping an eye on, not worth worrying about yet.
              </p>
            </div>
          </Reveal>

          <Reveal delay={90}>
            <div className="h-full rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
              <h2 className="font-display text-2xl text-ink">Medications</h2>
              <ul className="mt-6 space-y-5">
                {medications.map((med) => (
                  <li key={med.id}>
                    <div className="flex items-baseline justify-between gap-4">
                      <span className="font-semibold text-ink">{med.name}</span>
                      <span className="text-sm text-slate-ink">{med.dose}</span>
                    </div>
                    <p className="mt-1 text-sm text-slate-ink">{med.purpose}</p>
                    {med.criticality === "critical" && (
                      <p className="mt-1.5 text-xs font-semibold text-gold-ink">
                        Cara watches this one closely
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>
        </div>
      </section>

      {/* ----------------------------------------------------------- History */}
      <section className="mx-auto max-w-7xl px-6 pb-24 md:px-10">
        <Reveal>
          <h2 className="font-display text-3xl text-ink">Check-in history</h2>
        </Reveal>

        <div className="mt-8 overflow-hidden rounded-3xl border border-ink/10 bg-white">
          {checkIns.map((c, i) => (
            <div
              key={c.id}
              className={`flex flex-col gap-3 p-6 md:flex-row md:items-start md:gap-8 md:p-7 ${
                i > 0 ? "border-t border-ink/10" : ""
              }`}
            >
              <div className="w-32 shrink-0">
                <p className="font-semibold text-ink">{c.label}</p>
                <p className="text-sm text-slate-ink">{c.time}</p>
              </div>

              <div className="flex-1">
                {/* Status carries an icon glyph + words, never colour alone. */}
                <StatusBadge status={c.status} />
                <p className="mt-3 leading-relaxed text-slate-ink">
                  {c.caraSummary}
                </p>
                {c.missed.length > 0 && (
                  <p className="mt-2 text-sm text-ink">
                    Missed: <span className="font-medium">{c.missed.join(", ")}</span>
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.04] px-6 py-5">
      <p className="text-sm text-white/50">{label}</p>
      <p className="mt-2 font-display text-2xl text-white">{value}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; glyph: string; className: string }> = {
    completed: {
      label: "All taken",
      glyph: "✓",
      className: "bg-good-surface text-good",
    },
    missed_dose: {
      label: "A dose was missed",
      glyph: "!",
      className: "bg-concern-surface text-concern",
    },
    escalated: {
      label: "Cara got in touch",
      glyph: "→",
      className: "bg-concern-surface text-concern",
    },
    no_answer: {
      label: "No answer",
      glyph: "–",
      className: "bg-cloud text-slate-ink",
    },
  };

  const s = map[status] ?? map.no_answer;

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${s.className}`}
    >
      <span aria-hidden>{s.glyph}</span>
      {s.label}
    </span>
  );
}
