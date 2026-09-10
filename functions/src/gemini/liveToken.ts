import { GEMINI_API_HOST, LIVE_TOKEN_TTL_MINUTES } from '../lib/config.js';
import { logEvent, logError } from '../lib/logging.js';

/**
 * Minting short-lived tokens for direct client → Gemini Live connections.
 *
 * ## Why this exists
 *
 * The Android app needs a bidirectional audio WebSocket to Gemini. Two ways to
 * do that, and only one is acceptable:
 *
 *   ✗ Ship the API key in the APK. An APK is a zip file; anyone can pull the key
 *     out in about a minute and spend the quota. Non-starter.
 *
 *   ✗ Relay all audio through a Cloud Function. Doubles latency on a real-time
 *     voice call, costs egress on every second of audio, and turns one flaky
 *     connection into two.
 *
 *   ✓ Mint an ephemeral token here, hand it to the app, let the app connect
 *     directly. The key never leaves the server, and audio takes the short path.
 *
 * The token is deliberately narrow: single use, short expiry, and constrained to
 * one model and modality. Even if one leaked, it buys an attacker a single audio
 * session that expires in minutes.
 *
 * ## API surface caveat
 *
 * The ephemeral-token endpoint lives under a preview API version and its exact
 * request shape has moved before. If token minting starts failing after a Gemini
 * release, this request body is the first thing to check — see
 * docs/MORNING_CHECKLIST.md. The failure is loud (an HTTP error here, surfaced to
 * the caller) rather than silent, which is intentional.
 */

export interface EphemeralToken {
  token: string;
  expiresAt: Date;
}

interface AuthTokenResponse {
  name?: string;
  expireTime?: string;
}

/**
 * Mints a single-use token for one Live session.
 *
 * @param apiKey  The Gemini API key. Never logged, never returned to a client.
 * @param model   Live model id the token is constrained to.
 */
export async function mintLiveToken(
  apiKey: string,
  model: string,
): Promise<EphemeralToken> {
  const now = Date.now();
  const expireTime = new Date(now + LIVE_TOKEN_TTL_MINUTES * 60_000);

  // The window in which the session must START. Short on purpose: the app asks
  // for a token at the moment the user answers, so it should be used within
  // seconds. A long start window is just a longer theft window.
  const newSessionExpireTime = new Date(now + 2 * 60_000);

  const body = {
    uses: 1,
    expireTime: expireTime.toISOString(),
    newSessionExpireTime: newSessionExpireTime.toISOString(),
    // Constraining the token means a stolen one cannot be repurposed for a
    // different, more expensive model.
    liveConnectConstraints: {
      model,
      config: {
        responseModalities: ['AUDIO'],
      },
    },
  };

  const response = await fetch(
    `https://${GEMINI_API_HOST}/v1alpha/auth_tokens?key=${encodeURIComponent(apiKey)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    },
  );

  if (!response.ok) {
    // Deliberately does not include the response body: an auth error response can
    // echo back parts of the request, and the request contains the API key.
    logError('gemini.token.mint_failed', `HTTP_${response.status}`, {
      status: response.status,
    });
    throw new GeminiTokenError(
      response.status === 429
        ? 'QUOTA_EXHAUSTED'
        : response.status === 403
          ? 'API_KEY_INVALID'
          : 'TOKEN_MINT_FAILED',
      response.status,
    );
  }

  const data = (await response.json()) as AuthTokenResponse;
  if (!data.name) {
    throw new GeminiTokenError('TOKEN_MINT_FAILED', 500);
  }

  logEvent('gemini.token.minted', { ttlMinutes: LIVE_TOKEN_TTL_MINUTES });

  return {
    // The token IS `name` in the response. Never log this value.
    token: data.name,
    expiresAt: data.expireTime ? new Date(data.expireTime) : expireTime,
  };
}

export class GeminiTokenError extends Error {
  constructor(
    public readonly code: 'QUOTA_EXHAUSTED' | 'API_KEY_INVALID' | 'TOKEN_MINT_FAILED',
    public readonly status: number,
  ) {
    super(code);
    this.name = 'GeminiTokenError';
  }
}

/**
 * Whether we are within the free tier's concurrency budget for live sessions.
 *
 * The free tier allows only a handful of requests per minute, so two scheduled
 * calls landing simultaneously can put one of them into a 429. That is a bad
 * failure: the person's phone rings, they answer, and Cara cannot speak.
 *
 * This is a coarse Firestore counter rather than precise accounting. It exists to
 * make the failure *predictable* — we would rather delay a call by a minute and
 * have it work than fire it now and have it fail after the person picks up.
 *
 * Fails OPEN. If the check itself errors, we let the call proceed: a missed
 * medication check-in is worse than a possible quota error.
 */
export async function reserveLiveSessionSlot(
  db: FirebaseFirestore.Firestore,
  maxConcurrent: number,
): Promise<boolean> {
  const windowId = Math.floor(Date.now() / 60_000);
  const ref = db.doc(`_liveSessions/${windowId}`);

  try {
    return await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const count = (snap.get('count') as number | undefined) ?? 0;
      if (count >= maxConcurrent) return false;
      tx.set(
        ref,
        { count: count + 1, expiresAt: new Date((windowId + 5) * 60_000) },
        { merge: true },
      );
      return true;
    });
  } catch {
    return true; // fail open
  }
}
