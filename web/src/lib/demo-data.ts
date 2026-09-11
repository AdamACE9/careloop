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

export interface CheckIn {
  id: string;
  date: string;
  label: string;
  time: string;
  durationSeconds: number;
  status: CheckInStatus;
  confirmed: string[];
  missed: string[];
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

/** 14 days of blood sugar. Drifts up mid-period, settles after the escalation. */
export const bloodSugar = [
  { day: "13 d", value: 6.1 },
  { day: "12 d", value: 5.8 },
  { day: "11 d", value: 6.4 },
  { day: "10 d", value: 6.0 },
  { day: "9 d", value: 6.3 },
  { day: "8 d", value: 5.9 },
  { day: "7 d", value: 6.2 },
  { day: "6 d", value: 6.6 },
  { day: "5 d", value: 6.9 },
  { day: "4 d", value: 7.4 },
  { day: "3 d", value: 7.9 },
  { day: "2 d", value: 8.3 },
  { day: "1 d", value: 8.1 },
  { day: "Today", value: 7.6 },
];

export const bloodSugarNormalRange = { min: 4.0, max: 7.8 };

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
    date: "Today",
    label: "Today",
    time: "9:00 am",
    durationSeconds: 66,
    status: "completed",
    confirmed: ["Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"],
    missed: [],
    caraSummary: "Everything taken. Blood sugar is coming back down.",
  },
  {
    id: "ci-1",
    date: "Yesterday",
    label: "Yesterday",
    time: "9:02 am",
    durationSeconds: 78,
    status: "completed",
    confirmed: ["Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"],
    missed: [],
    caraSummary:
      "Warfarin taken, and Margaret had spoken to her GP about the ibuprofen. She's switched to paracetamol.",
  },
  {
    id: "ci-2",
    date: "Thursday",
    label: "Thursday",
    time: "9:01 am",
    durationSeconds: 112,
    status: "escalated",
    confirmed: ["Metformin", "Ramipril", "Ferrous sulfate"],
    missed: ["Warfarin"],
    caraSummary:
      "Warfarin missed again, and Margaret was unsure whether she'd taken it, the same uncertainty as Tuesday. She also mentioned taking ibuprofen for her knee, which doesn't mix well with warfarin. I let Sarah know.",
  },
  {
    id: "ci-3",
    date: "Wednesday",
    label: "Wednesday",
    time: "9:00 am",
    durationSeconds: 59,
    status: "completed",
    confirmed: ["Warfarin", "Metformin", "Ramipril", "Ferrous sulfate"],
    missed: [],
    caraSummary: "Back on track. Warfarin taken.",
  },
  {
    id: "ci-4",
    date: "Tuesday",
    label: "Tuesday",
    time: "9:03 am",
    durationSeconds: 88,
    status: "missed_dose",
    confirmed: ["Metformin", "Ramipril", "Ferrous sulfate"],
    missed: ["Warfarin"],
    caraSummary:
      "Warfarin missed last night. Margaret wasn't sure whether she'd taken it. I've made a note to watch this, it's the first time.",
  },
  {
    id: "ci-5",
    date: "Monday",
    label: "Monday",
    time: "9:01 am",
    durationSeconds: 64,
    status: "completed",
    confirmed: ["Warfarin", "Metformin", "Ramipril"],
    missed: ["Ferrous sulfate"],
    caraSummary:
      "Iron tablet missed at lunch. Margaret said she'd forgotten it was in the kitchen drawer. Not concerning on its own.",
  },
];

export const primaryEscalation: Escalation = {
  id: "esc-1",
  raisedAt: "Thursday, 9:04 am",
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
  raisedAt: "Yesterday, 9:06 am",
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
