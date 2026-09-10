package com.careloop.app.call

import android.util.Log
import com.careloop.app.di.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the push that makes CareLoop ring.
 *
 * This is the other half of the signature interaction. The backend sends a
 * high-priority, data-only FCM message; this class turns it into a `CallStyle`
 * notification with a full-screen intent, and Android renders a real incoming
 * call over the lock screen.
 *
 * ## Things here that are easy to get wrong, and why they are written this way
 *
 * **1. The backend must send data-only, and this is why.** If the message carries
 * a `notification` block, the system tray handles it while the app is
 * backgrounded and [onMessageReceived] never runs. Since this method is where the
 * call UI is built, a `notification` block would silently reduce our incoming call
 * to an ordinary banner. The server side has a matching comment; if you change one,
 * change both.
 *
 * **2. Do the minimum here, immediately.** There is a budget of roughly ten
 * seconds before the system may kill the process, and no warning when it does.
 * Posting a notification takes milliseconds. Anything slower, such as fetching
 * the patient's medication list, must not happen on this path. It does not: the
 * call screen loads its own data after the user answers.
 *
 * **3. Never start a foreground service from here.** It is permitted for
 * high-priority messages, but only while the message *is* high priority. FCM can
 * de-prioritise an app whose calls consistently go unanswered, and at that point
 * `startForegroundService` throws and the call fails entirely. Posting a
 * notification has no such restriction, so the notification is the whole
 * mechanism and the answer action does the rest.
 */
class CareLoopMessagingService : FirebaseMessagingService() {

    /**
     * Scope for the token write. Uses SupervisorJob so a failed write cannot
     * cancel anything else, and is deliberately not tied to the service lifecycle,
     * because the service may be torn down the moment this method returns.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"]
        if (type != TYPE_INCOMING_CALL) {
            Log.d(TAG, "Ignoring message of unrecognised type")
            return
        }

        val callAttemptId = message.data["callAttemptId"].orEmpty()
        val patientId = message.data["patientId"].orEmpty()
        val callerName = message.data["callerName"] ?: "Cara"

        if (callAttemptId.isEmpty() || patientId.isEmpty()) {
            // Malformed push. Ringing without knowing which attempt this is would
            // mean the outcome could never be reported, which corrupts the
            // reasoning engine's evidence. Better to drop it.
            Log.w(TAG, "Incoming call push missing required identifiers")
            return
        }

        // Everything below is synchronous and fast, on purpose. See note 2 above.
        CallSession.begin(callAttemptId = callAttemptId, patientId = patientId)
        CallNotifier.postIncomingCall(applicationContext, callerName = callerName)
    }

    /**
     * Persists a rotated token.
     *
     * Tokens change on reinstall, data clear and device restore. A stale token
     * fails *silently*: FCM accepts the send and the phone simply never rings.
     * That is the worst class of bug for this product, because from the outside it
     * looks like the person ignored their check-in.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            runCatching { AppContainer.repository.registerDeviceToken(token) }
                .onFailure { Log.w(TAG, "Could not register rotated FCM token") }
        }
    }

    private companion object {
        const val TAG = "CareLoopFcm"
        const val TYPE_INCOMING_CALL = "incoming_call"
    }
}

/**
 * The identifiers for the call currently ringing.
 *
 * A deliberately tiny piece of process-global state. The notification's answer
 * action launches an Activity through a PendingIntent, and threading identifiers
 * through intent extras across the notification boundary is fiddly and easy to
 * get wrong when the process has been restarted in between.
 *
 * Holding them here means the call screen can always answer the two questions it
 * needs: which attempt am I, and whose call is this. Cleared when the call ends so
 * a stale id can never be attached to a later call.
 */
object CallSession {
    @Volatile
    var callAttemptId: String? = null
        private set

    @Volatile
    var patientId: String? = null
        private set

    fun begin(callAttemptId: String, patientId: String) {
        this.callAttemptId = callAttemptId
        this.patientId = patientId
    }

    fun end() {
        callAttemptId = null
        patientId = null
    }

    /** True for a locally triggered demo call, which has no server attempt id. */
    val isDemo: Boolean get() = callAttemptId == null
}
