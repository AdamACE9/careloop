'use client';

import { useState } from 'react';
import { useLinkedPatients, useMedications, useVitals } from '@/lib/careloop-service';
import VitalsChart, { describeTrend } from '@/components/dashboard/VitalsChart';
import { vitalRanges, type VitalReading } from '@/lib/demo-data';

/**
 * Health: readings and medications.
 *
 * The chart gets a plain-language reading underneath it. A trend line tells you
 * what happened; it does not tell you whether to worry, and that is the actual
 * question. Interpreting it is the product.
 *
 * That sentence used to be a hardcoded paragraph describing a mid-week drift
 * that belonged to the example household, shown to everyone regardless of their
 * own numbers. It is now derived from the readings on screen, and omitted
 * entirely when there are too few to say anything honest.
 */

const TAB_ORDER: VitalReading['type'][] = [
  'blood_sugar',
  'blood_pressure',
  'heart_rate',
  'weight',
];

export default function HealthPage() {
  const { patients } = useLinkedPatients();
  const patient = patients[0];
  const patientId = patient?.id ?? '';

  const { data: medications, loading: medsLoading } = useMedications(patientId);
  const { data: vitals, loading: vitalsLoading } = useVitals(patientId);

  // Only offer a tab for a reading type she actually has. An empty "Weight" tab
  // is a dead end that implies the product lost something.
  const available = TAB_ORDER.filter((t) => vitals.some((v) => v.type === t));
  const [selected, setSelected] = useState<VitalReading['type'] | null>(null);
  const active = selected && available.includes(selected) ? selected : available[0];

  const needsRefill = medications
    .filter((m) => typeof m.daysRemaining === 'number' && m.daysRemaining <= 10)
    .sort((a, b) => (a.daysRemaining ?? 0) - (b.daysRemaining ?? 0));

  const name = patient?.preferredName || 'She';

  return (
    <div className="space-y-8">
      <div>
        <h2 className="font-display text-3xl text-ink">Health</h2>
        <p className="mt-2 leading-relaxed text-slate-ink">
          Readings taken during check-ins, and what {name} is currently taking.
        </p>
      </div>

      {needsRefill.length > 0 && <RefillNotice medications={needsRefill} />}

      <section className="rounded-3xl border border-ink/10 bg-white p-7 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h3 className="font-display text-2xl text-ink">
              {active ? vitalRanges[active].label : 'Readings'}, last 14 days
            </h3>
            {active && vitalRanges[active].min !== null && (
              <p className="mt-2 text-sm text-slate-ink">
                The shaded band is the usual range, {vitalRanges[active].min} to{' '}
                {vitalRanges[active].max} {vitalRanges[active].unit}.
              </p>
            )}
          </div>

          {available.length > 1 && (
            <div className="flex flex-wrap gap-1 rounded-xl bg-cloud p-1">
              {available.map((type) => (
                <button
                  key={type}
                  onClick={() => setSelected(type)}
                  aria-pressed={active === type}
                  className={`rounded-lg px-4 py-2 text-sm font-medium transition ${
                    active === type
                      ? 'bg-white text-ink shadow-sm'
                      : 'text-slate-ink hover:text-ink'
                  }`}
                >
                  {vitalRanges[type].label}
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="mt-6">
          {vitalsLoading ? (
            <div className="h-[300px] w-full animate-pulse rounded-2xl bg-cloud" />
          ) : active ? (
            <VitalsChart readings={vitals} type={active} />
          ) : (
            <div className="flex h-[300px] w-full flex-col items-center justify-center rounded-2xl bg-cloud px-6 text-center">
              <p className="font-display text-lg text-ink">No readings yet</p>
              <p className="mt-2 max-w-sm text-sm leading-relaxed text-slate-ink">
                Cara records a reading when {name} gives her one during a check-in,
                and {name} can enter one on the phone at any time.
              </p>
            </div>
          )}
        </div>

        {active && !vitalsLoading && <TrendNote readings={vitals} type={active} />}
      </section>

      <section>
        <h3 className="font-display text-2xl text-ink">Medications</h3>

        {medsLoading ? (
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            {[0, 1].map((i) => (
              <div key={i} className="h-40 animate-pulse rounded-2xl bg-cloud" />
            ))}
          </div>
        ) : medications.length === 0 ? (
          <div className="mt-5 rounded-3xl border border-ink/10 bg-white p-10 text-center">
            <p className="font-display text-xl text-ink">Nothing added yet</p>
            <p className="mx-auto mt-2 max-w-md leading-relaxed text-slate-ink">
              Medications are added on {name}&rsquo;s phone, so that the list Cara
              asks about is the list {name} agreed to. They will appear here as
              soon as they are added.
            </p>
          </div>
        ) : (
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            {medications.map((med) => (
              <MedicationCard key={med.id} med={med} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function TrendNote({
  readings,
  type,
}: {
  readings: VitalReading[];
  type: VitalReading['type'];
}) {
  const sentence = describeTrend(readings, type);
  // Below four readings there is nothing honest to say, so nothing is said.
  if (!sentence) return null;

  return (
    <p className="mt-5 rounded-2xl bg-cloud px-5 py-4 text-sm leading-relaxed text-ink/80">
      <strong className="font-semibold">Cara:</strong> {sentence}
    </p>
  );
}

function RefillNotice({
  medications,
}: {
  medications: { id: string; name: string; daysRemaining?: number }[];
}) {
  const first = medications[0]!;
  const rest = medications.slice(1);

  return (
    <section className="rounded-3xl border border-concern/25 bg-concern-surface p-7 md:p-8">
      <p className="text-xs font-semibold tracking-[0.16em] text-concern uppercase">
        Running low
      </p>
      <h3 className="mt-3 font-display text-xl text-ink">
        {first.name} runs out in about {first.daysRemaining} days
      </h3>
      {rest.length > 0 && (
        <p className="mt-2 text-sm text-ink/70">
          Also getting low: {rest.map((m) => `${m.name} (${m.daysRemaining} days)`).join(', ')}
        </p>
      )}
      <p className="mt-3 max-w-2xl leading-relaxed text-ink/75">
        Repeat prescriptions usually take a few working days, so it is worth
        starting now rather than waiting until it is gone.
      </p>
    </section>
  );
}

function MedicationCard({
  med,
}: {
  med: {
    id: string;
    name: string;
    dose: string;
    purpose: string;
    criticality?: string;
    daysRemaining?: number;
  };
}) {
  const low = typeof med.daysRemaining === 'number' && med.daysRemaining <= 10;

  return (
    <div className="rounded-2xl border border-ink/10 bg-white p-6">
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
        {typeof med.daysRemaining === 'number' ? (
          <span className={`text-xs ${low ? 'font-semibold text-concern' : 'text-slate-ink'}`}>
            {med.daysRemaining} days of supply left
          </span>
        ) : (
          <span className="text-xs text-slate-ink">Supply not tracked</span>
        )}
      </div>
    </div>
  );
}
