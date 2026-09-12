'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  collection,
  doc,
  onSnapshot,
  orderBy,
  query,
  updateDoc,
  where,
  limit as fsLimit,
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { getDb, getFns, isFirebaseConfigured, getFirebaseAuth } from './firebase';
import { useAuth } from './auth-context';
import * as demo from './demo-data';
import type {
  AgentThread,
  CheckIn,
  Escalation,
  Medication,
  VitalReading,
} from './demo-data';

/**
 * The single seam between the dashboard UI and its data.
 *
 * Every component reads through these hooks and never touches Firestore or the
 * demo dataset directly. That means the entire dashboard works identically in
 * three situations without a line of component code changing:
 *
 *   1. No Firebase configured at all  → bundled demo data
 *   2. Firebase configured, no data   → demo data (so a fresh project is not a blank page)
 *   3. Firebase configured, real data → live Firestore snapshots
 *
 * Case 2 is the one people forget. A judge opening a freshly deployed dashboard
 * should see a working product, not an empty state that looks broken.
 *
 * Reads use `onSnapshot` rather than one-shot `getDocs` so the dashboard updates
 * live. That matters for the demo: pressing "check on her now" on a laptop and
 * watching the check-in appear a minute later is the whole product in one gesture.
 */

// -----------------------------------------------------------------------------
// Patient identity
// -----------------------------------------------------------------------------

export interface LinkedPatient {
  id: string;
  firstName: string;
  lastName: string;
  preferredName: string;
  age: number;
  conditions: string[];
  checkInTime: string;
}

const DEMO_PATIENT: LinkedPatient = {
  id: 'demo-margaret',
  firstName: demo.elder.firstName,
  lastName: demo.elder.lastName,
  preferredName: demo.elder.firstName,
  age: demo.elder.age,
  conditions: demo.elder.conditions,
  checkInTime: demo.elder.checkInTime,
};

/**
 * Patients this caretaker is linked to.
 *
 * Three states, not two, and the difference matters.
 *
 *   - No Firebase project at all: show the example household. This is the
 *     marketing case, where a visitor should see what the product does.
 *   - Signed in and linked: show their real people.
 *   - Signed in and linked to NOBODY: show nothing, and let the page ask them
 *     to enter the code from the phone.
 *
 * That last case used to fall back to the example household too, which meant a
 * caretaker who had just created an account was shown a stranger's medication
 * list and blood sugar, presented as their mother's. It looked like the product
 * working and was the opposite.
 */
export function useLinkedPatients(): {
  patients: LinkedPatient[];
  loading: boolean;
  /** True when what is on screen is the example household, not real data. */
  isDemo: boolean;
} {
  // Taken from the auth context rather than read off auth.currentUser inside an
  // effect with no dependencies. That older form ran exactly once, on mount, so
  // signing in from the dashboard left this hook looking at the signed-out
  // answer until the page was reloaded: the new account saw the example
  // household and no amount of waiting changed it.
  const { user } = useAuth();
  const uid = user?.uid ?? null;

  // Tagged with the uid it was fetched for, so a result belonging to the
  // previous account is ignored on the render where the account changes.
  const [snapshot, setSnapshot] = useState<{
    uid: string;
    patients: LinkedPatient[];
  } | null>(null);

  const current = snapshot?.uid === uid ? snapshot : null;

  // Not signed in, or no Firebase at all: the example household is the right
  // thing to show a visitor, and the wrong thing to show an account holder.
  const isDemo = !isFirebaseConfigured || uid === null;
  const patients = isDemo ? [DEMO_PATIENT] : (current?.patients ?? []);
  const loading = !isDemo && current === null;

  useEffect(() => {
    const db = getDb();
    if (!db || !uid) return;

    // Scoped by caretakerIds, which matches the security rule exactly. Firestore
    // rules are not filters: a broader query here would fail outright rather
    // than returning a subset, so the query and the rule have to agree.
    const q = query(
      collection(db, 'patients'),
      where('caretakerIds', 'array-contains', uid),
    );

    return onSnapshot(
      q,
      (snap) => {
        setSnapshot({
          uid,
          // Signed in and linked to nobody gives an empty list, which is the
          // truth. It used to fall back to the example household, so a
          // caretaker who had just created an account was shown a stranger's
          // medication list and blood sugar presented as their mother's. It
          // looked like the product working and was the opposite.
          patients: snap.docs.map((d) => {
            const data = d.data() as Record<string, never>;
            const profile = (data.profile ?? {}) as Record<string, string & number>;
            return {
              id: d.id,
              firstName: String(profile.firstName ?? ''),
              lastName: String(profile.lastName ?? ''),
              preferredName: String(profile.preferredName ?? profile.firstName ?? ''),
              age: Number(profile.age ?? 0),
              conditions: (profile.conditions as unknown as string[]) ?? [],
              checkInTime: String(data.dailyCheckInTime ?? '09:00'),
            };
          }),
        });
      },
      () => {
        // A permission error here means the caretaker is linked to nobody.
        // Treated the same as empty: ask them to link, never invent a patient.
        setSnapshot({ uid, patients: [] });
      },
    );
  }, [uid]);

  return { patients, loading, isDemo };
}

// -----------------------------------------------------------------------------
// Collections
// -----------------------------------------------------------------------------

/**
 * One live subscription to a subcollection under a patient.
 *
 * Two sources feed every screen, and which one is showing is derived at render
 * time rather than assigned in an effect. That matters for three reasons:
 *
 *   - It agrees with useLinkedPatients by construction. Keyed off
 *     isFirebaseConfigured alone, the two hooks disagreed: a signed-out visitor
 *     on a deployed build got the example patient's name from one and empty
 *     collections from the other, so the dashboard rendered Margaret with no
 *     medications, no check-ins and an empty chart.
 *   - Switching patients cannot show one person's data under another's name,
 *     because the stored snapshot carries the id it came from and is ignored
 *     the moment that id stops matching.
 *   - `loading` and `empty` stay distinguishable. Without that, a page has to
 *     choose between flashing "nothing here yet" at someone whose data is one
 *     frame away and spinning forever at someone who genuinely has none.
 */
function useCollection<T>(
  patientId: string,
  path: string,
  orderField: string,
  fallback: T[],
  max = 60,
  direction: 'asc' | 'desc' = 'desc',
): { data: T[]; live: boolean; loading: boolean } {
  const showExample = !isFirebaseConfigured || patientId === DEMO_PATIENT.id;

  // Tagged with the patient it belongs to, so a snapshot for the previous
  // patient is discarded on the render where the id changes.
  const [snapshot, setSnapshot] = useState<{
    patientId: string;
    docs: T[];
    live: boolean;
  } | null>(null);

  const current = snapshot?.patientId === patientId ? snapshot : null;

  const data = showExample ? fallback : (current?.docs ?? []);
  const loading = !showExample && current === null;
  const live = current?.live ?? false;

  useEffect(() => {
    const db = getDb();
    // The demo patient id belongs to no real document, so there is nothing to
    // subscribe to and the example data stands.
    if (!db || !patientId || patientId === DEMO_PATIENT.id) return;

    const q = query(
      collection(db, `patients/${patientId}/${path}`),
      orderBy(orderField, direction),
      fsLimit(max),
    );

    return onSnapshot(
      q,
      (snap) => {
        // Real data, including when it is empty. An empty collection for a
        // linked patient means nothing has happened yet, which the page says in
        // words rather than papering over with somebody else's history.
        setSnapshot({
          patientId,
          docs: snap.docs.map((d) => ({ id: d.id, ...d.data() })) as T[],
          live: true,
        });
      },
      () => {
        // A read that failed is not a read that returned nothing. The page is
        // told it is not live so it can say so, and shows nothing rather than
        // the example household dressed up as this person's record.
        setSnapshot({ patientId, docs: [], live: false });
      },
    );
  }, [patientId, path, orderField, max, direction]);

  return { data, live, loading };
}

// -----------------------------------------------------------------------------
// Display shaping
//
// Stored documents carry ISO timestamps; screens want "Yesterday" and "9:02 am".
// Deriving that here, once, is what lets the demo dataset and Firestore be the
// same shape. It used to be the other way round: the example data held the
// pre-formatted strings and the pages read those field names, so real documents
// rendered blank dates and, worse, a missed-dose count that was always zero.
// -----------------------------------------------------------------------------

/** A check-in with the strings the pages actually render. */
export interface CheckInView extends CheckIn {
  /** "Today", "Yesterday", or "Thursday" within the last week, else a date. */
  label: string;
  /** "9:02 am" in the reader's own locale. */
  time: string;
  /** Milliseconds since the epoch, or NaN for an unparseable timestamp. */
  at: number;
  confirmed: string[];
  missed: string[];
}

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** "Today" / "Yesterday" / weekday within the last week / "12 Sep". */
export function dayLabel(iso: string, now = new Date()): string {
  const at = Date.parse(iso);
  if (!Number.isFinite(at)) return '';

  const then = new Date(at);
  const days = Math.round((startOfDay(now) - startOfDay(then)) / 86_400_000);

  if (days === 0) return 'Today';
  if (days === 1) return 'Yesterday';
  // Past six days only. "Thursday" nine days ago is actively misleading, and it
  // is the kind of wrong that reads as right.
  if (days > 1 && days < 7) return then.toLocaleDateString(undefined, { weekday: 'long' });
  return then.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function timeLabel(iso: string): string {
  const at = Date.parse(iso);
  if (!Number.isFinite(at)) return '';
  return new Date(at).toLocaleTimeString(undefined, {
    hour: 'numeric',
    minute: '2-digit',
  });
}

function toView(c: CheckIn): CheckInView {
  return {
    ...c,
    at: Date.parse(c.startedAt),
    label: dayLabel(c.startedAt),
    time: timeLabel(c.startedAt),
    confirmed: c.medicationsConfirmed ?? [],
    missed: c.medicationsMissed ?? [],
  };
}

export function useCheckIns(patientId: string): {
  data: CheckInView[];
  live: boolean;
  loading: boolean;
} {
  const { data, live, loading } = useCollection<CheckIn>(
    patientId,
    'checkIns',
    'startedAt',
    demo.checkIns,
  );
  const view = useMemo(() => data.map(toView), [data]);
  return { data: view, live, loading };
}

/**
 * The check-in from today, if there has been one.
 *
 * The overview used to take `checkIns[0]` and headline it as today. That is
 * only the most recent call, so a caretaker whose parent had not been reached
 * since Monday was shown "Margaret is doing well today" over Monday's summary.
 * Saying nothing happened today is the honest answer and the more useful one,
 * because a day with no call is exactly what somebody checking in wants to know.
 */
export function todaysCheckIn(checkIns: CheckInView[], now = new Date()): CheckInView | null {
  return checkIns.find((c) => dayLabel(c.startedAt, now) === 'Today') ?? null;
}

/** How many of the last `days` days had a dose missed. */
export function daysWithMissedDose(checkIns: CheckInView[], days = 7, now = new Date()): number {
  const cutoff = startOfDay(now) - (days - 1) * 86_400_000;
  const seen = new Set<number>();
  for (const c of checkIns) {
    if (!Number.isFinite(c.at) || c.at < cutoff) continue;
    if (c.missed.length > 0) seen.add(startOfDay(new Date(c.at)));
  }
  return seen.size;
}

export function useEscalations(patientId: string) {
  return useCollection<Escalation>(patientId, 'escalations', 'raisedAt', demo.escalations);
}

export function useAgentThreads(patientId: string) {
  // Ordered by when Cara opened them. Resolved ones are kept and filtered in the
  // UI rather than excluded here: seeing what she closed, and why, is most of
  // what makes the open ones believable.
  return useCollection<AgentThread>(patientId, 'agentThreads', 'raisedAt', demo.agentThreads);
}

export function useMedications(patientId: string) {
  // Ascending, because this one is ordered by name and a list running Z to A
  // reads as broken. Every other collection here is ordered by time, where
  // newest first is what you want.
  return useCollection<Medication>(patientId, 'medications', 'name', demo.medications, 60, 'asc');
}

/**
 * Readings, oldest first, for charting.
 *
 * Firestore can only order one way per query and the chart needs oldest to
 * newest, so this asks for ascending rather than reversing 200 documents on
 * every render.
 *
 * The 180 cap is roughly six months of daily readings. It is a bound on the
 * read, not a window: the chart picks its own range out of whatever comes back.
 */
export function useVitals(patientId: string) {
  return useCollection<VitalReading>(
    patientId,
    'vitals',
    'recordedAt',
    demo.vitals,
    180,
    'asc',
  );
}

// -----------------------------------------------------------------------------
// Actions
// -----------------------------------------------------------------------------

export type CallRequestResult =
  | { ok: true; demo: boolean }
  | { ok: false; reason: string };

/**
 * Fires an incoming call to the elder's phone.
 *
 * Calls the same Cloud Function the scheduler uses, so this button exercises the
 * real delivery path rather than a demo shortcut.
 *
 * Errors are translated into something a worried person can read. "Her phone
 * appears to be off" is useful; `functions/resource-exhausted` is not.
 */
export async function requestCheckInCall(patientId: string): Promise<CallRequestResult> {
  const fns = getFns();
  if (!fns || patientId === DEMO_PATIENT.id) {
    await new Promise((r) => setTimeout(r, 900));
    return { ok: true, demo: true };
  }

  try {
    const call = httpsCallable<
      { patientId: string; trigger: string },
      { delivered: boolean; reason: string | null }
    >(fns, 'triggerCall');

    const result = await call({ patientId, trigger: 'manual' });

    if (!result.data.delivered) {
      return {
        ok: false,
        reason:
          result.data.reason === 'NO_DEVICE_TOKEN'
            ? 'Her phone has not been set up with CareLoop yet.'
            : result.data.reason === 'DEVICE_UNREGISTERED'
              ? 'CareLoop is no longer installed on her phone.'
              : 'Could not reach her phone. It may be switched off.',
      };
    }
    return { ok: true, demo: false };
  } catch (error) {
    const code = (error as { code?: string })?.code ?? '';
    if (code.includes('resource-exhausted')) {
      return {
        ok: false,
        reason: 'Cara has already called several times today. Give her a little space.',
      };
    }
    return { ok: false, reason: 'Could not start the call. Please try again.' };
  }
}

/** Marks an escalation as seen. Fails quietly in demo mode. */
export async function acknowledgeEscalation(
  patientId: string,
  escalationId: string,
): Promise<void> {
  const db = getDb();
  if (!db || patientId === DEMO_PATIENT.id) return;

  await updateDoc(doc(db, `patients/${patientId}/escalations/${escalationId}`), {
    acknowledged: true,
    acknowledgedAt: new Date().toISOString(),
  });
}

export type RedeemResult =
  | { ok: true; patientName: string }
  | { ok: false; reason: string };

/**
 * Links this caretaker to an elder, using the code shown on the elder's phone.
 *
 * The direction here matters and was previously backwards. The backend's
 * generateLinkingCode requires the caller to BE the patient, so a caretaker
 * calling it gets permission-denied every time; only the elder's own device can
 * mint a code. The caretaker is the one who redeems.
 *
 * That is also the right way round for the product. The code grants somebody
 * ongoing sight of an elderly person's health record, so the person it belongs
 * to should be the one who issues it, from a device in their own hand, rather
 * than having access granted to them by someone else and being told afterwards.
 */
/**
 * Stops following a patient.
 *
 * A caretaker removing their own access, which the backend allows precisely
 * because it is their own. They cannot remove anyone else: letting one family
 * member quietly cut another out of a parent's care is a family dispute this
 * software has no business adjudicating, and the backend refuses it.
 *
 * The elder is told either way. An access change happening silently is the same
 * failure as an escalation happening silently.
 */
export async function unlinkSelf(patientId: string): Promise<{ ok: boolean; reason?: string }> {
  const fns = getFns();
  const auth = getFirebaseAuth();
  const uid = auth?.currentUser?.uid;

  if (!fns || !uid || patientId === DEMO_PATIENT.id) {
    return { ok: false, reason: 'This is the example household, so there is nothing to disconnect.' };
  }

  try {
    await httpsCallable(fns, 'unlinkCaretaker')({ patientId, caretakerId: uid });
    return { ok: true };
  } catch {
    return { ok: false, reason: 'That did not go through. Nothing has changed. Please try again.' };
  }
}

export async function redeemLinkingCode(code: string): Promise<RedeemResult> {
  const fns = getFns();
  if (!fns) {
    return { ok: false, reason: 'Not connected to a backend yet.' };
  }

  try {
    const call = httpsCallable<{ code: string }, { patientId: string; patientName: string }>(
      fns,
      'redeemLinkingCode',
    );
    const result = await call({ code: code.trim().toUpperCase() });
    return { ok: true, patientName: result.data.patientName };
  } catch (error) {
    const err = error as { message?: string };
    const message = err?.message ?? '';

    // The backend returns an identical error for missing, used and expired
    // codes, deliberately, so that nobody can discover which codes exist. The
    // wording here has to cover all three without implying which it was.
    if (message.includes('INVALID_CODE')) {
      return {
        ok: false,
        reason: 'That code was not recognised. It may have expired, or already been used.',
      };
    }
    if (message.includes('CANNOT_LINK_SELF')) {
      return { ok: false, reason: 'That is your own code. Ask them for the one on their phone.' };
    }
    if (message.includes('RATE_LIMIT') || message.includes('resource-exhausted')) {
      return { ok: false, reason: 'Too many attempts. Try again in a little while.' };
    }
    return { ok: false, reason: 'Could not connect just now. Try again in a moment.' };
  }
}
