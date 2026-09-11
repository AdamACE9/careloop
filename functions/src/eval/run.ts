/**
 * CareLoop evaluation harness.
 *
 * Run with:  npm --prefix functions run eval
 *
 * ## What this is, and what it deliberately is not
 *
 * This checks the parts of the agent whose behaviour is decided by us rather
 * than sampled from a model: the reasoning engine that decides whether to
 * escalate to family, the retry policy, and the curated interaction rules.
 *
 * Those are exactly the parts that must not be evaluated by vibes. An agent
 * that autonomously reports a person's health to their family is making a
 * consequential decision, and "it seemed right when I tried it" is not an
 * acceptable standard for it. Every case below is a product invariant: a
 * statement about what CareLoop must never do, not about how it currently
 * happens to be implemented. A failure here is a real defect, not a stale test.
 *
 * The conversational layer (does Cara actually sound warm, does she catch a
 * mumbled drug name) cannot be asserted this way and is not faked here. Those
 * scenarios live in docs/EVALUATION.md as a scripted set to run against a live
 * model, and they are marked honestly as requiring one.
 */

import { reason, type ReasoningInput } from '../reasoning/engine.js';
import { findFoodInteractions, findFoodInteractionsByFood } from '../interactions/foodRules.js';
import type { CheckInDoc, MedicationDoc, Criticality } from '../types.js';

// -----------------------------------------------------------------------------
// Tiny assertion runner. No framework: this has to run in CI and in a terminal
// with nothing installed beyond what the functions already depend on.
// -----------------------------------------------------------------------------

interface Result {
  id: string;
  name: string;
  passed: boolean;
  detail: string;
}

const results: Result[] = [];

function check(id: string, name: string, fn: () => string | null): void {
  try {
    const failure = fn();
    results.push({ id, name, passed: failure === null, detail: failure ?? 'ok' });
  } catch (error) {
    results.push({ id, name, passed: false, detail: `threw: ${String(error)}` });
  }
}

// -----------------------------------------------------------------------------
// Fixtures
// -----------------------------------------------------------------------------

const ISO = (daysAgo: number): string =>
  new Date(Date.now() - daysAgo * 86_400_000).toISOString();

function med(name: string, criticality: Criticality): MedicationDoc {
  return {
    name,
    dose: '5mg',
    purpose: 'test fixture',
    schedule: ['08:00'],
    criticality,
    dosesRemaining: 30,
    dosesPerDay: 1,
    refillLeadTimeDays: 7,
    foodGuidance: null,
    rxcui: null,
    createdAt: ISO(90),
    updatedAt: ISO(1),
  };
}

function checkIn(
  daysAgo: number,
  opts: {
    confirmed?: string[];
    missed?: string[];
    status?: CheckInDoc['status'];
    confusion?: number;
  } = {},
): CheckInDoc & { id: string } {
  return {
    id: `ci-${daysAgo}`,
    startedAt: ISO(daysAgo),
    durationSeconds: 120,
    status: opts.status ?? 'completed',
    medicationsConfirmed: opts.confirmed ?? [],
    medicationsMissed: opts.missed ?? [],
    transcript: [],
    toneSignals: opts.confusion
      ? { confusion: opts.confusion, hesitation: opts.confusion, note: 'unsure' }
      : null,
    caraSummary: 'fixture',
    callAttemptId: null,
  };
}

function input(over: Partial<ReasoningInput>): ReasoningInput {
  return {
    patientName: 'Margaret',
    caretakerName: 'Sarah',
    medications: [med('Warfarin', 'critical'), med('Atorvastatin', 'low')],
    recentCheckIns: [],
    recentVitals: [],
    consecutiveNoAnswers: 0,
    attemptsToday: 0,
    ...over,
  };
}

// -----------------------------------------------------------------------------
// Reasoning invariants
// -----------------------------------------------------------------------------

check('R1', 'A week of confirmed doses never escalates', () => {
  const out = reason(
    input({
      recentCheckIns: [0, 1, 2, 3, 4, 5, 6].map((d) =>
        checkIn(d, { confirmed: ['Warfarin', 'Atorvastatin'] }),
      ),
    }),
  );
  return out.action === 'escalate'
    ? `escalated on a clean week (score ${out.concernScore})`
    : null;
});

check('R2', 'A single missed critical dose reaches the family', () => {
  const out = reason(
    input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'], confirmed: ['Atorvastatin'] })] }),
  );
  return out.action === 'escalate'
    ? null
    : `did not escalate a missed anticoagulant (action ${out.action}, score ${out.concernScore})`;
});

check('R3', 'A single missed low-criticality dose does not', () => {
  const out = reason(
    input({ recentCheckIns: [checkIn(0, { missed: ['Atorvastatin'], confirmed: ['Warfarin'] })] }),
  );
  return out.action === 'escalate'
    ? 'escalated one missed statin, which would train the family to ignore alerts'
    : null;
});

check('R4', 'A full week of missed doses escalates even at the lowest criticality', () => {
  // Seven days rather than five. Five is a judgement call about calibration and
  // is reported separately below; a person missing a medication every single day
  // for a week is not a judgement call, and if that never reaches the family the
  // engine is broken regardless of how the weights are tuned.
  const out = reason(
    input({
      recentCheckIns: [0, 1, 2, 3, 4, 5, 6].map((d) =>
        checkIn(d, { missed: ['Atorvastatin'], confirmed: ['Warfarin'] }),
      ),
    }),
  );
  return out.action === 'escalate'
    ? null
    : `a full week of misses did not escalate (score ${out.concernScore})`;
});

check('R5', 'Uncertainty is never treated as less concerning than a clean miss', () => {
  const clean = reason(input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'] })] }));
  const unsure = reason(
    input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'], confusion: 0.8 })] }),
  );
  return unsure.concernScore >= clean.concernScore
    ? null
    : `uncertain (${unsure.concernScore}) scored below clean miss (${clean.concernScore})`;
});

check('R6', 'Every escalation cites at least one specific check-in', () => {
  const out = reason(input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'] })] }));
  if (out.action !== 'escalate') return 'precondition failed: did not escalate';
  if (!out.reasoning.length) return 'escalated with an empty reasoning trace';
  const cited = out.reasoning.some((step) => step.checkInId);
  return cited ? null : 'no reasoning step cites a check-in id, so the trace is unverifiable';
});

check('R7', 'Escalations always carry a headline and an explanation', () => {
  const out = reason(input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'] })] }));
  if (out.action !== 'escalate') return 'precondition failed: did not escalate';
  if (!out.headline.trim()) return 'empty headline';
  if (!out.explanation.trim()) return 'empty explanation';
  return null;
});

check('R8', 'A more critical medication is chased sooner than a less critical one', () => {
  const critical = reason(
    input({
      medications: [med('Warfarin', 'critical')],
      recentCheckIns: [checkIn(0, { status: 'no_answer' })],
      consecutiveNoAnswers: 1,
      attemptsToday: 1,
    }),
  );
  const low = reason(
    input({
      medications: [med('Atorvastatin', 'low')],
      recentCheckIns: [checkIn(0, { status: 'no_answer' })],
      consecutiveNoAnswers: 1,
      attemptsToday: 1,
    }),
  );
  if (critical.retryInMinutes === null || low.retryInMinutes === null) {
    return `no retry scheduled (critical ${critical.retryInMinutes}, low ${low.retryInMinutes})`;
  }
  return critical.retryInMinutes < low.retryInMinutes
    ? null
    : `critical waited ${critical.retryInMinutes}m, low waited ${low.retryInMinutes}m`;
});

check('R9', 'Past the daily attempt ceiling it escalates instead of calling again', () => {
  const out = reason(
    input({
      recentCheckIns: [checkIn(0, { status: 'no_answer' })],
      consecutiveNoAnswers: 4,
      attemptsToday: 4,
    }),
  );
  return out.action === 'escalate'
    ? null
    : `kept retrying after 4 attempts (action ${out.action}), which is harassment, not care`;
});

check('R10', 'Never escalates without naming an alternative it considered', () => {
  const out = reason(input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'] })] }));
  if (out.action !== 'escalate') return 'precondition failed: did not escalate';
  return out.alternativesConsidered.length
    ? null
    : 'escalated without recording what else it considered';
});

// -----------------------------------------------------------------------------
// Interaction-rule invariants
// -----------------------------------------------------------------------------

check('I1', 'Warfarin and leafy greens gives consistency advice, never avoidance', () => {
  const hits = findFoodInteractions('Warfarin');
  const greens = hits.find((h) => /green|kale|spinach|vitamin k/i.test(`${h.food} ${h.whatItMeans}`));
  if (!greens) return 'no leafy-green rule found for warfarin at all';
  const text = `${greens.advice} ${greens.whatItMeans}`;
  const saysAvoid = /avoid|stop eating|cut out/i.test(text);
  const saysSteady = /steady|consistent|same amount|similar amount/i.test(text);
  if (saysAvoid && !saysSteady) return `tells the person to avoid greens: "${greens.advice}"`;
  // The misconception field is the point of this rule existing at all.
  return greens.commonMisconception
    ? null
    : 'leafy-green rule carries no correction for the common "avoid greens" myth';
});

check('I2', 'Grapefruit is caught for a statin', () => {
  const hits = findFoodInteractionsByFood('grapefruit', ['Atorvastatin']);
  return hits.length ? null : 'grapefruit and atorvastatin returned nothing';
});

check('I3', 'Food lookup matches how a person would actually say it', () => {
  const hits = findFoodInteractionsByFood('a glass of grapefruit juice', ['Atorvastatin']);
  return hits.length ? null : 'phrasing as spoken aloud returned nothing';
});

check('I4', 'An unrelated food returns nothing rather than a false positive', () => {
  const hits = findFoodInteractionsByFood('toast', ['Atorvastatin']);
  return hits.length ? `invented an interaction for toast: ${hits[0]?.food}` : null;
});

// -----------------------------------------------------------------------------
// Characterisation
//
// Not assertions. These print where the engine currently sits on judgement
// calls that have no obviously correct answer, so the calibration is visible
// and arguable rather than buried in constants. Escalation threshold is 1.0.
// -----------------------------------------------------------------------------

const characterise: Array<[string, number]> = [
  [
    '1 missed statin (low)',
    reason(input({ recentCheckIns: [checkIn(0, { missed: ['Atorvastatin'] })] })).concernScore,
  ],
  [
    '3 consecutive missed statins',
    reason(
      input({
        recentCheckIns: [0, 1, 2].map((d) => checkIn(d, { missed: ['Atorvastatin'] })),
      }),
    ).concernScore,
  ],
  [
    '5 consecutive missed statins',
    reason(
      input({
        recentCheckIns: [0, 1, 2, 3, 4].map((d) => checkIn(d, { missed: ['Atorvastatin'] })),
      }),
    ).concernScore,
  ],
  [
    '1 missed anticoagulant (critical)',
    reason(input({ recentCheckIns: [checkIn(0, { missed: ['Warfarin'] })] })).concernScore,
  ],
];

// -----------------------------------------------------------------------------
// Report
// -----------------------------------------------------------------------------

const passed = results.filter((r) => r.passed).length;
const failed = results.length - passed;

for (const r of results) {
  const mark = r.passed ? 'PASS' : 'FAIL';
  console.log(`${mark}  ${r.id}  ${r.name}`);
  if (!r.passed) console.log(`        ${r.detail}`);
}

console.log('');
console.log('Calibration (escalation threshold is 1.0):');
for (const [label, score] of characterise) {
  const verdict = score >= 1 ? 'escalates' : 'stays quiet';
  console.log(`        ${label}: ${score.toFixed(2)} -> ${verdict}`);
}

console.log('');
console.log(`${passed}/${results.length} passed, ${failed} failed`);

process.exitCode = failed > 0 ? 1 : 0;
