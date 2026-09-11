/**
 * CareLoop shared domain types.
 *
 * This file is the single source of truth for the shape of every Firestore
 * document and every callable-function request/response. It deliberately mirrors
 * the Android app's `data/model/Models.kt` field-for-field — if you change a name
 * here, change it there, or the two sides silently disagree and the bug surfaces
 * as a blank screen rather than an error.
 *
 * Timestamps are stored in Firestore as `Timestamp` but cross the wire as ISO-8601
 * strings, because Kotlin and TypeScript disagree about how to deserialise
 * Firestore's native type and ISO-8601 is unambiguous in both.
 */

// =============================================================================
// Enumerations
// =============================================================================

/**
 * How serious it is to miss a medication.
 *
 * This is the most important field in the whole model. It is what turns
 * escalation into a judgement rather than a counter: missing two doses of an
 * anticoagulant is a genuine clinical risk, while missing two doses of a statin
 * is not. The reasoning engine weights every pattern by this.
 */
export type Criticality = 'critical' | 'high' | 'medium' | 'low';

/** Starting concern thresholds. The engine may move off these — see reasoning/. */
export const CRITICALITY_THRESHOLD: Record<Criticality, number> = {
  critical: 1,
  high: 2,
  medium: 3,
  low: 4,
};

/**
 * How many unanswered calls in a row before the agent stops calling and tells
 * the family.
 *
 * Separate from CRITICALITY_THRESHOLD on purpose. That one counts *missed
 * doses*, and reusing it here meant critical sat at 1, so a single unanswered
 * ring escalated immediately and the quick-retry ladder for critical
 * medications was unreachable code. One missed call is someone in the garden,
 * not an emergency; the right response is to try again in ten minutes and then
 * escalate if that also fails.
 */
export const NO_ANSWER_PATIENCE: Record<Criticality, number> = {
  critical: 2,
  high: 3,
  medium: 3,
  low: 4,
};

/** Relative weight a missed dose contributes to the concern score. */
export const CRITICALITY_WEIGHT: Record<Criticality, number> = {
  critical: 1.0,
  high: 0.6,
  medium: 0.35,
  low: 0.15,
};

export type CheckInStatus = 'completed' | 'missed_dose' | 'no_answer' | 'escalated';

export type CallOutcome = 'pending' | 'answered' | 'declined' | 'missed' | 'undeliverable';

export type VitalType = 'blood_sugar' | 'blood_pressure' | 'heart_rate' | 'weight';

export type InteractionSeverity = 'severe' | 'moderate' | 'minor';

export type EscalationSeverity = 'fyi' | 'concern' | 'urgent';

export type ElderResponse = 'not_yet_seen' | 'confirmed' | 'disputed';

/**
 * Confidence expressed as words, never a percentage.
 *
 * Research on explainability for non-technical readers is consistent: lay users
 * distrust false precision. "78.3% confident" reads as evasive where "I'm quite
 * sure" reads as honest.
 */
export type Confidence = 'high' | 'medium' | 'low';

export const CONFIDENCE_PHRASE: Record<Confidence, string> = {
  high: "I'm quite sure",
  medium: "I'm fairly confident",
  low: "I'm not certain",
};

export type ShareCategory = 'missed_doses' | 'confusion' | 'vitals' | 'refills';

/** What the agent decided to do after weighing a pattern. */
export type AgentAction = 'no_action' | 'retry_soon' | 'retry_later' | 'escalate';

// =============================================================================
// Firestore documents
// =============================================================================

export interface PatientProfile {
  firstName: string;
  lastName: string;
  /** What Cara calls them out loud. Often not their legal first name. */
  preferredName: string;
  age: number;
  /** Condition ids — drive which vitals Cara asks about. */
  conditions: string[];
}

export interface SharingPreferences {
  enabledCategories: ShareCategory[];
  /**
   * The safety floor. Even with every routine category muted, a genuine
   * emergency still reaches family. Shown to the elder plainly rather than
   * buried — hiding it would be exactly the paternalism this feature exists to
   * avoid.
   */
  alwaysShareUrgent: boolean;
  /** ISO-8601. Routine sharing is suppressed until this passes. */
  privacyHoldUntil: string | null;
}

/** `/patients/{patientId}` — patientId is always the elder's auth uid. */
export interface PatientDoc {
  profile: PatientProfile;
  /** Written ONLY by the server. See firestore.rules for why. */
  caretakerIds: string[];
  /** "HH:mm" in the patient's own timezone. The elder owns this setting. */
  dailyCheckInTime: string;
  /** IANA zone, e.g. "Europe/London". */
  timezone: string;
  sharingPreferences: SharingPreferences;
  createdAt: string;
  updatedAt: string;
}

/** `/patients/{patientId}/medications/{medicationId}` */
export interface MedicationDoc {
  name: string;
  dose: string;
  /** Plain language, for the elder. Never "indication" or "MOA". */
  purpose: string;
  /** "HH:mm" entries. */
  schedule: string[];
  criticality: Criticality;
  dosesRemaining: number;
  dosesPerDay: number;
  refillLeadTimeDays: number;
  foodGuidance: string | null;
  /** RxNorm concept id, when we managed to resolve one. */
  rxcui: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TranscriptLine {
  speaker: 'cara' | 'elder';
  text: string;
  offsetSeconds: number;
  flag: 'interaction_check' | 'observation' | 'safety_concern' | null;
}

/**
 * Signals the agent picked up from *how* the person answered, not just what they
 * said. This is the difference between verifying adherence and logging a tap.
 */
export interface ToneSignals {
  /** 0..1 — how uncertain they sounded about their own medication. */
  confusion: number;
  /** 0..1 — hesitation before answering. */
  hesitation: number;
  /** Short plain-language note, e.g. "unsure whether she had taken it". */
  note: string | null;
}

/** `/patients/{patientId}/checkIns/{checkInId}` — server-written. */
export interface CheckInDoc {
  startedAt: string;
  durationSeconds: number;
  status: CheckInStatus;
  medicationsConfirmed: string[];
  medicationsMissed: string[];
  /** Kept short. Full audio is never stored. */
  transcript: TranscriptLine[];
  toneSignals: ToneSignals | null;
  /** Cara's plain-language read. Shown to BOTH the elder and the family. */
  caraSummary: string;
  callAttemptId: string | null;
}

/** `/patients/{patientId}/vitals/{vitalId}` */
export interface VitalDoc {
  type: VitalType;
  value: number;
  /** Diastolic, for blood pressure only. */
  secondaryValue: number | null;
  recordedAt: string;
  source: 'call' | 'manual';
}

/**
 * One step of the agent's reasoning.
 *
 * Every step must be traceable to something that actually happened — a specific
 * call on a specific day, ideally a direct quote. A trace that cannot be traced
 * back is just a plausible-sounding story, and that is precisely what this
 * product claims not to be.
 */
export interface ReasoningStep {
  observation: string;
  evidence: string;
  checkInId: string | null;
}

/** `/patients/{patientId}/escalations/{escalationId}` — server-written. */
export interface EscalationDoc {
  raisedAt: string;
  severity: EscalationSeverity;
  /** One sentence a busy adult child can read in three seconds. */
  headline: string;
  /** The full plain-language explanation, in Cara's voice, to the caretaker. */
  explanation: string;
  /** Capped at 3 for display — more reasons confuse rather than convince. */
  reasoning: ReasoningStep[];
  confidence: Confidence;
  /** What the agent weighed and rejected. Evidence of judgement, not a threshold. */
  alternativesConsidered: string[];
  relatedMedication: string | null;
  /** The computed concern score that crossed the threshold. Audit trail. */
  concernScore: number;
  elderResponse: ElderResponse;
  elderNote: string | null;
  elderRespondedAt: string | null;
  acknowledged: boolean;
  acknowledgedAt: string | null;
}

/** `/patients/{patientId}/sharedItems/{itemId}` — the elder's transparency feed. */
export interface SharedItemDoc {
  sharedAt: string;
  category: ShareCategory;
  /** Written TO the elder, in Cara's voice: "I told Sarah that…" */
  whatCaraSaid: string;
  elderResponse: ElderResponse;
  elderNote: string | null;
  elderRespondedAt: string | null;
  escalationId: string | null;
}

/** `/patients/{patientId}/callAttempts/{attemptId}` — the retry ledger. */
export interface CallAttemptDoc {
  sentAt: string;
  /** 'scheduled' | 'manual' | 'retry' — why this call happened. */
  trigger: 'scheduled' | 'manual' | 'retry';
  outcome: CallOutcome;
  outcomeReportedAt: string | null;
  durationSeconds: number | null;
  /** Which attempt in the current sequence, 1-based. */
  attemptNumber: number;
  /** Set when FCM itself rejected the send (bad token, unregistered device). */
  deliveryError: string | null;
  requestedBy: string | null;
}

/** `/patients/{patientId}/refills/{medicationId}` — derived, server-written. */
export interface RefillDoc {
  medicationName: string;
  dosesRemaining: number;
  daysOfSupplyRemaining: number;
  needsRefill: boolean;
  lastReminderAt: string | null;
  updatedAt: string;
}

/** `/deviceTokens/{uid}` — never readable by a client. */
/**
 * `/patients/{patientId}/agentThreads/{threadId}`
 *
 * Something Cara decided, during a call and on her own initiative, to come back
 * to later.
 *
 * This is the part of the system that is agentic in the strict sense rather than
 * the marketing sense. Everything else the agent does is a reaction inside one
 * episode: it hears something, it looks something up, it answers. A thread is an
 * *intention that outlives the episode*. Cara forms it, it is carried into the
 * system instruction for a future call, she acts on it days later, and she
 * closes it when it is genuinely resolved.
 *
 * Deliberately NOT a free-form memory blob. A summary of everything ever said
 * would be both a privacy problem and useless as a prompt. A thread has to name
 * one thing and say why it is worth revisiting, which keeps the agent's memory
 * legible to the elder and to the family. It is shown on both dashboards as
 * "what Cara is keeping an eye on", because a memory the person cannot see is
 * surveillance.
 */
export interface AgentThreadDoc {
  /** One specific thing, in the elder's own terms. Not a clinical label. */
  topic: string;
  /** Why Cara thought it was worth returning to. Shown verbatim to both sides. */
  why: string;
  status: 'open' | 'resolved';
  raisedAt: string;
  raisedOnCheckInId: string | null;
  /** Cara chooses this. Asking about a sore knee every day is nagging. */
  followUpAfter: string;
  timesRaised: number;
  lastRaisedAt: string | null;
  resolution: string | null;
  resolvedAt: string | null;
}

export interface DeviceTokenDoc {
  token: string;
  platform: 'android' | 'ios' | 'web';
  updatedAt: string;
}

/** `/linkingCodes/{code}` — opaque to all clients. */
export interface LinkingCodeDoc {
  patientUid: string;
  createdAt: string;
  expiresAt: string;
  used: boolean;
  usedAt: string | null;
  usedBy: string | null;
  attempts: number;
}

// =============================================================================
// Interaction data
// =============================================================================

export interface DrugInteraction {
  id: string;
  drugA: string;
  drugB: string;
  severity: InteractionSeverity;
  /** One sentence, plain language. What could actually happen to them. */
  whatItMeans: string;
  /** Why it happens. Explaining the mechanism earns compliance; "avoid" does not. */
  mechanism: string;
  advice: string;
  source: string;
}

export interface FoodInteraction {
  id: string;
  /** Matched case-insensitively against medication names and drug classes. */
  drugNames: string[];
  drugClass: string | null;
  food: string;
  severity: InteractionSeverity;
  whatItMeans: string;
  mechanism: string;
  advice: string;
  source: string;
  /**
   * Set where the popular version of this advice is wrong or overstated, so the
   * agent can correct it rather than repeat it. e.g. warfarin and greens is about
   * *consistency*, not avoidance — telling someone to stop eating vegetables is
   * both wrong and harmful.
   */
  commonMisconception: string | null;
}

// =============================================================================
// Callable function contracts
// =============================================================================

export interface MintLiveTokenRequest {
  patientId: string;
  callAttemptId?: string;
}

export interface MintLiveTokenResponse {
  /** Ephemeral token. Short-lived; never log it. */
  token: string;
  expiresAt: string;
  model: string;
  /** The Live API host the client should connect to. */
  wsHost: string;
  /**
   * Cara's persona and this person's context, built server-side.
   *
   * This travels with the token rather than being baked into the app for two
   * reasons. It contains the patient's medications and Cara's open threads, so
   * it is per-session data, not a constant. And the prompt is the product's
   * actual behaviour: shipping it inside an APK would mean every wording change
   * waits on a store release.
   */
  systemInstruction: string;
  /**
   * Tool declarations, verbatim, for the client's setup frame.
   *
   * Also server-owned, and for a sharper reason: the tools and the backend
   * handlers that service them have to agree exactly. When the client carried
   * its own copy they silently drifted, which is a class of bug that shows up
   * only mid-call.
   */
  tools: unknown[];
  /** Voice and language for the session. */
  voice: { voiceName: string; languageCode: string };
}

export interface TriggerCallRequest {
  patientId: string;
  trigger: 'scheduled' | 'manual' | 'retry';
  attemptNumber?: number;
}

export interface TriggerCallResponse {
  callAttemptId: string;
  delivered: boolean;
  /** Present when delivery failed, so the caller can show something honest. */
  reason: string | null;
}

export interface ReportCallOutcomeRequest {
  patientId: string;
  callAttemptId: string;
  outcome: Exclude<CallOutcome, 'pending' | 'undeliverable'>;
  durationSeconds?: number;
}

export interface CheckInteractionRequest {
  patientId: string;
  /** What the person said they took. Free text — they will say it imprecisely. */
  substance: string;
  kind: 'drug' | 'food';
}

export interface CheckInteractionResponse {
  found: boolean;
  /** Resolved canonical name, when we managed to match one. */
  resolvedName: string | null;
  drugInteractions: DrugInteraction[];
  foodInteractions: FoodInteraction[];
  /** Ready-to-speak sentence for Cara. Empty when nothing was found. */
  spokenSummary: string;
}

export interface SubmitCheckInRequest {
  patientId: string;
  callAttemptId: string;
  durationSeconds: number;
  medicationsConfirmed: string[];
  medicationsMissed: string[];
  transcript: TranscriptLine[];
  toneSignals: ToneSignals | null;
  vitals: Array<{
    type: VitalType;
    value: number;
    secondaryValue?: number;
  }>;
}

export interface SubmitCheckInResponse {
  checkInId: string;
  /** What the reasoning engine decided after ingesting this check-in. */
  action: AgentAction;
  escalationId: string | null;
}

export interface GenerateLinkingCodeResponse {
  code: string;
  expiresAt: string;
}

export interface RedeemLinkingCodeRequest {
  code: string;
}

export interface RedeemLinkingCodeResponse {
  patientId: string;
  patientName: string;
}
