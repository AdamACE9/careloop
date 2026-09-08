package com.careloop.app.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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

                // TODO(backend): report the decline so the agent can reason about it.
                // A declined call is signal, not just absence: the retry strategy weighs
                // how critical the medication is against how many attempts have already
                // been made, and decides whether to retry soon, wait, or escalate.
                // A declined critical-medication check-in warrants a sooner retry than a
                // declined routine one.
                Log.d(TAG, "Call declined by user")
            }
        }
    }

    private companion object {
        const val TAG = "CallActionReceiver"
    }
}
