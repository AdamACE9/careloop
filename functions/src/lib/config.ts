import { defineSecret, defineString } from 'firebase-functions/params';

/**
 * Configuration and secrets.
 *
 * `functions.config()` is removed in the current generation — everything here uses
 * `defineSecret` (backed by Google Secret Manager) or `defineString` for
 * non-sensitive values.
 *
 * Two hard rules:
 *   1. A secret's `.value()` may only be read INSIDE a function handler, never at
 *      module load time. Reading it at the top level fails at deploy.
 *   2. None of these ever reach a client. The Gemini key in particular must never
 *      appear in an Android APK or a `NEXT_PUBLIC_*` variable — the whole point of
 *      the ephemeral-token flow is that the key stays here.
 */

// -----------------------------------------------------------------------------
// Secrets
// -----------------------------------------------------------------------------

/**
 * Gemini API key, used server-side ONLY to mint short-lived Live API tokens and
 * to run the reasoning/summarisation calls.
 *
 * Set with:  firebase functions:secrets:set GEMINI_API_KEY
 * Get one at: https://aistudio.google.com/apikey
 */
export const GEMINI_API_KEY = defineSecret('GEMINI_API_KEY');

/**
 * Optional openFDA API key. openFDA works without one, but the unauthenticated
 * limit is low enough that a live demo could plausibly hit it.
 *
 * Free key at: https://open.fda.gov/apis/authentication/
 */
export const OPENFDA_API_KEY = defineSecret('OPENFDA_API_KEY');

// -----------------------------------------------------------------------------
// Non-secret parameters
// -----------------------------------------------------------------------------

/**
 * The Gemini Live model.
 *
 * Deliberately a parameter rather than a hardcoded constant: Live model IDs are
 * still moving (preview suffixes get renamed and retired), and when that happens
 * this must be changeable without a code edit and redeploy of logic.
 *
 * The default targets a native-audio model, because those are the ones that
 * support asynchronous (NON_BLOCKING) function calling — which is what lets Cara
 * check a drug interaction mid-sentence without the call going silent. If you
 * change this to a model without async tool support, the interaction check still
 * works but the conversation will pause while it runs.
 *
 * Verify the current id at https://ai.google.dev/gemini-api/docs/models before a
 * demo. See docs/MORNING_CHECKLIST.md.
 */
export const GEMINI_LIVE_MODEL = defineString('GEMINI_LIVE_MODEL', {
  default: 'gemini-2.5-flash-native-audio-preview-12-2025',
});

/** Model used for non-realtime work: summaries, reasoning traces, diet guidance. */
export const GEMINI_TEXT_MODEL = defineString('GEMINI_TEXT_MODEL', {
  default: 'gemini-2.5-flash',
});

export const GEMINI_API_HOST = 'generativelanguage.googleapis.com';

// -----------------------------------------------------------------------------
// Operational constants
// -----------------------------------------------------------------------------

/**
 * Where the functions run.
 *
 * europe-west1 because Firestore for this project is in the eur3 multi-region,
 * and eur3 is served from europe-west1 and europe-west4. A function in
 * us-central1 would make a transatlantic round trip for every read, and this
 * backend reads Firestore on the critical path of a live phone call, where
 * latency is the difference between Cara answering and Cara pausing.
 *
 * Changing this after a deploy does NOT move the old functions. They keep
 * running in the old region until deleted explicitly, and the clients would
 * then be calling into whichever region they were told about. If this ever
 * changes again, delete the old deployment first.
 *
 * Both clients must agree with this value:
 *   web/src/lib/firebase.ts       getFunctions(app, REGION)
 *   android AppContainer.kt       FirebaseFunctions.getInstance(REGION)
 * The Android SDK defaults to us-central1 when no region is given, so leaving
 * it unspecified there is silently wrong rather than loudly wrong.
 */
export const REGION = 'europe-west1';

/**
 * How long we wait for the device to report a call outcome before treating it as
 * unanswered.
 *
 * A real phone rings for roughly 30 seconds. We allow a margin on top so a slow
 * network round-trip is not misread as "she didn't pick up" — a false "missed
 * call" would feed the escalation engine bad evidence, which is worse than
 * being slightly slow to notice a genuine miss.
 */
export const CALL_OUTCOME_TIMEOUT_SECONDS = 45;

/** Ephemeral Live token lifetime. Kept short; the client asks for one per call. */
export const LIVE_TOKEN_TTL_MINUTES = 15;

/** Retry backoff, in minutes, indexed by attempt number (1-based). */
export const RETRY_SCHEDULE_MINUTES = [10, 30, 90];

export const MAX_CALL_ATTEMPTS_PER_DAY = 4;

/** openFDA/RxNav results are effectively static; cache aggressively. */
export const INTERACTION_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
