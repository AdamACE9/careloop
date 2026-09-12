import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { onCall, HttpsError, type CallableRequest } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { setGlobalOptions } from 'firebase-functions/v2';
import { randomBytes } from 'node:crypto';

import {
  GEMINI_API_KEY,
  OPENFDA_API_KEY,
  GEMINI_LIVE_MODEL,
  GEMINI_API_HOST,
  GEMINI_LIVE_WS_PATH,
  REGION,
  CALL_OUTCOME_TIMEOUT_SECONDS,
  MAX_CALL_ATTEMPTS_PER_DAY,
} from './lib/config.js';
import { hashId, logEvent, logWarn, logError } from './lib/logging.js';
import {
  requireAuth,
  requireString,
  requireNumber,
  requireOneOf,
  requireArray,
  requirePatientSelf,
  requireLinkedOrSelf,
  enforceRateLimit,
} from './lib/validate.js';
import { createAndDeliverCall, registerDeviceToken } from './calls/deliver.js';
import { mintLiveToken, GeminiTokenError, reserveLiveSessionSlot } from './gemini/liveToken.js';
import { buildCaraSystemInstruction, CARA_TOOLS, CARA_VOICE_CONFIG } from './gemini/cara.js';
import { checkDrugInteractions } from './interactions/drugs.js';
import { findFoodInteractionsByFood, findFoodInteractionsForList } from './interactions/foodRules.js';
import { reason, type ReasoningInput } from './reasoning/engine.js';
import type {
  CheckInDoc,
  MedicationDoc,
  PatientDoc,
  VitalDoc,
  EscalationDoc,
  CallAttemptDoc,
  TranscriptLine,
  SharedItemDoc,
  VitalType,
  AgentThreadDoc,
} from './types.js';

initializeApp();

setGlobalOptions({
  region: REGION,
  // Free-tier friendly. A single instance handles many concurrent requests in
  // v2, so this is enough for a pilot without risking a runaway bill.
  maxInstances: 10,
});

const db = () => getFirestore();

// =============================================================================
// Device registration
// =============================================================================

/**
 * Registers this device's FCM token.
 *
 * Called on app start and from `onNewToken`. Tokens rotate on reinstall, data
 * clear and device restore, and a stale token fails *silently* — FCM accepts the
 * send and nothing rings. Refreshing on every launch is the cheap insurance.
 */
export const registerDevice = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const token = requireString(request.data?.token, 'token', { max: 4096 });
  const platform = requireOneOf(
    request.data?.platform ?? 'android',
    'platform',
    ['android', 'ios', 'web'] as const,
  );

  await registerDeviceToken(caller.uid, token, platform);
  return { ok: true };
});

// =============================================================================
// Gemini Live
// =============================================================================

/**
 * Mints a short-lived token so the app can open its own Live WebSocket.
 *
 * The API key never leaves the server. See gemini/liveToken.ts for why this
 * design rather than shipping the key or relaying audio.
 */
export const mintLiveSessionToken = onCall(
  { secrets: [GEMINI_API_KEY] },
  async (request: CallableRequest) => {
    const caller = requireAuth(request);
    const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
    requirePatientSelf(caller, patientId);

    await enforceRateLimit(`live:${caller.uid}`, { limit: 12, windowSeconds: 3600 });

    // Free-tier concurrency is tight. Better to know now than to have the person
    // answer the phone and find Cara cannot speak.
    const slot = await reserveLiveSessionSlot(db(), 3);
    if (!slot) {
      logWarn('gemini.live.slot_unavailable', { patientHash: hashId(patientId) });
      throw new HttpsError(
        'resource-exhausted',
        'BUSY: too many live sessions right now. Try again in a minute.',
      );
    }

    const model = GEMINI_LIVE_MODEL.value();

    try {
      const { token, expiresAt } = await mintLiveToken(GEMINI_API_KEY.value(), model);

      // Built here rather than shipped in the app. Until this existed, cara.ts
      // was dead code and the model ran with no persona at all: the whole
      // character, every research-backed rule in it, and the tool declarations
      // reached nothing. The call worked and Cara was a stranger.
      const [patient, medications, recentContext, openThreads] = await Promise.all([
        loadPatient(patientId),
        loadMedications(patientId),
        loadRecentContext(patientId),
        loadOpenThreads(patientId),
      ]);

      return {
        token,
        expiresAt: expiresAt.toISOString(),
        model,
        wsHost: GEMINI_API_HOST,
        wsPath: GEMINI_LIVE_WS_PATH.value(),
        systemInstruction: buildCaraSystemInstruction({
          patient,
          medications,
          recentContext,
          openThreads,
          caretakerFirstName: null,
        }),
        tools: CARA_TOOLS,
        voice: CARA_VOICE_CONFIG,
      };
    } catch (error) {
      // An HttpsError raised deeper in this handler already says exactly what
      // went wrong. Relabelling it as TOKEN_MINT_FAILED actively misleads:
      // a missing patient record was reported for hours as a Gemini problem,
      // and sent the search to the wrong system entirely.
      if (error instanceof HttpsError) throw error;

      if (error instanceof GeminiTokenError) {
        // Distinct codes so the app can say something honest and specific rather
        // than a generic failure. "We've hit today's limit" is a very different
        // message from "something went wrong".
        throw new HttpsError(
          error.code === 'QUOTA_EXHAUSTED' ? 'resource-exhausted' : 'internal',
          error.code,
        );
      }
      throw new HttpsError('internal', 'TOKEN_MINT_FAILED');
    }
  },
);

// =============================================================================
// Calls
// =============================================================================

async function loadPatient(patientId: string): Promise<PatientDoc> {
  const snap = await db().doc(`patients/${patientId}`).get();
  if (!snap.exists) throw new HttpsError('not-found', 'Patient not found.');
  return snap.data() as PatientDoc;
}

async function loadMedications(patientId: string): Promise<MedicationDoc[]> {
  const snap = await db().collection(`patients/${patientId}/medications`).get();
  return snap.docs.map((d) => ({ ...(d.data() as MedicationDoc), id: d.id })) as MedicationDoc[];
}

/**
 * Short factual notes from recent calls, for continuity.
 *
 * Deliberately Cara's own summaries rather than transcripts. A transcript would
 * blow the context budget, and more importantly it would put the person's exact
 * words back into a prompt days later, which is a different and worse privacy
 * bargain than a one-line summary they can read on their own screen.
 */
async function loadRecentContext(patientId: string): Promise<string[]> {
  const snap = await db()
    .collection(`patients/${patientId}/checkIns`)
    .orderBy('startedAt', 'desc')
    .limit(4)
    .get();

  return snap.docs
    .map((d) => (d.data() as CheckInDoc).caraSummary)
    .filter((summary): summary is string => Boolean(summary && summary.trim()));
}

/** Threads Cara opened herself and has not closed. */
async function loadOpenThreads(patientId: string): Promise<AgentThreadDoc[]> {
  const snap = await db()
    .collection(`patients/${patientId}/agentThreads`)
    .where('status', '==', 'open')
    .orderBy('raisedAt', 'desc')
    .limit(8)
    .get();

  return snap.docs.map((d) => ({ ...(d.data() as AgentThreadDoc), id: d.id }));
}

async function countAttemptsToday(patientId: string): Promise<number> {
  const since = new Date();
  since.setHours(0, 0, 0, 0);
  const snap = await db()
    .collection(`patients/${patientId}/callAttempts`)
    .where('sentAt', '>=', since.toISOString())
    .get();
  return snap.size;
}

/**
 * Fires an incoming call.
 *
 * Used by the dashboard's "check on them now" button and by the scheduler. Both
 * go through exactly the same path deliberately: a manual call must exercise the
 * same code as a scheduled one, or the button becomes a demo feature that hides
 * bugs in the real flow.
 */
export const triggerCall = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  const trigger = requireOneOf(
    request.data?.trigger ?? 'manual',
    'trigger',
    ['scheduled', 'manual', 'retry'] as const,
  );

  // A caretaker may request a call; only the server schedules one.
  await requireLinkedOrSelf(caller, patientId);
  await enforceRateLimit(`call:${patientId}`, { limit: 6, windowSeconds: 3600 });

  const attemptsToday = await countAttemptsToday(patientId);
  if (attemptsToday >= MAX_CALL_ATTEMPTS_PER_DAY) {
    throw new HttpsError(
      'resource-exhausted',
      'DAILY_LIMIT: this is the fourth call today. Give them a bit of space.',
    );
  }

  const patient = await loadPatient(patientId);

  const { callAttemptId, result } = await createAndDeliverCall({
    patientId,
    trigger,
    callerName: 'Cara',
    attemptNumber: attemptsToday + 1,
    requestedBy: caller.uid === patientId ? null : caller.uid,
  });

  return {
    callAttemptId,
    delivered: result.delivered,
    reason: result.reason,
    patientName: patient.profile.preferredName,
  };
});

/**
 * The device reporting what happened.
 *
 * FCM cannot tell us whether someone answered — it only knows whether it handed
 * the message to a device. Everything the retry and escalation logic knows about
 * "she didn't pick up" comes from here, which is why the sweep below treats
 * silence as its own signal rather than assuming success.
 */
export const reportCallOutcome = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  const callAttemptId = requireString(request.data?.callAttemptId, 'callAttemptId', { max: 128 });
  const outcome = requireOneOf(
    request.data?.outcome,
    'outcome',
    ['answered', 'declined', 'missed'] as const,
  );
  const durationSeconds = request.data?.durationSeconds === undefined
    ? null
    : requireNumber(request.data.durationSeconds, 'durationSeconds', { min: 0, max: 7200 });

  requirePatientSelf(caller, patientId);

  await db().doc(`patients/${patientId}/callAttempts/${callAttemptId}`).update({
    outcome,
    outcomeReportedAt: new Date().toISOString(),
    durationSeconds,
  });

  logEvent('call.outcome.reported', {
    patientHash: hashId(patientId),
    callAttemptId,
    outcome,
  });

  return { ok: true };
});

// =============================================================================
// Interaction checking (called mid-conversation)
// =============================================================================

/**
 * Checks something the person just mentioned against what they already take.
 *
 * This is invoked by Cara's `check_interaction` tool while the call is live, so
 * latency is a product concern, not just an engineering one. Curated rules
 * resolve instantly; network lookups are cached and time-limited so a slow
 * openFDA response degrades to "what we know offline" rather than a silent gap
 * in the conversation.
 */
/**
 * Cara opening a thread on herself.
 *
 * Rate limited, because the failure mode of a model with a memory tool is
 * writing one every time anything is mentioned, which turns the next call's
 * prompt into a wall of stale trivia and the dashboard into noise.
 *
 * The follow-up interval is clamped rather than trusted. A model that picks 0
 * would make Cara raise the same topic on the very next call, which is exactly
 * the nagging behaviour the prompt tells her to avoid, and 365 would mean the
 * thread is never seen again.
 */
/**
 * Cara escalating in the moment, mid-call.
 *
 * Everything else the agent reports goes through the reasoning engine at the end
 * of a call, which is right for patterns and wrong for chest pain. This path is
 * deliberately immediate and deliberately unscored: there is no threshold to
 * weigh, because the model has already been told to say "call 999 first" out
 * loud before it calls this.
 *
 * It writes the escalation and the elder's own copy in one batch, like every
 * other escalation, because an alert the family can see and the person cannot is
 * the asymmetry this product exists to avoid.
 */
export const reportUrgentConcern = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  const description = requireString(request.data?.whatTheyDescribed, 'whatTheyDescribed', {
    max: 500,
  });

  requirePatientSelf(caller, patientId);
  await enforceRateLimit(`urgent:${caller.uid}`, { limit: 5, windowSeconds: 3600 });

  const patient = await loadPatient(patientId);
  const name = patient.profile.preferredName;
  const now = new Date().toISOString();

  const escalationRef = db().collection(`patients/${patientId}/escalations`).doc();
  const escalation: EscalationDoc = {
    raisedAt: now,
    severity: 'urgent',
    headline: `${name} described something that needs attention now`,
    explanation:
      `During today's call ${name} described: "${description}". I told them to contact ` +
      'emergency services first, and I am telling you straight away rather than waiting ' +
      'for the end of the day. I have not assessed this and I am not able to.',
    reasoning: [
      {
        observation: 'Described a symptom during the call that I am not able to assess',
        evidence: description,
        checkInId: null,
      },
    ],
    confidence: 'high',
    alternativesConsidered: [
      'Waiting for the end-of-call summary, which would have delayed this by minutes',
    ],
    relatedMedication: null,
    concernScore: 99,
    elderResponse: 'not_yet_seen',
    elderNote: null,
    elderRespondedAt: null,
    acknowledged: false,
    acknowledgedAt: null,
  };

  const sharedRef = db().collection(`patients/${patientId}/sharedItems`).doc();
  const shared: SharedItemDoc = {
    sharedAt: now,
    category: 'confusion',
    whatCaraSaid: `I let your family know straight away that you mentioned: "${description}".`,
    escalationId: escalationRef.id,
    elderResponse: 'not_yet_seen',
    elderNote: null,
    elderRespondedAt: null,
  };

  const batch = db().batch();
  batch.set(escalationRef, escalation);
  batch.set(sharedRef, shared);
  await batch.commit();

  logEvent('agent.urgent_reported', { patientHash: hashId(patientId) });
  return { escalationId: escalationRef.id };
});

export const rememberForNextTime = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  const topic = requireString(request.data?.topic, 'topic', { max: 160 });
  const why = requireString(request.data?.why, 'why', { max: 400 });
  const rawDays = requireNumber(request.data?.followUpInDays ?? 2, 'followUpInDays');

  requirePatientSelf(caller, patientId);
  await enforceRateLimit(`thread:${caller.uid}`, { limit: 8, windowSeconds: 3600 });

  const days = Math.min(30, Math.max(1, Math.round(rawDays)));
  const now = new Date();
  const followUpAfter = new Date(now.getTime() + days * 86_400_000);

  const existing = await db()
    .collection(`patients/${patientId}/agentThreads`)
    .where('status', '==', 'open')
    .get();

  // Cheap de-duplication. The model will not reliably remember it already
  // opened a thread for the same thing, and two near-identical entries read as
  // a bug to anyone looking at the dashboard.
  const normalised = topic.trim().toLowerCase();
  const duplicate = existing.docs.find(
    (d) => (d.data() as AgentThreadDoc).topic.trim().toLowerCase() === normalised,
  );
  if (duplicate) {
    return { threadId: duplicate.id, created: false };
  }

  const doc: AgentThreadDoc = {
    topic,
    why,
    status: 'open',
    raisedAt: now.toISOString(),
    raisedOnCheckInId: null,
    followUpAfter: followUpAfter.toISOString(),
    timesRaised: 0,
    lastRaisedAt: null,
    resolution: null,
    resolvedAt: null,
  };

  const ref = await db().collection(`patients/${patientId}/agentThreads`).add(doc);
  logEvent('agent.thread_opened', { patientHash: hashId(patientId), days });
  return { threadId: ref.id, created: true };
});

/** Cara deciding something is finished. */
export const closeOpenThread = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  const topic = requireString(request.data?.topic, 'topic', { max: 160 });
  const whatHappened = requireString(request.data?.whatHappened, 'whatHappened', { max: 400 });

  requirePatientSelf(caller, patientId);

  const open = await db()
    .collection(`patients/${patientId}/agentThreads`)
    .where('status', '==', 'open')
    .get();

  // Matched on the topic text rather than an id: the model is given topics, not
  // document ids, and asking it to echo an opaque id back correctly mid-call is
  // a needless failure point.
  const normalised = topic.trim().toLowerCase();
  const match = open.docs.find(
    (d) => (d.data() as AgentThreadDoc).topic.trim().toLowerCase() === normalised,
  );

  if (!match) {
    // Not an error. Cara closing something already closed is harmless, and
    // throwing would surface as a tool failure mid-conversation.
    return { closed: false, reason: 'NO_MATCHING_THREAD' };
  }

  await match.ref.update({
    status: 'resolved',
    resolution: whatHappened,
    resolvedAt: new Date().toISOString(),
  });

  logEvent('agent.thread_closed', { patientHash: hashId(patientId) });
  return { closed: true };
});

export const checkInteraction = onCall(
  { secrets: [OPENFDA_API_KEY] },
  async (request: CallableRequest) => {
    const caller = requireAuth(request);
    const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
    const substance = requireString(request.data?.substance, 'substance', { max: 200 });
    const kind = requireOneOf(request.data?.kind ?? 'drug', 'kind', ['drug', 'food'] as const);

    requirePatientSelf(caller, patientId);

    const medications = await loadMedications(patientId);
    const medicationNames = medications.map((m) => m.name);

    if (kind === 'food') {
      const foodInteractions = findFoodInteractionsByFood(substance, medicationNames);
      return {
        found: foodInteractions.length > 0,
        resolvedName: substance,
        drugInteractions: [],
        foodInteractions,
        spokenSummary: summariseForSpeech([], foodInteractions),
      };
    }

    let openFdaKey: string | null = null;
    try {
      openFdaKey = OPENFDA_API_KEY.value() || null;
    } catch {
      // Optional secret. openFDA works unauthenticated, just with a lower limit.
    }

    const { resolvedName, interactions } = await checkDrugInteractions({
      substance,
      currentMedications: medicationNames,
      openFdaApiKey: openFdaKey,
    });

    const foodInteractions = findFoodInteractionsByFood(substance, medicationNames);

    return {
      found: interactions.length > 0 || foodInteractions.length > 0,
      resolvedName,
      drugInteractions: interactions,
      foodInteractions,
      spokenSummary: summariseForSpeech(interactions, foodInteractions),
    };
  },
);

/**
 * Turns findings into one or two sentences Cara can actually say.
 *
 * Deliberately terse. Reading a list of five interactions aloud to an 84-year-old
 * on a phone is useless: they will remember none of it. The most serious finding,
 * with its mechanism, is what lands.
 */
function summariseForSpeech(
  drug: Array<{ drugA: string; drugB: string; whatItMeans: string; mechanism: string; severity: string }>,
  food: Array<{ food: string; whatItMeans: string; mechanism: string; severity: string }>,
): string {
  const order = { severe: 0, moderate: 1, minor: 2 } as Record<string, number>;
  const all = [
    ...drug.map((d) => ({
      severity: d.severity,
      text: `${d.whatItMeans} ${d.mechanism}`,
    })),
    ...food.map((f) => ({
      severity: f.severity,
      text: `${f.whatItMeans} ${f.mechanism}`,
    })),
  ].sort((a, b) => (order[a.severity] ?? 3) - (order[b.severity] ?? 3));

  if (!all.length) return '';
  return all[0]!.text;
}

/** Diet guidance for the whole medication list, for the app's medication screen. */
export const getDietGuidance = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  await requireLinkedOrSelf(caller, patientId);

  const medications = await loadMedications(patientId);
  const interactions = findFoodInteractionsForList(medications.map((m) => m.name));

  return { interactions };
});

// =============================================================================
// Check-in submission and reasoning
// =============================================================================

/**
 * Records a completed call and decides what to do about it.
 *
 * This is where the agent's autonomy actually lives: the reasoning engine looks
 * at this check-in in the context of the preceding week, weighs it by how serious
 * each medication is, and independently decides whether to say nothing, call back,
 * or tell the family — writing down its reasoning either way.
 */
export const submitCheckIn = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  requirePatientSelf(caller, patientId);

  const callAttemptId = requireString(request.data?.callAttemptId, 'callAttemptId', { max: 128 });
  const durationSeconds = requireNumber(request.data?.durationSeconds, 'durationSeconds', {
    min: 0,
    max: 7200,
  });

  const medicationsConfirmed = requireArray(
    request.data?.medicationsConfirmed ?? [],
    'medicationsConfirmed',
    (item) => requireString(item, 'medication', { max: 120 }),
    { max: 50 },
  );
  const medicationsMissed = requireArray(
    request.data?.medicationsMissed ?? [],
    'medicationsMissed',
    (item) => requireString(item, 'medication', { max: 120 }),
    { max: 50 },
  );

  const transcript = requireArray(
    request.data?.transcript ?? [],
    'transcript',
    (item): TranscriptLine => {
      const line = item as Record<string, unknown>;
      return {
        speaker: requireOneOf(line.speaker, 'speaker', ['cara', 'elder'] as const),
        text: requireString(line.text, 'text', { max: 2000 }),
        offsetSeconds: requireNumber(line.offsetSeconds ?? 0, 'offsetSeconds', {
          min: 0,
          max: 7200,
        }),
        flag: line.flag === undefined || line.flag === null
          ? null
          : requireOneOf(line.flag, 'flag', [
              'interaction_check', 'observation', 'safety_concern',
            ] as const),
      };
    },
    { max: 400 },
  );

  const toneRaw = request.data?.toneSignals as Record<string, unknown> | undefined | null;
  const toneSignals = toneRaw
    ? {
        confusion: requireNumber(toneRaw.confusion ?? 0, 'confusion', { min: 0, max: 1 }),
        hesitation: requireNumber(toneRaw.hesitation ?? 0, 'hesitation', { min: 0, max: 1 }),
        note: toneRaw.note ? requireString(toneRaw.note, 'note', { max: 500 }) : null,
      }
    : null;

  const vitals = requireArray(
    request.data?.vitals ?? [],
    'vitals',
    (item) => {
      const v = item as Record<string, unknown>;
      return {
        type: requireOneOf(v.type, 'type', [
          'blood_sugar', 'blood_pressure', 'heart_rate', 'weight',
        ] as const) as VitalType,
        value: requireNumber(v.value, 'value', { min: 0, max: 1000 }),
        secondaryValue: v.secondaryValue === undefined || v.secondaryValue === null
          ? null
          : requireNumber(v.secondaryValue, 'secondaryValue', { min: 0, max: 1000 }),
      };
    },
    { max: 10 },
  );

  const now = new Date().toISOString();
  const patient = await loadPatient(patientId);
  const medications = await loadMedications(patientId);

  // --- Write the check-in -------------------------------------------------
  const checkInRef = db().collection(`patients/${patientId}/checkIns`).doc();
  const checkIn: CheckInDoc = {
    startedAt: now,
    durationSeconds,
    status: medicationsMissed.length > 0 ? 'missed_dose' : 'completed',
    medicationsConfirmed,
    medicationsMissed,
    transcript,
    toneSignals,
    caraSummary: buildCallSummary(medicationsConfirmed, medicationsMissed, toneSignals),
    callAttemptId,
  };
  await checkInRef.set(checkIn);

  // --- Vitals -------------------------------------------------------------
  const batch = db().batch();
  for (const v of vitals) {
    const ref = db().collection(`patients/${patientId}/vitals`).doc();
    const doc: VitalDoc = {
      type: v.type,
      value: v.value,
      secondaryValue: v.secondaryValue,
      recordedAt: now,
      source: 'call',
    };
    batch.set(ref, doc);
  }
  await batch.commit();

  // --- Decrement dose supply ---------------------------------------------
  await decrementDoses(patientId, medicationsConfirmed, medications);

  // --- Reason -------------------------------------------------------------
  const outcome = await runReasoning(patientId, patient, medications);

  let escalationId: string | null = null;
  if (outcome.action === 'escalate') {
    escalationId = await writeEscalation(patientId, patient, outcome);
    await checkInRef.update({ status: 'escalated' });
  }

  logEvent('checkin.submitted', {
    patientHash: hashId(patientId),
    action: outcome.action,
    missedCount: medicationsMissed.length,
    escalated: escalationId !== null,
  });

  return {
    checkInId: checkInRef.id,
    action: outcome.action,
    escalationId,
  };
});

/**
 * Cara's one-line account of the call.
 *
 * The three cases are genuinely different and were collapsed into two. With
 * nothing missed this returned "All medications taken." even when nothing had
 * been confirmed either, so a call that ended before any medication was
 * discussed, or where the line was too poor to make anything out, was written
 * into the health record as a day the person took everything. That is the agent
 * asserting something it does not know, on the record its own escalation
 * reasoning is later built from, which is the precise failure this product
 * claims to avoid.
 *
 * The reasoning engine was never fooled: it only ever scores explicit misses,
 * so an unestablished call is neutral to it rather than a clean day. The lie was
 * confined to what the family and the elder were shown, which is arguably worse,
 * because that is the part a person acts on.
 */
function buildCallSummary(
  confirmed: string[],
  missed: string[],
  tone: { confusion: number; note: string | null } | null,
): string {
  if (!missed.length && !confirmed.length) {
    return tone?.note
      ? `We spoke, but did not get through the medications. ${tone.note}.`
      : 'We spoke, but did not get through the medications.';
  }

  if (!missed.length) {
    return tone?.note
      ? `All medications taken. ${tone.note}.`
      : 'All medications taken.';
  }
  const list = missed.join(' and ');
  const unsure = (tone?.confusion ?? 0) > 0.4;
  return unsure
    ? `${list} missed, and there was some uncertainty about whether it had been taken.`
    : `${list} missed.`;
}

/**
 * Loads the recent history and runs the engine.
 *
 * Deliberately reads a week of history on every check-in rather than keeping a
 * running score. Recomputing from source means a corrected or deleted record
 * immediately changes the conclusion, and there is no drifting cached number that
 * can silently disagree with the evidence shown to the family.
 */
async function runReasoning(
  patientId: string,
  patient: PatientDoc,
  medications: MedicationDoc[],
) {
  const weekAgo = new Date(Date.now() - 7 * 86_400_000).toISOString();

  const [checkInsSnap, vitalsSnap, attemptsSnap] = await Promise.all([
    db().collection(`patients/${patientId}/checkIns`)
      .where('startedAt', '>=', weekAgo)
      .orderBy('startedAt', 'desc')
      .limit(30)
      .get(),
    db().collection(`patients/${patientId}/vitals`)
      .where('recordedAt', '>=', weekAgo)
      .limit(60)
      .get(),
    db().collection(`patients/${patientId}/callAttempts`)
      .orderBy('sentAt', 'desc')
      .limit(10)
      .get(),
  ]);

  const recentCheckIns = checkInsSnap.docs.map(
    (d) => ({ ...(d.data() as CheckInDoc), id: d.id }),
  ) as Array<CheckInDoc & { id: string }>;

  const recentVitals = vitalsSnap.docs.map((d) => d.data() as VitalDoc);

  // Count consecutive unanswered attempts from most recent backwards.
  let consecutiveNoAnswers = 0;
  for (const doc of attemptsSnap.docs) {
    const attempt = doc.data() as CallAttemptDoc;
    if (attempt.outcome === 'missed' || attempt.outcome === 'undeliverable') {
      consecutiveNoAnswers += 1;
    } else if (attempt.outcome === 'answered') {
      break;
    }
  }

  const input: ReasoningInput = {
    patientName: patient.profile.preferredName,
    caretakerName: 'your family',
    medications,
    recentCheckIns,
    recentVitals,
    consecutiveNoAnswers,
    attemptsToday: await countAttemptsToday(patientId),
  };

  return reason(input);
}

/**
 * Writes the escalation and, in the same breath, tells the elder it happened.
 *
 * These two writes are deliberately coupled. The whole dignity position collapses
 * if an escalation can ever exist without a corresponding entry in the elder's
 * "what I shared" feed — that would be exactly the asymmetric surveillance the
 * product claims not to be. If you refactor this, keep them together.
 */
async function writeEscalation(
  patientId: string,
  patient: PatientDoc,
  outcome: Awaited<ReturnType<typeof runReasoning>>,
): Promise<string> {
  const now = new Date().toISOString();

  const escalationRef = db().collection(`patients/${patientId}/escalations`).doc();
  const escalation: EscalationDoc = {
    raisedAt: now,
    severity: outcome.severity,
    headline: outcome.headline,
    explanation: outcome.explanation,
    reasoning: outcome.reasoning,
    confidence: outcome.confidence,
    alternativesConsidered: outcome.alternativesConsidered,
    relatedMedication: outcome.relatedMedication,
    concernScore: Math.round(outcome.concernScore * 100) / 100,
    elderResponse: 'not_yet_seen',
    elderNote: null,
    elderRespondedAt: null,
    acknowledged: false,
    acknowledgedAt: null,
  };

  const sharedRef = db().collection(`patients/${patientId}/sharedItems`).doc();
  const shared: SharedItemDoc = {
    sharedAt: now,
    category: outcome.relatedMedication ? 'missed_doses' : 'confusion',
    // Written to the elder, in Cara's voice, describing what was actually sent.
    whatCaraSaid: buildElderFacingSummary(outcome.headline, patient),
    elderResponse: 'not_yet_seen',
    elderNote: null,
    elderRespondedAt: null,
    escalationId: escalationRef.id,
  };

  const batch = db().batch();
  batch.set(escalationRef, escalation);
  batch.set(sharedRef, shared);
  await batch.commit();

  logEvent('escalation.raised', {
    patientHash: hashId(patientId),
    severity: outcome.severity,
    confidence: outcome.confidence,
    score: escalation.concernScore,
  });

  return escalationRef.id;
}

function buildElderFacingSummary(headline: string, patient: PatientDoc): string {
  const name = patient.profile.preferredName;
  // Rewrite the third-person headline into something addressed to the elder.
  const rewritten = headline
    .replace(new RegExp(`^${name}\\s+`, 'i'), 'you ')
    .replace(/\bher\b/gi, 'your')
    .replace(/\bshe\b/gi, 'you');
  return `I let your family know that ${rewritten}.`;
}

async function decrementDoses(
  patientId: string,
  confirmed: string[],
  medications: MedicationDoc[],
): Promise<void> {
  if (!confirmed.length) return;
  const batch = db().batch();

  for (const name of confirmed) {
    const med = medications.find(
      (m) => m.name.toLowerCase() === name.toLowerCase(),
    ) as (MedicationDoc & { id?: string }) | undefined;
    if (!med?.id) continue;

    batch.update(db().doc(`patients/${patientId}/medications/${med.id}`), {
      dosesRemaining: FieldValue.increment(-1),
      updatedAt: new Date().toISOString(),
    });
  }
  await batch.commit();
}

// =============================================================================
// Caretaker linking
// =============================================================================

/**
 * Generates a one-time code the elder (or whoever is setting up their phone)
 * types in to link a caretaker.
 *
 * Codes are 8 characters from a deliberately reduced alphabet: no O/0, no I/1/L.
 * Someone is going to read this aloud over a telephone to a 78-year-old, and
 * "was that an O or a zero" is a real failure mode, not a hypothetical one.
 */
export const generateLinkingCode = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  await enforceRateLimit(`linkgen:${caller.uid}`, { limit: 5, windowSeconds: 3600 });

  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  requirePatientSelf(caller, patientId);

  const code = generateReadableCode(8);
  const expiresAt = new Date(Date.now() + 24 * 3600 * 1000);

  await db().doc(`linkingCodes/${code}`).set({
    patientUid: patientId,
    createdAt: new Date().toISOString(),
    expiresAt: expiresAt.toISOString(),
    used: false,
    usedAt: null,
    usedBy: null,
    attempts: 0,
  });

  logEvent('link.code_generated', { patientHash: hashId(patientId) });
  return { code, expiresAt: expiresAt.toISOString() };
});

const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

function generateReadableCode(length: number): string {
  const bytes = randomBytes(length);
  let out = '';
  for (let i = 0; i < length; i += 1) {
    out += CODE_ALPHABET[bytes[i]! % CODE_ALPHABET.length];
  }
  return out;
}

/**
 * Redeems a code, linking the caller as a caretaker.
 *
 * Rate limited hard. Eight characters from a 31-symbol alphabet is a large space,
 * but a code that grants read access to someone's health record deserves more
 * than entropy alone — an attacker gets a handful of guesses per hour, not
 * unlimited ones.
 */
/**
 * Removes a caretaker's access to a patient.
 *
 * This did not exist, which was a real hole rather than a missing convenience.
 * A product whose whole ethical position is that the elder stays in control had
 * a one-way door: a family member could be granted sight of somebody's health
 * record and there was no way, anywhere in the product, to take it back. Consent
 * you cannot withdraw is not consent.
 *
 * Two callers are allowed, for different reasons:
 *
 *   - The ELDER may remove anyone. It is their record. They do not have to
 *     justify it and nothing here asks them to.
 *   - A CARETAKER may remove THEMSELVES, and only themselves. Someone who no
 *     longer wants the responsibility should be able to step back without
 *     asking a 78-year-old to work out how to do it for them. Letting a
 *     caretaker remove a DIFFERENT caretaker would let one family member
 *     quietly cut another out, which is a family dispute this software has no
 *     business adjudicating.
 *
 * The elder is told either way. An access change happening silently is the same
 * failure as an escalation happening silently: see D12.
 */
export const unlinkCaretaker = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  await enforceRateLimit(`unlink:${caller.uid}`, { limit: 20, windowSeconds: 3600 });

  const patientId = requireString(request.data?.patientId, 'patientId', { max: 128 });
  const caretakerId = requireString(request.data?.caretakerId, 'caretakerId', { max: 128 });

  const isPatient = caller.uid === patientId;
  const isSelfRemoval = caller.uid === caretakerId;
  if (!isPatient && !isSelfRemoval) {
    throw new HttpsError('permission-denied', 'NOT_ALLOWED');
  }

  const patientRef = db().doc(`patients/${patientId}`);

  const removed = await db().runTransaction(async (tx) => {
    const snap = await tx.get(patientRef);
    if (!snap.exists) throw new HttpsError('not-found', 'NO_SUCH_PATIENT');

    const data = snap.data() as { caretakerIds?: string[] };
    const current = data.caretakerIds ?? [];
    if (!current.includes(caretakerId)) {
      // Already gone. Not an error: the caller wanted them not to have access,
      // and they do not.
      return false;
    }

    tx.update(patientRef, {
      caretakerIds: FieldValue.arrayRemove(caretakerId),
      updatedAt: new Date().toISOString(),
    });
    return true;
  });

  if (removed) {
    // The elder's own record of what happened to their data. Written even when
    // the caretaker removed themselves, because "Sarah stopped following you"
    // is exactly the kind of thing that should never be a surprise.
    await db().collection(`patients/${patientId}/sharedItems`).add({
      category: 'access_change',
      whatCaraSaid: isSelfRemoval && !isPatient
        ? 'Someone you had connected has disconnected themselves. They can no longer see your check-ins.'
        : 'You disconnected someone. They can no longer see your check-ins.',
      sharedAt: new Date().toISOString(),
      elderResponse: 'not_yet_seen',
      elderNote: null,
      elderRespondedAt: null,
      escalationId: null,
    });
  }

  logEvent('link.removed', {
    patientHash: hashId(patientId),
    callerHash: hashId(caller.uid),
    selfRemoval: isSelfRemoval,
    wasLinked: removed,
  });

  return { removed };
});

export const redeemLinkingCode = onCall(async (request: CallableRequest) => {
  const caller = requireAuth(request);
  await enforceRateLimit(`linkredeem:${caller.uid}`, { limit: 8, windowSeconds: 3600 });

  const code = requireString(request.data?.code, 'code', { min: 4, max: 16 })
    .toUpperCase()
    .replace(/\s/g, '');

  const codeRef = db().doc(`linkingCodes/${code}`);

  const patientId = await db().runTransaction(async (tx) => {
    const snap = await tx.get(codeRef);
    // Identical error for missing, used and expired: distinguishing them lets an
    // attacker learn which codes exist.
    if (!snap.exists) throw new HttpsError('not-found', 'INVALID_CODE');

    const data = snap.data() as {
      patientUid: string; used: boolean; expiresAt: string;
    };
    if (data.used) throw new HttpsError('not-found', 'INVALID_CODE');
    if (new Date(data.expiresAt) < new Date()) throw new HttpsError('not-found', 'INVALID_CODE');
    if (data.patientUid === caller.uid) {
      throw new HttpsError('failed-precondition', 'CANNOT_LINK_SELF');
    }

    tx.update(codeRef, {
      used: true,
      usedAt: new Date().toISOString(),
      usedBy: caller.uid,
    });
    tx.update(db().doc(`patients/${data.patientUid}`), {
      caretakerIds: FieldValue.arrayUnion(caller.uid),
      updatedAt: new Date().toISOString(),
    });

    return data.patientUid;
  });

  const patient = await loadPatient(patientId);
  logEvent('link.redeemed', {
    patientHash: hashId(patientId),
    callerHash: hashId(caller.uid),
  });

  return { patientId, patientName: patient.profile.preferredName };
});

// =============================================================================
// Scheduled work
// =============================================================================

/**
 * Fires scheduled check-in calls.
 *
 * Runs every five minutes and looks for patients whose local check-in time has
 * just arrived. Doing the timezone comparison per patient rather than running one
 * job per timezone keeps this to a single scheduled function, which matters on a
 * free-tier budget.
 */
export const scheduledCheckInCalls = onSchedule(
  { schedule: 'every 5 minutes', region: REGION },
  async () => {
    const patientsSnap = await db().collection('patients').get();
    const now = new Date();
    let fired = 0;

    for (const doc of patientsSnap.docs) {
      const patient = doc.data() as PatientDoc;
      if (!isCheckInTimeNow(patient, now)) continue;

      const attemptsToday = await countAttemptsToday(doc.id);
      if (attemptsToday >= MAX_CALL_ATTEMPTS_PER_DAY) continue;

      // Don't double-fire if a call already went out in this window.
      const recent = await db()
        .collection(`patients/${doc.id}/callAttempts`)
        .where('sentAt', '>=', new Date(now.getTime() - 20 * 60_000).toISOString())
        .limit(1)
        .get();
      if (!recent.empty) continue;

      await createAndDeliverCall({
        patientId: doc.id,
        trigger: 'scheduled',
        callerName: 'Cara',
        attemptNumber: attemptsToday + 1,
        requestedBy: null,
      });
      fired += 1;
    }

    logEvent('scheduler.calls_fired', { count: fired });
  },
);

/**
 * Whether the patient's local check-in time falls in this five-minute window.
 *
 * Uses `Intl` to get the local hour and minute rather than doing offset
 * arithmetic, so daylight saving is handled by the platform. Getting this wrong
 * would mean calling people an hour early twice a year, which for this audience
 * is genuinely disruptive.
 */
function isCheckInTimeNow(patient: PatientDoc, now: Date): boolean {
  try {
    const formatter = new Intl.DateTimeFormat('en-GB', {
      timeZone: patient.timezone || 'UTC',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
    const local = formatter.format(now); // "09:03"
    const [targetH, targetM] = (patient.dailyCheckInTime || '09:00').split(':').map(Number);
    const [nowH, nowM] = local.split(':').map(Number);
    if (targetH === undefined || nowH === undefined) return false;

    const targetMinutes = targetH * 60 + (targetM ?? 0);
    const nowMinutes = nowH * 60 + (nowM ?? 0);
    const delta = nowMinutes - targetMinutes;
    return delta >= 0 && delta < 5;
  } catch {
    return false;
  }
}

/**
 * Closes out calls nobody answered, and decides what to do about it.
 *
 * A call with no reported outcome is not a neutral event — it is the evidence
 * that someone did not pick up, and the retry/escalation logic depends on it
 * being recorded rather than left pending forever.
 */
export const sweepStaleCalls = onSchedule(
  { schedule: 'every 5 minutes', region: REGION },
  async () => {
    const cutoff = new Date(Date.now() - CALL_OUTCOME_TIMEOUT_SECONDS * 1000).toISOString();

    const stale = await db()
      .collectionGroup('callAttempts')
      .where('outcome', '==', 'pending')
      .where('sentAt', '<=', cutoff)
      .limit(50)
      .get();

    let handled = 0;

    for (const doc of stale.docs) {
      // parent path: patients/{patientId}/callAttempts/{id}
      const patientId = doc.ref.parent.parent?.id;
      if (!patientId) continue;

      await doc.ref.update({
        outcome: 'missed',
        outcomeReportedAt: new Date().toISOString(),
      });

      try {
        const patient = await loadPatient(patientId);
        const medications = await loadMedications(patientId);
        const outcome = await runReasoning(patientId, patient, medications);

        if (outcome.action === 'retry_soon' || outcome.action === 'retry_later') {
          // Scheduling a retry means writing the intent; the next scheduler tick
          // picks it up. We do not sleep inside a function waiting to call back.
          await db().doc(`patients/${patientId}`).set(
            { pendingRetryAt: new Date(Date.now() + (outcome.retryInMinutes ?? 30) * 60_000).toISOString() },
            { merge: true },
          );
        } else if (outcome.action === 'escalate') {
          await writeEscalation(patientId, patient, outcome);
        }
        handled += 1;
      } catch (error) {
        logError('sweep.reasoning_failed', 'REASONING_ERROR', {
          patientHash: hashId(patientId),
        }, error);
      }
    }

    logEvent('scheduler.sweep_complete', { count: handled });
  },
);

/** Fires retries whose scheduled moment has arrived. */
export const fireDueRetries = onSchedule(
  { schedule: 'every 5 minutes', region: REGION },
  async () => {
    const due = await db()
      .collection('patients')
      .where('pendingRetryAt', '<=', new Date().toISOString())
      .limit(25)
      .get();

    for (const doc of due.docs) {
      await doc.ref.update({ pendingRetryAt: FieldValue.delete() });

      const attemptsToday = await countAttemptsToday(doc.id);
      if (attemptsToday >= MAX_CALL_ATTEMPTS_PER_DAY) continue;

      await createAndDeliverCall({
        patientId: doc.id,
        trigger: 'retry',
        callerName: 'Cara',
        attemptNumber: attemptsToday + 1,
        requestedBy: null,
      });
    }

    logEvent('scheduler.retries_fired', { count: due.size });
  },
);

/**
 * Daily refill planning.
 *
 * Proactive rather than reactive: it looks at remaining doses against the lead
 * time a repeat prescription actually takes, and raises a low-severity note
 * *before* supply runs out. Telling someone they have run out is not planning.
 */
export const updateRefillPlanning = onSchedule(
  { schedule: 'every day 08:00', region: REGION, timeZone: 'UTC' },
  async () => {
    const patients = await db().collection('patients').get();
    let raised = 0;

    for (const patientDoc of patients.docs) {
      const patient = patientDoc.data() as PatientDoc;
      const medications = await loadMedications(patientDoc.id);

      for (const med of medications as Array<MedicationDoc & { id?: string }>) {
        if (!med.id) continue;
        const perDay = med.dosesPerDay > 0 ? med.dosesPerDay : 1;
        const daysLeft = Math.floor(med.dosesRemaining / perDay);
        const needsRefill = daysLeft <= med.refillLeadTimeDays;

        const refillRef = db().doc(`patients/${patientDoc.id}/refills/${med.id}`);
        const existing = await refillRef.get();
        const lastReminderAt = existing.get('lastReminderAt') as string | undefined;

        await refillRef.set({
          medicationName: med.name,
          dosesRemaining: med.dosesRemaining,
          daysOfSupplyRemaining: daysLeft,
          needsRefill,
          lastReminderAt: lastReminderAt ?? null,
          updatedAt: new Date().toISOString(),
        }, { merge: true });

        // Remind once per medication per week, not daily. A daily nag about the
        // same prescription is how people learn to ignore the app.
        const remindedRecently = lastReminderAt
          && Date.now() - new Date(lastReminderAt).getTime() < 7 * 86_400_000;

        if (needsRefill && !remindedRecently) {
          await raiseRefillEscalation(patientDoc.id, patient, med, daysLeft);
          await refillRef.update({ lastReminderAt: new Date().toISOString() });
          raised += 1;
        }
      }
    }

    logEvent('scheduler.refills_checked', { count: raised });
  },
);

async function raiseRefillEscalation(
  patientId: string,
  patient: PatientDoc,
  med: MedicationDoc,
  daysLeft: number,
): Promise<void> {
  const now = new Date().toISOString();
  const ref = db().collection(`patients/${patientId}/escalations`).doc();

  const escalation: EscalationDoc = {
    raisedAt: now,
    severity: 'fyi',
    headline: `${med.name} runs out in about ${daysLeft} ${daysLeft === 1 ? 'day' : 'days'}`,
    explanation:
      `Not urgent, but worth starting now. Repeat prescriptions usually take a few days, ` +
      `and ${med.name.toLowerCase()} is not one I would want ${patient.profile.preferredName} ` +
      `to run out of. There are ${med.dosesRemaining} doses left.`,
    reasoning: [
      {
        observation: `${med.dosesRemaining} doses remaining at ${med.dosesPerDay} per day`,
        evidence: 'Counted down from the last recorded refill',
        checkInId: null,
      },
      {
        observation: 'Repeat prescriptions typically take a few working days',
        evidence: `Lead time set to ${med.refillLeadTimeDays} days for this medication`,
        checkInId: null,
      },
    ],
    confidence: 'high',
    alternativesConsidered: [],
    relatedMedication: med.name,
    concernScore: 0,
    elderResponse: 'not_yet_seen',
    elderNote: null,
    elderRespondedAt: null,
    acknowledged: false,
    acknowledgedAt: null,
  };

  await ref.set(escalation);
}
