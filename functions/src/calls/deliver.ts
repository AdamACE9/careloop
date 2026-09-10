import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging, type Message } from 'firebase-admin/messaging';
import type { CallAttemptDoc, DeviceTokenDoc } from '../types.js';
import { hashId, logEvent, logWarn, logError } from '../lib/logging.js';

/**
 * Delivering an incoming call to the elder's phone.
 *
 * This is the mechanism behind CareLoop's signature interaction. There is no
 * telephony involved: a high-priority FCM **data message** wakes the app, and the
 * app posts a `CallStyle` notification with a full-screen intent, which Android
 * renders as a real incoming-call screen over the lock screen.
 *
 * ## Three things here are load-bearing and easy to get wrong
 *
 * 1. **The message must be data-only.** If it carries a `notification` block, the
 *    system tray handles it while the app is backgrounded and
 *    `onMessageReceived` never fires — which is precisely where we build the call
 *    UI. A `notification` block would turn our incoming call into a banner that
 *    does nothing. There is deliberately no `notification` field below.
 *
 * 2. **Priority must be high.** It is the only thing that gets a message through
 *    Doze. Note that FCM may de-prioritise an app whose high-priority messages
 *    consistently produce no user interaction — so a stream of unanswered calls
 *    can itself degrade delivery. That is one more reason the retry logic is
 *    conservative rather than hammering.
 *
 * 3. **TTL must be short.** A medication check-in that arrives ninety minutes
 *    late is worse than one that never arrives: the person is confused by a call
 *    about a dose they already took, and the agent records a bogus outcome. The
 *    TTL below deliberately drops a call rather than delivering it stale.
 */

/** Short enough that a delayed call is dropped rather than delivered stale. */
const CALL_TTL_SECONDS = 300;

export interface DeliveryResult {
  delivered: boolean;
  /** Stable code, safe to log. Null on success. */
  reason: string | null;
  /** True when the token is dead and should be removed. */
  tokenInvalid: boolean;
}

/**
 * Sends the push that makes the phone ring.
 *
 * Returns a result rather than throwing on delivery failure: "her phone is off"
 * is a normal, expected outcome that the retry/escalation engine needs to reason
 * about, not an exception. Only genuine internal faults throw.
 */
export async function deliverCall(params: {
  patientId: string;
  callAttemptId: string;
  callerName: string;
  attemptNumber: number;
}): Promise<DeliveryResult> {
  const { patientId, callAttemptId, callerName, attemptNumber } = params;
  const db = getFirestore();

  const tokenSnap = await db.doc(`deviceTokens/${patientId}`).get();
  if (!tokenSnap.exists) {
    logWarn('call.deliver.no_token', { patientHash: hashId(patientId), callAttemptId });
    return { delivered: false, reason: 'NO_DEVICE_TOKEN', tokenInvalid: false };
  }

  const device = tokenSnap.data() as DeviceTokenDoc;

  const message: Message = {
    token: device.token,

    // Data-only. See the note above — adding `notification` here breaks the
    // entire feature in a way that is invisible until you test on a backgrounded
    // device.
    data: {
      type: 'incoming_call',
      callAttemptId,
      patientId,
      callerName,
      attemptNumber: String(attemptNumber),
      initiatedAt: new Date().toISOString(),
    },

    android: {
      priority: 'high',
      ttl: CALL_TTL_SECONDS * 1000,
      // Collapsing on the patient means a newer call replaces an older undelivered
      // one for the same person. Two identical check-in calls queued behind a
      // flaky connection should ring once, not twice.
      collapseKey: `careloop_call_${patientId}`,
    },

    fcmOptions: { analyticsLabel: 'careloop_checkin_call' },
  };

  try {
    await getMessaging().send(message);
    logEvent('call.deliver.sent', {
      patientHash: hashId(patientId),
      callAttemptId,
      attemptNumber,
    });
    return { delivered: true, reason: null, tokenInvalid: false };
  } catch (error) {
    const code = (error as { code?: string })?.code ?? 'unknown';

    // A dead registration is permanent: the app was uninstalled, data was
    // cleared, or the token aged out. Retrying achieves nothing, and leaving the
    // token in place means every future call silently fails. Signal for cleanup.
    const tokenInvalid =
      code === 'messaging/registration-token-not-registered' ||
      code === 'messaging/invalid-registration-token' ||
      code === 'messaging/invalid-argument';

    logError('call.deliver.failed', code, {
      patientHash: hashId(patientId),
      callAttemptId,
      tokenInvalid,
    }, error);

    return {
      delivered: false,
      reason: tokenInvalid ? 'DEVICE_UNREGISTERED' : 'DELIVERY_FAILED',
      tokenInvalid,
    };
  }
}

/**
 * Creates the attempt ledger entry and delivers the call.
 *
 * The attempt document is written **before** the push goes out, so a call that
 * fails mid-delivery still leaves a record. An undelivered call is evidence the
 * reasoning engine needs — a phone that has been off for two days is a different
 * situation from a person declining to answer, and the agent must be able to tell
 * them apart.
 */
export async function createAndDeliverCall(params: {
  patientId: string;
  trigger: CallAttemptDoc['trigger'];
  callerName: string;
  attemptNumber: number;
  requestedBy: string | null;
}): Promise<{ callAttemptId: string; result: DeliveryResult }> {
  const { patientId, trigger, callerName, attemptNumber, requestedBy } = params;
  const db = getFirestore();

  const attemptRef = db.collection(`patients/${patientId}/callAttempts`).doc();

  const attempt: CallAttemptDoc = {
    sentAt: new Date().toISOString(),
    trigger,
    outcome: 'pending',
    outcomeReportedAt: null,
    durationSeconds: null,
    attemptNumber,
    deliveryError: null,
    requestedBy,
  };
  await attemptRef.set(attempt);

  const result = await deliverCall({
    patientId,
    callAttemptId: attemptRef.id,
    callerName,
    attemptNumber,
  });

  if (!result.delivered) {
    await attemptRef.update({
      outcome: 'undeliverable',
      deliveryError: result.reason,
      outcomeReportedAt: new Date().toISOString(),
    });

    if (result.tokenInvalid) {
      await db.doc(`deviceTokens/${patientId}`).delete().catch(() => {
        // Best effort. A stale token left behind is untidy but harmless; the next
        // delivery attempt will try to remove it again.
      });
    }
  }

  return { callAttemptId: attemptRef.id, result };
}

/**
 * Registers (or refreshes) a device's FCM token.
 *
 * Called from the app on start and from `onNewToken`. Tokens rotate on reinstall,
 * data clear, and device restore, and a stale token is a silent failure: calls
 * are "sent" successfully to a device that no longer exists.
 */
export async function registerDeviceToken(
  uid: string,
  token: string,
  platform: DeviceTokenDoc['platform'],
): Promise<void> {
  await getFirestore().doc(`deviceTokens/${uid}`).set(
    {
      token,
      platform,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  logEvent('device.token.registered', { patientHash: hashId(uid), platform });
}
