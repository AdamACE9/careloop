/**
 * Firestore security rules tests.
 *
 * These rules are the only thing standing between two families' health records,
 * and until now nothing checked them. That was the largest untested surface in
 * the project.
 *
 * They run against the Firestore emulator, which CANNOT start on the
 * development machine: its JVM fails to open an AF_UNIX socket pair, the same
 * fault that stops Gradle there (CLAUDE.md section 9). So this runs on CI,
 * under `firebase emulators:exec`.
 *
 * Every case is written from the attacker's side where one exists. "A caretaker
 * can read their patient" is worth one test; "a stranger cannot", "a caretaker
 * cannot write medications", and "nobody can grant themselves access" are worth
 * more, because those are the ones that lose people's data when they regress.
 *
 * .mts rather than .ts: node --experimental-strip-types removes the types but
 * does not rewrite module syntax, and functions/package.json has no
 * "type": "module", so a plain .ts file here would be read as CommonJS and
 * every import would throw.
 */

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

const ELDER = 'elder-uid';
const CARER = 'carer-uid';
const STRANGER = 'stranger-uid';

let env: RulesTestEnvironment;

function patientData(caretakerIds: string[] = []) {
  return {
    profile: {
      firstName: 'Margaret',
      lastName: 'Whitfield',
      preferredName: 'Margaret',
      age: 78,
      conditions: ['atrial_fibrillation'],
    },
    caretakerIds,
    dailyCheckInTime: '09:00',
    timezone: 'Europe/London',
    sharingPreferences: {
      enabledCategories: ['missed_doses'],
      alwaysShareUrgent: true,
      privacyHoldUntil: null,
    },
    createdAt: '2026-09-01T00:00:00.000Z',
    updatedAt: '2026-09-01T00:00:00.000Z',
  };
}

/** Writes a document with the rules switched off, to set up a scenario. */
async function seed(path: string, data: Record<string, unknown>) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), path), data);
  });
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'careloop-rules-test',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

// Every test starts from an empty database. Without this a document created by
// one test turns the next test's create into an update, and the assertion still
// passes while testing something else entirely.
beforeEach(async () => {
  await env.clearFirestore();
});

after(async () => {
  await env?.cleanup();
});

describe('patients', () => {
  it('an elder can create their own record with no caretakers', async () => {
    const db = env.authenticatedContext(ELDER).firestore();
    await assertSucceeds(setDoc(doc(db, `patients/${ELDER}`), patientData()));
  });

  it('an elder cannot create a record that already names a caretaker', async () => {
    // This is the whole access model in one test. If a client could seed
    // caretakerIds, anyone could grant themselves sight of a stranger's health
    // record by writing their own uid into it at creation time.
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(setDoc(doc(db, `patients/${ELDER}`), patientData([STRANGER])));
  });

  it('nobody can create a record under another account uid', async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(setDoc(doc(db, `patients/${ELDER}`), patientData()));
  });

  it('an elder cannot add a caretaker to themselves', async () => {
    await seed(`patients/${ELDER}`, patientData());
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(updateDoc(doc(db, `patients/${ELDER}`), { caretakerIds: [CARER] }));
  });

  it('an elder can change their own check-in time', async () => {
    await seed(`patients/${ELDER}`, patientData());
    const db = env.authenticatedContext(ELDER).firestore();
    await assertSucceeds(
      updateDoc(doc(db, `patients/${ELDER}`), {
        dailyCheckInTime: '10:30',
        updatedAt: '2026-09-02T00:00:00.000Z',
      }),
    );
  });

  it('a linked caretaker can read the patient, a stranger cannot', async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));

    const carer = env.authenticatedContext(CARER).firestore();
    await assertSucceeds(getDoc(doc(carer, `patients/${ELDER}`)));

    const stranger = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDoc(doc(stranger, `patients/${ELDER}`)));
  });

  it('the dashboard query works for a caretaker and returns nothing to a stranger', async () => {
    // The regression this exists for: get() worked and list() did not, because
    // a list rule cannot call get() on each candidate document. The dashboard
    // showed an empty page to every correctly linked caretaker. A rule that is
    // right for one verb and wrong for the other stays invisible until someone
    // runs the exact query the app runs, so this runs the exact query the app
    // runs.
    await seed(`patients/${ELDER}`, patientData([CARER]));

    const carer = env.authenticatedContext(CARER).firestore();
    const mine = query(
      collection(carer, 'patients'),
      where('caretakerIds', 'array-contains', CARER),
    );
    const snap = await assertSucceeds(getDocs(mine));
    assert.equal(snap.size, 1);
    assert.equal(snap.docs[0]!.id, ELDER);

    const stranger = env.authenticatedContext(STRANGER).firestore();
    const theirs = query(
      collection(stranger, 'patients'),
      where('caretakerIds', 'array-contains', STRANGER),
    );
    const empty = await assertSucceeds(getDocs(theirs));
    assert.equal(empty.size, 0);
  });

  it('an unfiltered listing of every patient is refused', async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
    const carer = env.authenticatedContext(CARER).firestore();
    await assertFails(getDocs(collection(carer, 'patients')));
  });

  it('an unauthenticated visitor can read nothing', async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, `patients/${ELDER}`)));
  });
});

describe('medications', () => {
  beforeEach(async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
  });

  it('the elder may add and remove their own medications', async () => {
    const db = env.authenticatedContext(ELDER).firestore();
    const ref = doc(db, `patients/${ELDER}/medications/warfarin`);
    await assertSucceeds(setDoc(ref, { name: 'Warfarin', dose: '3mg' }));
    await assertSucceeds(deleteDoc(ref));
  });

  it('a caretaker may read them but never write them', async () => {
    await seed(`patients/${ELDER}/medications/warfarin`, { name: 'Warfarin', dose: '3mg' });

    const carer = env.authenticatedContext(CARER).firestore();
    await assertSucceeds(getDoc(doc(carer, `patients/${ELDER}/medications/warfarin`)));
    await assertFails(
      setDoc(doc(carer, `patients/${ELDER}/medications/injected`), { name: 'X' }),
    );
    await assertFails(deleteDoc(doc(carer, `patients/${ELDER}/medications/warfarin`)));
  });

  it('a stranger sees nothing', async () => {
    await seed(`patients/${ELDER}/medications/warfarin`, { name: 'Warfarin', dose: '3mg' });
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDoc(doc(db, `patients/${ELDER}/medications/warfarin`)));
  });
});

describe('vitals', () => {
  const reading = {
    type: 'blood_sugar',
    value: 7.6,
    secondaryValue: null,
    recordedAt: '2026-09-02T09:00:00.000Z',
    source: 'manual',
  };

  beforeEach(async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
  });

  it('the elder may record a manual reading', async () => {
    const db = env.authenticatedContext(ELDER).firestore();
    await assertSucceeds(setDoc(doc(db, `patients/${ELDER}/vitals/v1`), reading));
  });

  it('a reading with an extra field is rejected', async () => {
    // The rule pins the exact key set. Worth a test because the app builds this
    // document by hand, so an added field fails at write time on a real user's
    // phone rather than at compile time here.
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(
      setDoc(doc(db, `patients/${ELDER}/vitals/v2`), { ...reading, note: 'felt fine' }),
    );
  });

  it('a reading cannot claim to have come from a call', async () => {
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(
      setDoc(doc(db, `patients/${ELDER}/vitals/v3`), { ...reading, source: 'call' }),
    );
  });

  it('a non-numeric value is rejected', async () => {
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(
      setDoc(doc(db, `patients/${ELDER}/vitals/v4`), { ...reading, value: '7.6' }),
    );
  });

  it('readings cannot be edited or deleted once written', async () => {
    await seed(`patients/${ELDER}/vitals/v5`, reading);
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(updateDoc(doc(db, `patients/${ELDER}/vitals/v5`), { value: 4.0 }));
    await assertFails(deleteDoc(doc(db, `patients/${ELDER}/vitals/v5`)));
  });

  it('a caretaker may read them and may not write one', async () => {
    await seed(`patients/${ELDER}/vitals/v6`, reading);
    const carer = env.authenticatedContext(CARER).firestore();
    await assertSucceeds(getDoc(doc(carer, `patients/${ELDER}/vitals/v6`)));
    await assertFails(setDoc(doc(carer, `patients/${ELDER}/vitals/v7`), reading));
  });
});

describe('agent output', () => {
  beforeEach(async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
    await seed(`patients/${ELDER}/checkIns/c1`, { caraSummary: 'fine' });
    await seed(`patients/${ELDER}/agentThreads/t1`, { topic: 'knee', status: 'open' });
  });

  it('nobody can write a check-in from a client', async () => {
    // Check-ins are the evidence the escalation reasoning is built on. A client
    // that could edit them could rewrite the record of what was actually said.
    const elder = env.authenticatedContext(ELDER).firestore();
    await assertFails(setDoc(doc(elder, `patients/${ELDER}/checkIns/c2`), { caraSummary: 'x' }));
    await assertFails(updateDoc(doc(elder, `patients/${ELDER}/checkIns/c1`), { caraSummary: 'x' }));

    const carer = env.authenticatedContext(CARER).firestore();
    await assertFails(updateDoc(doc(carer, `patients/${ELDER}/checkIns/c1`), { caraSummary: 'x' }));
  });

  it('both sides can read agent threads, and neither can write them', async () => {
    // The elder seeing exactly what the family sees is the product's stated
    // position, not an implementation detail, so it gets a test.
    const elder = env.authenticatedContext(ELDER).firestore();
    const carer = env.authenticatedContext(CARER).firestore();
    const path = `patients/${ELDER}/agentThreads/t1`;

    await assertSucceeds(getDoc(doc(elder, path)));
    await assertSucceeds(getDoc(doc(carer, path)));

    await assertFails(updateDoc(doc(elder, path), { status: 'resolved' }));
    await assertFails(updateDoc(doc(carer, path), { status: 'resolved' }));
  });
});

describe('escalations', () => {
  beforeEach(async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
    await seed(`patients/${ELDER}/escalations/e1`, {
      severity: 'medium',
      reasoning: 'two missed doses of warfarin',
      acknowledged: false,
      acknowledgedAt: null,
      elderResponse: null,
    });
  });

  it('a caretaker may acknowledge one and nothing more', async () => {
    const carer = env.authenticatedContext(CARER).firestore();
    const ref = doc(carer, `patients/${ELDER}/escalations/e1`);
    await assertSucceeds(
      updateDoc(ref, { acknowledged: true, acknowledgedAt: '2026-09-02T10:00:00.000Z' }),
    );
    // Rewriting the reasoning would let a family member edit the audit trail of
    // an autonomous decision after the fact.
    await assertFails(updateDoc(ref, { reasoning: 'nothing happened' }));
  });

  it('the elder may answer back but may not acknowledge for the family', async () => {
    const elder = env.authenticatedContext(ELDER).firestore();
    const ref = doc(elder, `patients/${ELDER}/escalations/e1`);
    await assertSucceeds(
      updateDoc(ref, {
        elderResponse: 'disputed',
        elderNote: 'I did take it',
        elderRespondedAt: '2026-09-02T10:05:00.000Z',
      }),
    );
    await assertFails(updateDoc(ref, { acknowledged: true }));
  });

  it('no client can create or delete one', async () => {
    const carer = env.authenticatedContext(CARER).firestore();
    const elder = env.authenticatedContext(ELDER).firestore();
    await assertFails(setDoc(doc(elder, `patients/${ELDER}/escalations/e2`), { severity: 'low' }));
    await assertFails(deleteDoc(doc(carer, `patients/${ELDER}/escalations/e1`)));
  });
});

describe('device tokens', () => {
  it('a device registers its own token and nobody can read it back', async () => {
    // A token identifies a physical device. Keeping it unreadable is why it
    // lives outside the patient document in the first place.
    const db = env.authenticatedContext(ELDER).firestore();
    await assertSucceeds(
      setDoc(doc(db, `deviceTokens/${ELDER}`), {
        token: 'fcm-token-value',
        platform: 'android',
        updatedAt: '2026-09-02T00:00:00.000Z',
      }),
    );
    await assertFails(getDoc(doc(db, `deviceTokens/${ELDER}`)));
  });

  it('nobody can register a token under another uid', async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(
      setDoc(doc(db, `deviceTokens/${ELDER}`), {
        token: 'stolen',
        platform: 'android',
        updatedAt: '2026-09-02T00:00:00.000Z',
      }),
    );
  });
});

describe('linking codes', () => {
  it('are invisible to every client', async () => {
    // A readable code collection would let anyone enumerate live codes and
    // attach themselves to a stranger's record, which is a compromise of the
    // whole access model rather than the leak of one document.
    await seed('linkingCodes/ABCD1234', { patientUid: ELDER, used: false });

    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDoc(doc(db, 'linkingCodes/ABCD1234')));
    await assertFails(getDocs(collection(db, 'linkingCodes')));
    await assertFails(setDoc(doc(db, 'linkingCodes/EVIL0000'), { patientUid: ELDER }));
  });
});

describe('caretaker profiles', () => {
  const profile = {
    displayName: 'Sarah',
    email: 'sarah@example.com',
    phone: '07700900000',
    relationship: 'daughter',
    updatedAt: '2026-09-02T00:00:00.000Z',
  };

  beforeEach(async () => {
    await seed(`patients/${ELDER}`, patientData([CARER]));
    await seed(`users/${CARER}`, profile);
  });

  it('an elder can read the profile of their own caretaker', async () => {
    const db = env.authenticatedContext(ELDER).firestore();
    await assertSucceeds(getDoc(doc(db, `users/${CARER}`)));
  });

  it('an unrelated account cannot, so this is not a phone directory', async () => {
    const db = env.authenticatedContext(STRANGER).firestore();
    await assertFails(getDoc(doc(db, `users/${CARER}`)));
  });

  it('you can write your own profile, with only the allowed fields', async () => {
    const carer = env.authenticatedContext(CARER).firestore();
    await assertSucceeds(
      setDoc(doc(carer, `users/${CARER}`), { ...profile, displayName: 'Sarah W' }),
    );
    await assertFails(setDoc(doc(carer, `users/${CARER}`), { ...profile, isAdmin: true }));
    await assertFails(setDoc(doc(carer, `users/${STRANGER}`), { displayName: 'Not mine' }));
  });

  it('a profile cannot be deleted, because links would outlive it', async () => {
    const carer = env.authenticatedContext(CARER).firestore();
    await assertFails(deleteDoc(doc(carer, `users/${CARER}`)));
  });
});

describe('the default posture', () => {
  it('is deny, for a path nobody thought about', async () => {
    // The catch-all exists so that adding a collection and forgetting its rules
    // fails closed. If this test ever passes a write, someone has introduced a
    // broad match above it.
    const db = env.authenticatedContext(ELDER).firestore();
    await assertFails(setDoc(doc(db, 'somethingNew/doc1'), { hello: 'world' }));
    await assertFails(getDoc(doc(db, 'somethingNew/doc1')));
  });
});
