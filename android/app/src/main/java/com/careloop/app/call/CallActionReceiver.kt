package com.careloop.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.careloop.app.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Handles the decline action on the incoming-call notification.
 *
 * Decline is a broadcast rather than an activity because declining should dismiss the call
 * without ever bringing the app to the foreground — tapping "decline" and then being
 * dropped into an app you did not want to open is a small thing that feels broken.
 *
 * Answer is deliberately *not* handled here: it needs to open [IncomingCallActivity], so it
 * is an activity PendingIntent (see [CallNotifier]).
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifier.ACTION_DECLINE -> {
                CallNotifier.cancel(context)

                // A declined call is signal, not absence, and it has to reach the
                // server to be either.
                //
                // This used to be a TODO and a log line. Declining told nobody: the
                // attempt sat pending until the stale sweep eventually wrote it off as
                // missed, so "I saw it and chose not to answer" and "the phone rang in
                // an empty room" became the same fact. They are not remotely the same
                // fact. One is a person exercising a choice this product says they
                // have; the other is the thing the whole product exists to notice.
                //
                // The retry strategy also weighs them differently: a declined
                // critical-medication check-in warrants a sooner retry than a declined
                // routine one, and neither should count toward the consecutive
                // no-answer ladder that eventually alerts the family.
                val attemptId = CallSession.callAttemptId
                CallSession.end()

                if (attemptId == null) {
                    Log.w(TAG, "Declined a call with no attempt id; nothing was reported")
                    return
                }

                // goAsync keeps the process alive past the end of onReceive, which is
                // otherwise the moment the system is free to kill it. Without it the
                // report is a race against teardown, and this codebase has already lost
                // that race twice in other places.
                val pending = goAsync()
                AppContainer.applicationScope.launch {
                    try {
                        AppContainer.repository
                            .reportCallOutcome(attemptId, OUTCOME_DECLINED, durationSeconds = 0)
                            .onFailure { Log.w(TAG, "Could not report the decline") }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "CallActionReceiver"

        /** Matches the values the backend and the security rules accept. */
        const val OUTCOME_DECLINED = "declined"
    }
}
