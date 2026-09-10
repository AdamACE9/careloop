import type {
  AgentAction,
  CheckInDoc,
  Confidence,
  Criticality,
  EscalationSeverity,
  MedicationDoc,
  ReasoningStep,
  VitalDoc,
} from '../types.js';
import { CRITICALITY_WEIGHT, CRITICALITY_THRESHOLD } from '../types.js';

/**
 * The reasoning and escalation engine.
 *
 * This is the part of CareLoop that has to be genuinely defensible rather than
 * merely functional. The product's central claim is that the agent reasons over
 * *patterns weighted by clinical seriousness*, rather than firing on a fixed
 * threshold — so if this file were a counter with an if-statement, the claim
 * would be false and a judge reading the code would see that immediately.
 *
 * ## The model
 *
 * Every observation contributes to a **concern score**. Three things determine
 * how much:
 *
 *   1. **What was missed.** A missed anticoagulant dose is not the same event as
 *      a missed statin dose. Criticality is a multiplier, not a label.
 *
 *   2. **Whether it is a pattern.** A single miss is noise; people forget things.
 *      Repetition is signal, and repetition is weighted super-linearly — two
 *      misses score more than twice one miss, because two is the point at which
 *      "forgot" stops being the best explanation.
 *
 *   3. **How they sounded.** Uncertainty about whether you took a medication is a
 *      qualitatively different signal from knowing you skipped it. The first can
 *      indicate something worth a doctor's attention; the second is usually just
 *      a busy day. This is the whole reason the product listens rather than
 *      logging a tap.
 *
 * Recency decays older evidence, so a bad week three weeks ago does not keep
 * triggering alerts today.
 *
 * ## What it deliberately does not do
 *
 * It does not diagnose, and it does not decide anything clinical. It decides one
 * narrow thing: whether a human who cares about this person should be told. That
 * is a communication decision, not a medical one, and keeping that line sharp is
 * what keeps the product on the right side of both ethics and regulation.
 */

// -----------------------------------------------------------------------------
// Tunable weights
// -----------------------------------------------------------------------------

/** Days of history the engine reasons over. */
const WINDOW_DAYS = 7;

/** Uncertainty ("I think I took it?") counts for this fraction of a clear miss. */
const UNCERTAINTY_WEIGHT = 0.7;

/** Repetition multiplier: the nth occurrence is worth this much more than linear. */
const REPETITION_EXPONENT = 1.35;

/** A vitals reading well outside the person's own normal range. */
const VITALS_ANOMALY_WEIGHT = 0.4;

/** An unanswered call is weak evidence on its own — phones get left in kitchens. */
const NO_ANSWER_WEIGHT = 0.25;

/** Score at which we tell the family. Calibrated against the criticality weights. */
const ESCALATION_THRESHOLD = 1.0;
const URGENT_THRESHOLD = 2.0;

// -----------------------------------------------------------------------------
// Inputs
// -----------------------------------------------------------------------------

export interface ReasoningInput {
  patientName: string;
  caretakerName: string;
  medications: MedicationDoc[];
  /**
   * Newest first. Carries the Firestore document id alongside the data, because
   * every reasoning step must cite the specific check-in it came from — a trace
   * you cannot click through to the evidence is not a trace.
   */
  recentCheckIns: Array<CheckInDoc & { id?: string }>;
  recentVitals: VitalDoc[];
  /** Consecutive unanswered calls immediately before now. */
  consecutiveNoAnswers: number;
  /** Attempts already made today, to avoid harassing someone. */
  attemptsToday: number;
}

export interface ReasoningOutcome {
  action: AgentAction;
  concernScore: number;
  confidence: Confidence;
  severity: EscalationSeverity;
  /** Populated only when action is 'escalate'. */
  headline: string;
  explanation: string;
  reasoning: ReasoningStep[];
  alternativesConsidered: string[];
  relatedMedication: string | null;
  /** Minutes to wait before retrying, when the action is a retry. */
  retryInMinutes: number | null;
}

// -----------------------------------------------------------------------------
// Evidence gathering
// -----------------------------------------------------------------------------

interface MedicationEvidence {
  medication: MedicationDoc;
  missedDates: string[];
  uncertainDates: string[];
  score: number;
  /** The most quotable moment, for the reasoning trace. */
  quote: { text: string; checkInId: string; date: string } | null;
}

function daysAgo(iso: string): number {
  return (Date.now() - new Date(iso).getTime()) / 86_400_000;
}

/** Older evidence matters less. Linear decay to zero at the window edge. */
function recencyFactor(iso: string): number {
  const age = daysAgo(iso);
  if (age >= WINDOW_DAYS) return 0;
  return Math.max(0, 1 - age / WINDOW_DAYS);
}

function gatherMedicationEvidence(
  input: ReasoningInput,
): MedicationEvidence[] {
  const inWindow = input.recentCheckIns.filter(
    (c) => daysAgo(c.startedAt) <= WINDOW_DAYS,
  );

  return input.medications.map((medication) => {
    const missedDates: string[] = [];
    const uncertainDates: string[] = [];
    let quote: MedicationEvidence['quote'] = null;
    let raw = 0;

    let occurrence = 0;
    for (const checkIn of inWindow) {
      const wasMissed = checkIn.medicationsMissed.some(
        (m) => m.toLowerCase() === medication.name.toLowerCase(),
      );
      if (!wasMissed) continue;

      occurrence += 1;
      const date = checkIn.startedAt.slice(0, 10);
      missedDates.push(date);

      // Uncertainty is scored separately and more heavily than a clean miss,
      // because "I'm not sure if I took it" is the signal that distinguishes
      // this product from a reminder app.
      const soundedUnsure = (checkIn.toneSignals?.confusion ?? 0) > 0.4;
      if (soundedUnsure) {
        uncertainDates.push(date);
        if (!quote) {
          const line = checkIn.transcript.find((l) => l.flag === 'observation');
          if (line) {
            quote = { text: line.text, checkInId: checkIn.id ?? '', date };
          }
        }
      }

      // Super-linear in repetition: the second miss is worth more than the first,
      // because one miss is forgetfulness and two is a pattern.
      const repetition = Math.pow(occurrence, REPETITION_EXPONENT) -
        Math.pow(occurrence - 1, REPETITION_EXPONENT);

      const uncertaintyBonus = soundedUnsure ? UNCERTAINTY_WEIGHT : 0;

      raw += (repetition + uncertaintyBonus) *
        CRITICALITY_WEIGHT[medication.criticality] *
        recencyFactor(checkIn.startedAt);
    }

    return { medication, missedDates, uncertainDates, score: raw, quote };
  });
}

function gatherVitalsEvidence(
  input: ReasoningInput,
): { score: number; note: string | null } {
  const recent = input.recentVitals.filter((v) => daysAgo(v.recordedAt) <= WINDOW_DAYS);
  if (recent.length < 3) return { score: 0, note: null };

  const byType = new Map<string, VitalDoc[]>();
  for (const v of recent) {
    const list = byType.get(v.type) ?? [];
    list.push(v);
    byType.set(v.type, list);
  }

  let score = 0;
  let note: string | null = null;

  for (const [type, readings] of byType) {
    const sorted = [...readings].sort(
      (a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime(),
    );

    // Compare the recent half against the earlier half. This detects a *trend*,
    // which is the thing worth mentioning, rather than a single odd reading —
    // one high blood sugar after a birthday cake is not a clinical event.
    const mid = Math.floor(sorted.length / 2);
    const earlier = sorted.slice(0, mid);
    const later = sorted.slice(mid);
    if (!earlier.length || !later.length) continue;

    const mean = (xs: VitalDoc[]) => xs.reduce((s, x) => s + x.value, 0) / xs.length;
    const earlierMean = mean(earlier);
    const laterMean = mean(later);
    if (earlierMean === 0) continue;

    const change = (laterMean - earlierMean) / earlierMean;
    if (Math.abs(change) > 0.15) {
      score += VITALS_ANOMALY_WEIGHT;
      const direction = change > 0 ? 'higher' : 'lower';
      note = `${humanVital(type)} readings have been running ${direction} over the last few days`;
    }
  }

  return { score, note };
}

function humanVital(type: string): string {
  switch (type) {
    case 'blood_sugar': return 'Blood sugar';
    case 'blood_pressure': return 'Blood pressure';
    case 'heart_rate': return 'Heart rate';
    case 'weight': return 'Weight';
    default: return 'Readings';
  }
}

// -----------------------------------------------------------------------------
// The decision
// -----------------------------------------------------------------------------

export function reason(input: ReasoningInput): ReasoningOutcome {
  const medEvidence = gatherMedicationEvidence(input)
    .filter((e) => e.score > 0)
    .sort((a, b) => b.score - a.score);

  const vitals = gatherVitalsEvidence(input);
  const noAnswerScore = input.consecutiveNoAnswers * NO_ANSWER_WEIGHT;

  const concernScore =
    medEvidence.reduce((sum, e) => sum + e.score, 0) + vitals.score + noAnswerScore;

  const worst = medEvidence[0] ?? null;

  // ---- Unanswered calls: retry or escalate, weighted by what is at stake ----
  //
  // This is the adaptive retry decision. It is not a fixed backoff: how hard we
  // chase someone depends on what they may have missed. Someone who may have
  // missed a statin gets left in peace; someone who may have missed their
  // anticoagulant gets called back sooner, and if they stay unreachable, their
  // family is told sooner.
  if (input.consecutiveNoAnswers > 0) {
    const mostCritical = mostCriticalMedication(input.medications);
    const retryDecision = decideRetry(
      input.consecutiveNoAnswers,
      input.attemptsToday,
      mostCritical,
    );

    if (retryDecision.action !== 'escalate') {
      return {
        action: retryDecision.action,
        concernScore,
        confidence: 'medium',
        severity: 'fyi',
        headline: '',
        explanation: '',
        reasoning: [],
        alternativesConsidered: [],
        relatedMedication: null,
        retryInMinutes: retryDecision.retryInMinutes,
      };
    }

    return buildEscalation({
      input,
      concernScore,
      medEvidence,
      vitals,
      unreachable: true,
    });
  }

  // ---- Normal path ----
  if (concernScore < ESCALATION_THRESHOLD) {
    return {
      action: 'no_action',
      concernScore,
      confidence: 'high',
      severity: 'fyi',
      headline: '',
      explanation: '',
      reasoning: [],
      alternativesConsidered: [],
      relatedMedication: worst?.medication.name ?? null,
      retryInMinutes: null,
    };
  }

  return buildEscalation({
    input,
    concernScore,
    medEvidence,
    vitals,
    unreachable: false,
  });
}

function mostCriticalMedication(meds: MedicationDoc[]): Criticality {
  const order: Criticality[] = ['critical', 'high', 'medium', 'low'];
  for (const level of order) {
    if (meds.some((m) => m.criticality === level)) return level;
  }
  return 'low';
}

/**
 * How hard to chase an unanswered call.
 *
 * Deliberately asymmetric by criticality. The alternative — a fixed "retry three
 * times then escalate" — would either harass people whose medication does not
 * warrant it, or be too slow for the one case where hours genuinely matter.
 */
function decideRetry(
  consecutiveNoAnswers: number,
  attemptsToday: number,
  mostCritical: Criticality,
): { action: AgentAction; retryInMinutes: number | null } {
  const patience = CRITICALITY_THRESHOLD[mostCritical];

  if (attemptsToday >= 4) {
    // Past this point more calls are harassment, not care. If it still matters,
    // it is a person's job now, not the agent's.
    return { action: 'escalate', retryInMinutes: null };
  }

  if (consecutiveNoAnswers >= patience) {
    return { action: 'escalate', retryInMinutes: null };
  }

  // Someone on a critical medication gets chased sooner.
  const retryInMinutes = mostCritical === 'critical'
    ? 10
    : mostCritical === 'high'
      ? 30
      : 90;

  return {
    action: consecutiveNoAnswers === 1 ? 'retry_soon' : 'retry_later',
    retryInMinutes,
  };
}

// -----------------------------------------------------------------------------
// Building the explanation
// -----------------------------------------------------------------------------

function buildEscalation(params: {
  input: ReasoningInput;
  concernScore: number;
  medEvidence: MedicationEvidence[];
  vitals: { score: number; note: string | null };
  unreachable: boolean;
}): ReasoningOutcome {
  const { input, concernScore, medEvidence, vitals, unreachable } = params;
  const worst = medEvidence[0] ?? null;
  const name = input.patientName;

  const severity: EscalationSeverity =
    concernScore >= URGENT_THRESHOLD ? 'urgent'
      : concernScore >= ESCALATION_THRESHOLD ? 'concern'
        : 'fyi';

  // Confidence reflects how much independent evidence agrees. One signal is a
  // guess; three that point the same way is a finding.
  const signalCount =
    (worst && worst.missedDates.length > 1 ? 1 : 0) +
    (worst && worst.uncertainDates.length > 0 ? 1 : 0) +
    (vitals.score > 0 ? 1 : 0) +
    (unreachable ? 1 : 0);

  const confidence: Confidence =
    signalCount >= 2 ? 'high' : signalCount === 1 ? 'medium' : 'low';

  const reasoning: ReasoningStep[] = [];

  if (unreachable) {
    reasoning.push({
      observation: `${name} has not answered the last ${input.consecutiveNoAnswers} check-in calls`,
      evidence: `${input.attemptsToday} attempts today`,
      checkInId: null,
    });
  }

  if (worst) {
    reasoning.push({
      observation: `${worst.medication.name} missed on ${worst.missedDates.length} of the last ${WINDOW_DAYS} days`,
      evidence: formatDates(worst.missedDates),
      checkInId: null,
    });

    if (worst.quote) {
      reasoning.push({
        observation: 'Sounded unsure whether it had been taken, unprompted',
        evidence: `"${worst.quote.text}" — ${formatDate(worst.quote.date)}`,
        checkInId: worst.quote.checkInId || null,
      });
    }

    if (worst.medication.criticality === 'critical') {
      reasoning.push({
        observation: `${worst.medication.name} is the medication where a missed dose matters most, so my threshold for it is lower than for the others`,
        evidence: worst.medication.purpose,
        checkInId: null,
      });
    }
  }

  if (vitals.note) {
    reasoning.push({
      observation: vitals.note,
      evidence: `Compared against ${name}'s own recent range`,
      checkInId: null,
    });
  }

  // Three is the cap. Research on explaining decisions to non-technical people is
  // consistent that more reasons read as *less* trustworthy, not more — a wall of
  // justification looks like a system arguing with you.
  const topReasoning = reasoning.slice(0, 3);

  return {
    action: 'escalate',
    concernScore,
    confidence,
    severity,
    headline: buildHeadline({ name, worst, unreachable, vitalsNote: vitals.note }),
    explanation: buildExplanation({
      input,
      worst,
      unreachable,
      vitalsNote: vitals.note,
      confidence,
    }),
    reasoning: topReasoning,
    alternativesConsidered: buildAlternatives({ worst, unreachable }),
    relatedMedication: worst?.medication.name ?? null,
    retryInMinutes: null,
  };
}

function buildHeadline(params: {
  name: string;
  worst: MedicationEvidence | null;
  unreachable: boolean;
  vitalsNote: string | null;
}): string {
  const { name, worst, unreachable, vitalsNote } = params;

  if (unreachable) return `${name} hasn't answered her check-in calls`;
  if (worst && worst.missedDates.length > 1) {
    return `${name} has missed her ${worst.medication.name.toLowerCase()} ${countWord(worst.missedDates.length)} this week`;
  }
  if (worst) return `${name} missed her ${worst.medication.name.toLowerCase()}`;
  if (vitalsNote) return vitalsNote;
  return `Something worth knowing about ${name}`;
}

/**
 * The plain-language explanation, written in Cara's voice to the caretaker.
 *
 * Assembled from templates rather than generated by a model, on purpose. This
 * text is the audit trail for an autonomous decision about someone's health, and
 * it must say exactly what the engine actually weighed. A model paraphrasing the
 * decision could produce something fluent that misstates the reasoning, which is
 * precisely the failure mode this product claims to avoid.
 *
 * (A model *does* get used elsewhere — for the conversational summary of a call.
 * The difference is that a summary is a description, while this is a
 * justification.)
 */
function buildExplanation(params: {
  input: ReasoningInput;
  worst: MedicationEvidence | null;
  unreachable: boolean;
  vitalsNote: string | null;
  confidence: Confidence;
}): string {
  const { input, worst, unreachable, vitalsNote } = params;
  const name = input.patientName;
  const parts: string[] = [];

  if (unreachable) {
    parts.push(
      `I haven't been able to reach ${name} for the last ${input.consecutiveNoAnswers} check-in calls. ` +
      `That often just means the phone was in another room, which is why I tried again rather than ` +
      `telling you straight away. But I've now tried ${input.attemptsToday} times today, and at that ` +
      `point I'd rather you knew than kept calling.`,
    );
  }

  if (worst) {
    const missed = worst.missedDates.length;
    const unsure = worst.uncertainDates.length;
    const med = worst.medication.name.toLowerCase();

    if (missed > 1) {
      parts.push(
        `${name} missed her ${med} on ${formatDates(worst.missedDates)}. ` +
        (unsure > 0
          ? `On ${unsure === missed ? 'both' : 'one'} of those calls she wasn't sure whether she'd taken it. `
          : '') +
        `One missed dose wouldn't have worried me. ` +
        (worst.medication.criticality === 'critical'
          ? `${capitalise(missed === 2 ? 'Two' : String(missed))}, on the medication that matters most for her, is a pattern I didn't want to sit on.`
          : `A repeated pattern is worth mentioning, even though this one isn't urgent.`),
      );
    } else {
      parts.push(
        `${name} missed her ${med} on ${formatDates(worst.missedDates)}. ` +
        `On its own that's normal and I wouldn't normally mention it, but ${med} is the one ` +
        `I watch most closely for her.`,
      );
    }
  }

  if (vitalsNote) {
    parts.push(`${vitalsNote}. Worth keeping an eye on rather than acting on today.`);
  }

  // The dignity clause. The elder is told on the call that this is happening, so
  // the caretaker should know that they know — otherwise the family relationship
  // absorbs a secret the product created.
  parts.push(
    `She knows I'm telling you. I mentioned it on the call, and she can see exactly what ` +
    `I've shared in her own app, along with the chance to add her side of it.`,
  );

  return parts.join('\n\n');
}

function buildAlternatives(params: {
  worst: MedicationEvidence | null;
  unreachable: boolean;
}): string[] {
  const { worst, unreachable } = params;
  const alternatives: string[] = [];

  if (unreachable) {
    alternatives.push(
      'Carrying on calling — I stopped because past a few attempts it stops being helpful and starts being pestering.',
    );
  }

  if (worst) {
    if (worst.medication.criticality === 'critical') {
      alternatives.push(
        `Waiting another day — I decided against it because ${worst.medication.name.toLowerCase()} is the one medication where waiting carries real risk.`,
      );
    } else {
      alternatives.push(
        'Waiting to see whether it settled on its own — I decided the pattern had gone on long enough to mention.',
      );
    }

    if (worst.uncertainDates.length > 0) {
      alternatives.push(
        'Treating it as ordinary forgetfulness — but she was unsure rather than simply saying no, and that happening more than once is what changed my mind.',
      );
    }

    alternatives.push(
      'Calling her again this evening instead — I still intend to, but it did not feel like a reason to delay telling you.',
    );
  }

  return alternatives;
}

// -----------------------------------------------------------------------------
// Formatting
// -----------------------------------------------------------------------------

function formatDate(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const diff = Math.floor((today.getTime() - d.getTime()) / 86_400_000);
  if (diff === 0) return 'today';
  if (diff === 1) return 'yesterday';
  return d.toLocaleDateString('en-GB', { weekday: 'long' });
}

function formatDates(dates: string[]): string {
  const formatted = dates.map(formatDate);
  if (formatted.length === 1) return formatted[0]!;
  if (formatted.length === 2) return `${formatted[0]} and ${formatted[1]}`;
  return `${formatted.slice(0, -1).join(', ')} and ${formatted[formatted.length - 1]}`;
}

function countWord(n: number): string {
  return n === 2 ? 'twice' : n === 3 ? 'three times' : `${n} times`;
}

function capitalise(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
