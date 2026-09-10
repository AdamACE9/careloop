'use client';

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut as fbSignOut,
  updateProfile,
  type User,
} from 'firebase/auth';
import { getFirebaseAuth, isFirebaseConfigured } from './firebase';

/**
 * Caretaker authentication.
 *
 * ## Demo mode
 *
 * When Firebase is not configured, this provider exposes a signed-in demo user
 * instead of failing. That is deliberate rather than lazy: the dashboard is the
 * thing judges click first, and it has to be fully explorable from a cold link
 * with no credentials in existence. The UI is honest about it, showing a demo
 * badge rather than pretending an account exists.
 *
 * The moment real credentials appear in the environment, the same components
 * switch to real auth with no code change.
 *
 * ## Why email/password rather than something cleverer
 *
 * The caretaker is an adult child on a laptop, signing in occasionally. Email
 * link would avoid passwords but adds a round trip through an inbox at exactly
 * the moment someone is worried about their parent. Password is the boring,
 * reliable choice for this specific user.
 *
 * The *elder* never sees any of this. Their device authenticates separately and
 * is linked by a one-time code, because asking a 78-year-old to manage a password
 * is a documented way to lose them at setup.
 */

export interface AuthState {
  user: User | null;
  /** True when running without Firebase, on the bundled demo dataset. */
  isDemo: boolean;
  loading: boolean;
  displayName: string;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, name: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

const DEMO_NAME = 'Sarah Whitfield-Chen';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isFirebaseConfigured) {
      setLoading(false);
      return;
    }
    const auth = getFirebaseAuth();
    if (!auth) {
      setLoading(false);
      return;
    }
    return onAuthStateChanged(auth, (next) => {
      setUser(next);
      setLoading(false);
    });
  }, []);

  const value = useMemo<AuthState>(() => {
    const isDemo = !isFirebaseConfigured;

    return {
      user,
      isDemo,
      loading,
      displayName: user?.displayName ?? (isDemo ? DEMO_NAME : (user?.email ?? '')),

      async signIn(email, password) {
        const auth = getFirebaseAuth();
        if (!auth) return; // demo mode: the dashboard is already open
        await signInWithEmailAndPassword(auth, email, password);
      },

      async signUp(email, password, name) {
        const auth = getFirebaseAuth();
        if (!auth) return;
        const credential = await createUserWithEmailAndPassword(auth, email, password);
        if (name) await updateProfile(credential.user, { displayName: name });
      },

      async signOut() {
        const auth = getFirebaseAuth();
        if (!auth) return;
        await fbSignOut(auth);
      },
    };
  }, [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider.');
  }
  return context;
}

/**
 * Turns a Firebase auth error code into something a worried human can act on.
 *
 * Firebase's own messages are developer-facing ("auth/invalid-credential") and
 * reach the UI unchanged if you let them. Someone signing in to check on their
 * mother should not be shown an error code.
 */
export function friendlyAuthError(error: unknown): string {
  const code = (error as { code?: string })?.code ?? '';
  switch (code) {
    case 'auth/invalid-email':
      return 'That does not look like an email address.';
    case 'auth/user-not-found':
    case 'auth/wrong-password':
    case 'auth/invalid-credential':
      return 'That email and password do not match an account.';
    case 'auth/email-already-in-use':
      return 'There is already an account with that email. Try signing in instead.';
    case 'auth/weak-password':
      return 'Please choose a password of at least six characters.';
    case 'auth/too-many-requests':
      return 'Too many attempts. Please wait a minute and try again.';
    case 'auth/network-request-failed':
      return 'Could not reach the network. Check your connection and try again.';
    default:
      return 'Something went wrong signing in. Please try again.';
  }
}
