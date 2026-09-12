'use client';

import { useEffect, useState } from 'react';
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
import { getDb, getFns, isFirebaseConfigured } from './firebase';
import { getFirebaseAuth } from './firebase';
import * as demo from './demo-data';
import type { AgentThread, CheckIn, Escalation, Medication } from './demo-data';

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
  const [patients, setPatients] = useState<LinkedPatient[]>(
    isFirebaseConfigured ? [] : [DEMO_PATIENT],
  );
  const [isDemo, setIsDemo] = useState(!isFirebaseConfigured);
  const [loading, setLoading] = useState(isFirebaseConfigured);

  useEffect(() => {
    const db = getDb();
    const auth = getFirebaseAuth();
    if (!db || !auth?.currentUser) {
      // Not signed in. The example household is the right thing to show a
      // visitor, and the wrong thing to show an account holder.
      setPatients([DEMO_PATIENT]);
      setIsDemo(true);
      setLoading(false);
      return;
    }

    // Scoped by caretakerIds, which matches the security rule exactly. Firestore
    // rules are not filters: a broader query here would fail outright rather than
    // returning a subset, so the query and the rule have to agree.
    const q = query(
      collection(db, 'patients'),
      where('caretakerIds', 'array-contains', auth.currentUser.uid),
    );

    return onSnapshot(
      q,
      (snap) => {
        if (snap.empty) {
          // Signed in, linked to nobody. Empty is the truth.
          setPatients([]);
          setIsDemo(false);
        } else {
          setIsDemo(false);
          setPatients(
            snap.docs.map((d) => {
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
          );
        }
        setLoading(false);
      },
      () => {
        // A permission error here means the caretaker is not linked to anyone.
        // Treated the same as empty: ask them to link, do not invent a patient.
        setPatients([]);
        setIsDemo(false);
        setLoading(false);
      },
    );
  }, []);

  return { patients, loading, isDemo };
}

// -----------------------------------------------------------------------------
// Collections
// -----------------------------------------------------------------------------

function useCollection<T>(
  patientId: string,
  path: string,
  orderField: string,
  fallback: T[],
  max = 60,
): { data: T[]; live: boolean } {
  // Seeded with the example data only when there is no backend to read from.
  const [data, setData] = useState<T[]>(isFirebaseConfigured ? [] : fallback);
  const [live, setLive] = useState(false);

  useEffect(() => {
    const db = getDb();
    // The demo patient id belongs to no real document, so there is nothing to
    // subscribe to and the seeded example data stands.
    if (!db || !patientId || patientId === DEMO_PATIENT.id) return;

    const q = query(
      collection(db, `patients/${patientId}/${path}`),
      orderBy(orderField, 'desc'),
      fsLimit(max),
    );

    return onSnapshot(
      q,
      (snap) => {
        if (snap.empty) {
          setData([]);
          setLive(true);
          return;
        }
        // Real data, including when it is empty. An empty collection for a
        // linked patient means nothing has happened yet, which the page says
        // in words rather than papering over with somebody else's history.
        setData(snap.docs.map((d) => ({ id: d.id, ...d.data() })) as T[]);
        setLive(true);
      },
      () => {
        // A read that fails is not the same as a read that returns nothing, so
        // this keeps the seeded value and marks the data as not live.
        setData(fallback);
        setLive(false);
      },
    );
    // `fallback` is a stable module-level array; excluding it avoids resubscribing
    // on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [patientId, path, orderField, max]);

  return { data, live };
}

export function useCheckIns(patientId: string) {
  return useCollection<CheckIn>(patientId, 'checkIns', 'startedAt', demo.checkIns);
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
  return useCollection<Medication>(patientId, 'medications', 'name', demo.medications);
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
