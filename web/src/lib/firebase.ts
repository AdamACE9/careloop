'use client';

import { initializeApp, getApps, type FirebaseApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, type Auth } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, type Firestore } from 'firebase/firestore';
import { getFunctions, connectFunctionsEmulator, type Functions } from 'firebase/functions';

/**
 * Firebase client initialisation.
 *
 * ## About these values being public
 *
 * Everything in `firebaseConfig` is safe in a client bundle, and `NEXT_PUBLIC_`
 * is correct here. A Firebase web config is an *address*, not a credential: it
 * identifies which project to talk to. Access is decided entirely by
 * `firestore.rules` and Firebase Auth.
 *
 * That is emphatically NOT true of the Gemini API key, which must never appear
 * in this file, in any `NEXT_PUBLIC_` variable, or anywhere else a browser can
 * read. It lives in Secret Manager and is used only inside Cloud Functions. If
 * you ever find yourself adding it here, the answer is a callable function.
 *
 * ## Degrading without configuration
 *
 * Until the project exists, `isFirebaseConfigured` is false and the app runs on
 * demo data rather than crashing on boot. That keeps the site presentable and
 * deployable before any credentials are issued, which matters because the whole
 * dashboard is a live demo surface.
 */

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

/**
 * Whether real credentials are present.
 *
 * Every data call checks this and falls back to the bundled demo dataset when it
 * is false, so the dashboard is fully explorable with no backend at all.
 */
export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey && firebaseConfig.projectId,
);

let app: FirebaseApp | null = null;
let authInstance: Auth | null = null;
let dbInstance: Firestore | null = null;
let functionsInstance: Functions | null = null;

function ensureApp(): FirebaseApp | null {
  if (!isFirebaseConfigured) return null;
  if (app) return app;

  app = getApps().length
    ? getApps()[0]!
    : initializeApp(firebaseConfig as Record<string, string>);
  return app;
}

export function getFirebaseAuth(): Auth | null {
  const instance = ensureApp();
  if (!instance) return null;
  if (!authInstance) {
    authInstance = getAuth(instance);
    maybeConnectEmulators();
  }
  return authInstance;
}

export function getDb(): Firestore | null {
  const instance = ensureApp();
  if (!instance) return null;
  if (!dbInstance) {
    dbInstance = getFirestore(instance);
    maybeConnectEmulators();
  }
  return dbInstance;
}

export function getFns(): Functions | null {
  const instance = ensureApp();
  if (!instance) return null;
  if (!functionsInstance) {
    functionsInstance = getFunctions(instance, 'us-central1');
    maybeConnectEmulators();
  }
  return functionsInstance;
}

/**
 * Points the SDK at local emulators during development.
 *
 * Guarded so it runs exactly once. Calling `connect*Emulator` twice throws, and
 * React strict mode in development will happily invoke this twice if you let it.
 */
let emulatorsConnected = false;
function maybeConnectEmulators(): void {
  if (emulatorsConnected) return;
  if (process.env.NEXT_PUBLIC_USE_FIREBASE_EMULATORS !== 'true') return;
  emulatorsConnected = true;

  try {
    if (authInstance) {
      connectAuthEmulator(authInstance, 'http://127.0.0.1:9099', { disableWarnings: true });
    }
    if (dbInstance) connectFirestoreEmulator(dbInstance, '127.0.0.1', 8080);
    if (functionsInstance) connectFunctionsEmulator(functionsInstance, '127.0.0.1', 5001);
  } catch {
    // Already connected, or emulators are not running. Neither is fatal.
  }
}
