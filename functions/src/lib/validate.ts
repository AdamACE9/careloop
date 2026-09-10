import { HttpsError, type CallableRequest } from 'firebase-functions/v2/https';
import { getFirestore } from 'firebase-admin/firestore';

/**
 * Input validation and authorisation guards.
 *
 * Every callable entry point runs through these. The app is talking to elderly
 * users about their medication, so a malformed payload must fail cleanly and
 * loudly at the boundary rather than propagating a `undefined` into the reasoning
 * engine and producing a confidently wrong escalation.
 *
 * Error messages are deliberately generic. They are returned to a client, so they
 * must never confirm whether a given patient exists or echo back submitted data.
 */

// -----------------------------------------------------------------------------
// Primitive guards
// -----------------------------------------------------------------------------

export function requireString(
  value: unknown,
  field: string,
  { max = 500, min = 1 }: { max?: number; min?: number } = {},
): string {
  if (typeof value !== 'string') {
    throw new HttpsError('invalid-argument', `${field} must be a string.`);
  }
  const trimmed = value.trim();
  if (trimmed.length < min || trimmed.length > max) {
    throw new HttpsError('invalid-argument', `${field} has an invalid length.`);
  }
  return trimmed;
}

export function requireNumber(
  value: unknown,
  field: string,
  { min = -Infinity, max = Infinity }: { min?: number; max?: number } = {},
): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new HttpsError('invalid-argument', `${field} must be a number.`);
  }
  if (value < min || value > max) {
    throw new HttpsError('invalid-argument', `${field} is out of range.`);
  }
  return value;
}

export function requireOneOf<T extends string>(
  value: unknown,
  field: string,
  allowed: readonly T[],
): T {
  if (typeof value !== 'string' || !allowed.includes(value as T)) {
    throw new HttpsError('invalid-argument', `${field} is not a permitted value.`);
  }
  return value as T;
}

export function optionalString(
  value: unknown,
  field: string,
  max = 2000,
): string | null {
  if (value === undefined || value === null || value === '') return null;
  return requireString(value, field, { max });
}

export function requireArray<T>(
  value: unknown,
  field: string,
  itemGuard: (item: unknown, index: number) => T,
  { max = 200 }: { max?: number } = {},
): T[] {
  if (!Array.isArray(value)) {
    throw new HttpsError('invalid-argument', `${field} must be an array.`);
  }
  if (value.length > max) {
    throw new HttpsError('invalid-argument', `${field} has too many entries.`);
  }
  return value.map(itemGuard);
}

// -----------------------------------------------------------------------------
// Authorisation
// -----------------------------------------------------------------------------

export interface Caller {
  uid: string;
  token: Record<string, unknown>;
}

export function requireAuth(request: CallableRequest): Caller {
  if (!request.auth?.uid) {
    throw new HttpsError('unauthenticated', 'Sign in required.');
  }
  return { uid: request.auth.uid, token: request.auth.token as Record<string, unknown> };
}

/**
 * The elder themselves.
 *
 * patientId is always the elder's own auth uid, so this is a straight identity
 * check — but it is written as a named guard so call sites read as intent
 * ("only the patient's own device may do this") rather than as an incidental
 * string comparison someone might later "simplify" away.
 */
export function requirePatientSelf(caller: Caller, patientId: string): void {
  if (caller.uid !== patientId) {
    throw new HttpsError('permission-denied', 'Not permitted.');
  }
}

/**
 * The elder, or a caretaker explicitly linked to them.
 *
 * This deliberately re-checks the link server-side rather than trusting a claim
 * on the token. The Admin SDK bypasses Firestore rules entirely, so a callable
 * that skipped this check would be a hole straight through the entire access
 * model no matter how careful firestore.rules is.
 */
export async function requireLinkedOrSelf(
  caller: Caller,
  patientId: string,
): Promise<void> {
  if (caller.uid === patientId) return;

  const snap = await getFirestore().doc(`patients/${patientId}`).get();
  if (!snap.exists) {
    // Same error as an unlinked caretaker, on purpose: distinguishing them would
    // let anyone probe which patient ids exist.
    throw new HttpsError('permission-denied', 'Not permitted.');
  }

  const caretakerIds = (snap.get('caretakerIds') as string[] | undefined) ?? [];
  if (!caretakerIds.includes(caller.uid)) {
    throw new HttpsError('permission-denied', 'Not permitted.');
  }
}

// -----------------------------------------------------------------------------
// Rate limiting
// -----------------------------------------------------------------------------

/**
 * Fixed-window counter kept in Firestore.
 *
 * Not a precise distributed limiter — under heavy concurrency a few extra calls
 * can slip through. That is an acceptable trade for something with no extra
 * infrastructure, because the threats here are a person hammering a button and
 * someone brute-forcing linking codes, not a coordinated flood.
 *
 * Fails OPEN on an internal error: a limiter outage must not stop a genuine
 * medication call from going out.
 */
export async function enforceRateLimit(
  key: string,
  { limit, windowSeconds }: { limit: number; windowSeconds: number },
): Promise<void> {
  const db = getFirestore();
  const windowId = Math.floor(Date.now() / (windowSeconds * 1000));
  const ref = db.doc(`_rateLimits/${key}_${windowId}`);

  try {
    const count = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const current = (snap.get('count') as number | undefined) ?? 0;
      const next = current + 1;
      tx.set(
        ref,
        { count: next, expiresAt: new Date((windowId + 2) * windowSeconds * 1000) },
        { merge: true },
      );
      return next;
    });

    if (count > limit) {
      throw new HttpsError('resource-exhausted', 'Too many requests. Try again shortly.');
    }
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    // Fail open — see the note above.
  }
}
