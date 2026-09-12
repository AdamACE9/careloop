/**
 * Demo data for the caretaker dashboard.
 *
 * Deliberately mirrors the Android app's `MockData.kt`, same people, same medications,
 * same week, same escalation. The app and the dashboard are two views of one story, and a
 * judge who looks at both should find them consistent down to the wording of what Cara said.
 *
 * TODO(backend): replace with Firestore reads through a service module with this exact
 * shape, so no component needs to change. Keep the types; swap the source.
 */

export type Confidence = "high" | "medium" | "low";
export type EscalationSeverity = "fyi" | "concern" | "urgent";
export type ElderResponse = "not_yet_seen" | "confirmed" | "disputed";
export type CheckInStatus = "completed" | "missed_dose" | "no_answer" | "escalated";

export interface ReasoningStep {
  observation: string;
  evidence: string;
}

export interface Escalation {
  id: string;
  raisedAt: string;
  severity: EscalationSeverity;
  headline: string;
  explanation: string;
  reasoning: ReasoningStep[];
  confidence: Confidence;
  alternativesConsidered: string[];
  relatedMedication?: string;
  elderResponse: ElderResponse;
  elderNote?: string;
  acknowledged: boolean;
}

/**
 * One check-in, in exactly the shape Firestore stores.
 *
 * This used to carry `label: "Thursday"`, `time: "9:01 am"`, `confirmed` and
 * `missed`. None of those fields exist in a real document, which stores
 * `startedAt`, `medicationsConfirmed` and `medicationsMissed`. The dashboard
 * read the demo names, so against real data the date column rendered blank and,
 * far worse, `missed?.length ?? 0` was always zero: the overview told every real
 * caretaker their parent was doing well on a day a dose had been missed, and the
 * "with a missed dose" filter never matched anything.
 *
 * The display strings are derived in careloop-service, once, for both data
 * sources. Formatting is a view concern and does not belong in storage.
 */
export interface CheckIn {
  id: string;
  startedAt: string;
  durationSeconds: number;
  status: CheckInStatus;
  medicationsConfirmed: string[];
  medicationsMissed: string[];
  caraSummary: string;
}

export interface Medication {
  id: string;
  name: string;
  dose: string;
  purpose: string;
  criticality: "critical" | "high" | "medium" | "low";
  daysRemaining: number;
}

export const caretaker = {
  name: "Sarah Whitfield-Chen",
  relationship: "Daughter",
};

export const elder = {
  firstName: "Margaret",
  lastName: "Whitfield",
  age: 78,
  conditions: [
    "Atrial fibrillation",
    "Type 2 diabetes",
    "High blood pressure",
    "Iron-deficiency anaemia",
  ],
  checkInTime: "9:00 am",
};

export const medications: Medication[] = [
  {
    id: "warfarin",
    name: "Warfarin",
    dose: "3 mg",
    purpose: "Prevents clots, for her heart rhythm",
    criticality: "critical",
    daysRemaining: 9,
  },
  {
    id: "metformin",
    name: "Metformin",
    dose: "500 mg",
    purpose: "Keeps blood sugar steady",
    criticality: "high",
    daysRemaining: 22,
  },
  {
    id: "ramipril",
    name: "Ramipril",
    dose: "5 mg",
    purpose: "Blood pressure",
    criticality: "high",
    daysRemaining: 26,
  },
  {
    id: "iron",
    name: "Ferrous sulfate",
    dose: "200 mg",
    purpose: "For the anaemia",
    criticality: "medium",
    daysRemaining: 31,
  },
  {
    id: "atorvastatin",
    name: "Atorvastatin",
    dose: "20 mg",
    purpose: "Cholesterol",
    criticality: "medium",
    daysRemaining: 52,
  },
];

/**
 * One reading, in exactly the shape Firestore stores.
 *
 * The example data used to be `{ day: "3 d", value: 7.9 }`, which meant the
 * chart could only ever render the example data: nothing in Firestore looks
 * like that, and the real documents have an ISO timestamp and a type. So the
 * chart had a demo import baked into it and no way to show a real person's
 * readings. Matching the stored shape is what lets one component serve both.
 */
export interface VitalReading {
  id: string;
  type: "blood_sugar" | "blood_pressure" | "heart_rate" | "weight";
  value: number;
  /** Diastolic, for blood pressure only. */
  secondaryValue: number | null;
  recordedAt: string;
  source: "call" | "manual";
}

/**
 * What counts as normal, per reading type, with the unit to display.
 *
 * `min`/`max` are null where the idea does not apply. Weight has no normal
 * range: a healthy weight is a fact about a particular person, not about the
 * measurement, and drawing a band across the chart would assert something this
 * app has no basis for. The chart omits the band rather than inventing one.
 *
 * There is no fixed axis range here on purpose. The chart sizes its own axis to
 * the readings plus the band, so the line fills the plot instead of sitting in a
 * flat strip across the middle of a range chosen in advance.
 */
export const vitalRanges: Record<
  VitalReading["type"],
  { label: string; unit: string; min: number | null; max: number | null }
> = {
  blood_sugar: { label: "Blood sugar", unit: "mmol/L", min: 4.0, max: 7.8 },
  blood_pressure: { label: "Blood pressure", unit: "mmHg", min: 90, max: 140 },
  heart_rate: { label: "Heart rate", unit: "bpm", min: 60, max: 100 },
  weight: { label: "Weight", unit: "kg", min: null, max: null },
};

/** Kept for anything still importing it. Derived, never edited separately. */
export const bloodSugarNormalRange = {
  min: vitalRanges.blood_sugar.min!,
  max: vitalRanges.blood_sugar.max!,
};

/**
 * A local-time ISO string n days before the example "today".
 *
 * No trailing Z on purpose: see the note on the example timestamps above. A
 * string without an offset is parsed as local time, so these readings land at
 * 9am for every reader instead of shifting across the day by timezone.
 */
function daysAgo(n: number): string {
  const d = new Date(2026, 8, 12, 9, 0, 0);
  d.setDate(d.getDate() - n);
  const pad = (x: number) => String(x).padStart(2, "0");
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
  );
}

/**
 * Fourteen days of readings for the example household.
 *
 * Blood sugar drifts up mid-period and settles after the escalation, which is
 * the same story the example check-ins and the example escalation tell. The
 * three datasets have to agree, or the demo contradicts itself on screen.
 */
export const vitals: VitalReading[] = [
  ...[6.1, 5.8, 6.4, 6.0, 6.3, 5.9, 6.2, 6.6, 6.9, 7.4, 7.9, 8.3, 8.1, 7.6].map(
    (value, i): VitalReading => ({
      id: `bs-${i}`,
      type: "blood_sugar",
      value,
      secondaryValue: null,
      recordedAt: daysAgo(13 - i),
      source: "call",
    }),
  ),
  ...[
    [138, 84],
    [142, 86],
    [136, 82],
    [145, 88],
    [139, 83],
  ].map(
    ([systolic, diastolic], i): VitalReading => ({
      id: `bp-${i}`,
      type: "blood_pressure",
      value: systolic!,
      secondaryValue: diastolic!,
      recordedAt: daysAgo(12 - i * 3),
      source: "call",
    }),
  ),
];

export const adherence = [
  { day: "Mon", taken: 5, total: 5 },
  { day: "Tue", taken: 4, total: 5 },
  { day: "Wed", taken: 5, total: 5 },
  { day: "Thu", taken: 4, total: 5 },
  { day: "Fri", taken: 5, total: 5 },
  { day: "Sat", taken: 5, total: 5 },
  { day: "Sun", taken: 5, total: 5 },
];

export const checkIns: CheckIn[] = [
  {
    id: "ci-0",
    startedAt: "2026-09-12T09:00:00",
    durationSeconds: 66,
    status: "completed",
    medicationsConfirmed: ["Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"],
    medicationsMissed: [],
    caraSummary: "Everything taken. Blood sugar is coming back down.",
  },
  {
    id: "ci-1",
    startedAt: "2026-09-11T09:02:00",
    durationSeconds: 78,
    status: "completed",
    medicationsConfirmed: ["Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"],
    medicationsMissed: [],
    caraSummary:
      "Warfarin taken, and Margaret had spoken to her GP about the ibuprofen. She's switched to paracetamol.",
  },
  {
    id: "ci-2",
    startedAt: "2026-09-10T09:01:00",
    durationSeconds: 112,
    status: "escalated",
    medicationsConfirmed: ["Metformin", "Ramipril", "Ferrous sulfate"],
    medicationsMissed: ["Warfarin"],
    caraSummary:
      "Warfarin missed again, and Margaret was unsure whether she'd taken it, the same uncertainty as Tuesday. She also mentioned taking ibuprofen for her knee, which doesn't mix well with warfarin. I let Sarah know.",
  },
  {
    id: "ci-3",
    startedAt: "2026-09-09T09:00:00",
    durationSeconds: 59,
    status: "completed",
    medicationsConfirmed: ["Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"],
    medicationsMissed: [],
    caraSummary: "Back on track. Warfarin taken.",
  },
  {
    id: "ci-4",
    startedAt: "2026-09-08T09:03:00",
    durationSeconds: 88,
    status: "missed_dose",
    medicationsConfirmed: ["Metformin", "Ramipril", "Ferrous sulfate"],
    medicationsMissed: ["Warfarin"],
    caraSummary:
      "Warfarin missed last night. Margaret wasn't sure whether she'd taken it. I've made a note to watch this, it's the first time.",
  },
  {
    id: "ci-5",
    startedAt: "2026-09-07T09:01:00",
    durationSeconds: 64,
    status: "completed",
    medicationsConfirmed: ["Warfarin", "Metformin", "Ramipril"],
    medicationsMissed: ["Ferrous sulfate"],
    caraSummary:
      "Iron tablet missed at lunch. Margaret said she'd forgotten it was in the kitchen drawer. Not concerning on its own.",
  },
];

export const primaryEscalation: Escalation = {
  id: "esc-1",
  raisedAt: "2026-09-10T09:04:00",
  severity: "concern",
  headline: "Margaret has missed her warfarin twice this week",
  explanation:
    "I'm reaching out because your mother missed her warfarin on Tuesday evening and again on Thursday, and on both calls she wasn't sure whether she'd taken it. One missed dose wouldn't have worried me. Two, with the same uncertainty each time, on the medication that matters most for her heart rhythm, is a pattern I didn't want to sit on.\n\nShe also mentioned she's been taking ibuprofen for her knee. That doesn't mix well with warfarin, together they make bleeding more likely, so I asked her to speak to her GP before taking any more. She said she would.\n\nShe knows I'm telling you. I asked her on the call and she was happy for me to.",
  reasoning: [
    {
      observation: "Warfarin missed on two of the last five evenings",
      evidence: "Tuesday and Thursday check-ins",
    },
    {
      observation: "She was unsure whether she'd taken it, both times, unprompted",
      evidence: '"I\'m not sure if I took it or not, love", Thursday, 9:01 am',
    },
    {
      observation:
        "Warfarin is her highest-risk medication, so my threshold for it is lower than for the others",
      evidence: "Anticoagulant for atrial fibrillation",
    },
  ],
  confidence: "high",
  alternativesConsidered: [
    "Waiting another day, I decided against it because warfarin is the one medication where a wait carries real risk.",
    "Treating it as forgetfulness only, but the same uncertainty twice, rather than simply forgetting, is what changed my mind.",
    "Calling her again in the evening instead, I'll still do this, but it didn't feel like a reason to delay telling you.",
  ],
  relatedMedication: "Warfarin",
  elderResponse: "confirmed",
  acknowledged: true,
};

export const refillEscalation: Escalation = {
  id: "esc-2",
  raisedAt: "2026-09-11T09:06:00",
  severity: "fyi",
  headline: "Warfarin runs out in about nine days",
  explanation:
    "Not urgent, but worth starting now, repeat prescriptions for warfarin usually take a few days, and it's not one I'd want her to run out of. She has nine days left.",
  reasoning: [
    { observation: "9 doses remaining, one per day", evidence: "Counted from her last refill" },
    {
      observation: "Repeat prescriptions typically take 3–5 working days",
      evidence: "Her usual pharmacy's stated turnaround",
    },
  ],
  confidence: "high",
  alternativesConsidered: [],
  relatedMedication: "Warfarin",
  elderResponse: "not_yet_seen",
  acknowledged: false,
};

export const escalations = [primaryEscalation, refillEscalation];

/**
 * Something Cara decided, on her own, to come back to on a later call.
 *
 * Shown to the family and to the elder in identical terms. An agent memory that
 * only one side can see is surveillance, and the whole position of this product
 * is that it is not that.
 */
export interface AgentThread {
  id: string;
  topic: string;
  why: string;
  status: "open" | "resolved";
  raisedAt: string;
  followUpAfter: string;
  timesRaised: number;
  resolution: string | null;
  resolvedAt: string | null;
}

export const agentThreads: AgentThread[] = [
  {
    id: "th-1",
    topic: "Her right knee hurting on the stairs",
    why: "She mentioned it twice without my asking, and it is the reason she has been going up to bed later.",
    status: "open",
    raisedAt: "2026-09-08T09:14:00",
    followUpAfter: "2026-09-11T09:00:00",
    timesRaised: 1,
    resolution: null,
    resolvedAt: null,
  },
  {
    id: "th-2",
    topic: "Whether the new pill box is easier to use",
    why: "Sarah bought it on Saturday and most of the missed doses this month were on days she used the old one.",
    status: "open",
    raisedAt: "2026-09-09T09:06:00",
    followUpAfter: "2026-09-12T09:00:00",
    timesRaised: 0,
    resolution: null,
    resolvedAt: null,
  },
  {
    id: "th-3",
    topic: "Feeling light headed standing up",
    why: "She said it happened twice on Tuesday. Worth knowing whether it settled or kept going.",
    status: "resolved",
    raisedAt: "2026-09-02T09:11:00",
    followUpAfter: "2026-09-04T09:00:00",
    timesRaised: 2,
    resolution: "It stopped after she started taking the tablet with breakfast instead of before it. She told her GP at her Friday appointment.",
    resolvedAt: "2026-09-06T09:20:00",
  },
];

export const confidencePhrase: Record<Confidence, string> = {
  high: "I'm quite sure",
  medium: "I'm fairly confident",
  low: "I'm not certain",
};

/** The interaction Cara caught mid-call. */
export const liveInteraction = {
  drugA: "Warfarin",
  drugB: "Ibuprofen",
  severity: "Serious",
  whatItMeans:
    "Taken together, these make bleeding much more likely, including bleeding in the stomach that can be hard to notice at first.",
  mechanism:
    "Ibuprofen irritates the stomach lining and also stops platelets clumping properly. Warfarin is already slowing her clotting. The two effects stack.",
  source: "openFDA / DrugBank",
};

/**
 * The scripted call, used by the phone mock on the landing page.
 *
 * Explicitly typed rather than `as const`. With `as const`, TypeScript narrows each entry
 * to its own literal type, so `flag` only exists on the two objects that declare it and
 * reading `line.flag` across the array fails to compile.
 */
export interface CallScriptLine {
  speaker: "cara" | "elder";
  text: string;
  flag?: "checking" | "observation";
}

export const callScript: CallScriptLine[] = [
  { speaker: "cara", text: "Good morning, Margaret. It's Cara. How are you feeling today?" },
  { speaker: "elder", text: "Oh, morning. I'm alright. A bit stiff, but alright." },
  { speaker: "cara", text: "Stiff where, if you don't mind me asking?" },
  {
    speaker: "elder",
    text: "My knee, mostly. I've been taking ibuprofen for it. Couple a day.",
    flag: "checking",
  },
  {
    speaker: "cara",
    text: "Thank you for telling me, let me just look at that alongside your other tablets while we talk.",
  },
  {
    speaker: "elder",
    text: "The warfarin... I think so. I'm not sure if I took it or not, love.",
    flag: "observation",
  },
];
