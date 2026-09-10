'use client';

import { useLinkedPatients, useMedications } from '@/lib/careloop-service';
import BloodSugarChart from '@/components/dashboard/BloodSugarChart';

/**
 * Health: readings and medications.
 *
 * The chart gets a plain-language reading underneath it, in Cara's voice. A trend
 * line tells you what happened; it does not tell you whether to worry, and that
 * is the actual question. Interpreting it is the product.
 */
export default function HealthPage() {
  const { patients } = useLinkedPatients();
  const patientId = patients[0]?.id ?? '';
  const { data: medications } = useMedications(patientId);

  const needsRefill = medications.filter((m) => (m.daysRemaining ?? 999) <= 10);

  return (
    <div className="space-y-8">
      <div>
        <h2 className="font-display text-3xl text-ink">Health</h2>
        <p className="mt-2 leading-relaxed text-slate-ink">
          Readings taken during check-ins, and what she is currently taking.
        </p>
      </div>

      {needsRefill.length > 0 && (
        <section className="rounded-3xl border border-concern/25 bg-concern-surface p-7 md:p-8">
          <p className="text-xs font-semibold tracking-[0.16em] text-concern uppercase">
            Running low
          </p>
          <h3 className="mt-3 font-display text-xl text-ink">
            {needsRefill[0]!.name} runs out in about {needsRefill[0]!.daysRemaining} days
          </h3>
          <p className="mt-3 max-w-2xl leading-relaxed text-ink/75">
            Repeat prescriptions usually take a few working days, so it is worth
            starting now rather than waiting until it is gone.
          </p>
        </section>
      )}

      <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
        <h3 className="font-display text-2xl text-ink">Blood sugar, last 14 days</h3>
        <p className="mt-2 text-sm text-slate-ink">
          The shaded band is her normal range, 4.0 to 7.8 mmol/L.
        </p>
        <div className="mt-6">
          <BloodSugarChart />
        </div>
        <p className="mt-5 rounded-2xl bg-cloud px-5 py-4 text-sm leading-relaxed text-ink/80">
          <strong className="font-semibold">Cara:</strong> Her readings drifted above
          the usual range mid-week and have come back down over the last two days.
          Worth keeping an eye on, not worth worrying about yet.
        </p>
      </section>

      <section>
        <h3 className="font-display text-2xl text-ink">Medications</h3>
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          {medications.map((med) => (
            <div
              key={med.id}
              className="rounded-2xl border border-ink/10 bg-white p-6"
            >
              <div className="flex items-baseline justify-between gap-4">
                <h4 className="text-lg font-semibold text-ink">{med.name}</h4>
                <span className="text-sm text-slate-ink">{med.dose}</span>
              </div>
              <p className="mt-2 leading-relaxed text-slate-ink">{med.purpose}</p>

              <div className="mt-4 flex flex-wrap items-center gap-3">
                {med.criticality === 'critical' && (
                  <span className="rounded-full bg-cloud px-3 py-1 text-xs font-semibold text-gold-ink">
                    Cara watches this one closely
                  </span>
                )}
                <span className="text-xs text-slate-ink">
                  {med.daysRemaining} days of supply left
                </span>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
