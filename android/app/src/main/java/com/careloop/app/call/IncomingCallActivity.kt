package com.careloop.app.call

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import com.careloop.app.data.model.CallState
import com.careloop.app.ui.screens.call.IncomingCallScreen
import com.careloop.app.ui.screens.call.LiveCallScreen
import com.careloop.app.ui.theme.CareLoopTheme

/**
 * The full-screen incoming call.
 *
 * This activity is the product's signature surface, and the reason CareLoop feels like a
 * companion phoning you rather than an app nagging you.
 *
 * ## Two things here are easy to get wrong
 *
 * 1. **[setShowWhenLocked] and [setTurnScreenOn] must be called in `onCreate`, before
 *    `setContent`.** They configure the window itself. Calling them from inside a
 *    `@Composable` does nothing at all — the call silently fails to appear over the lock
 *    screen, which is the one place it most needs to work.
 *
 * 2. **`showWhenLocked` shows the call over the keyguard; it does not dismiss it.** That is
 *    correct and deliberate: Margaret can see and answer Cara without unlocking, but
 *    anything sensitive behind the lock screen stays locked. We only ask the system to
 *    dismiss the keyguard when she actually answers.
 *
 * The activity is also declared `excludeFromRecents` and `noHistory` in the manifest so a
 * finished call does not linger in the recents list as something to accidentally reopen.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureLockScreenBehaviour()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // If launched via the notification's answer action we skip the ringing state
        // entirely and open straight into the conversation.
        val answeredImmediately = intent?.getBooleanExtra(CallNotifier.EXTRA_ANSWERED, false) == true

        setContent {
            CareLoopTheme(darkTheme = true) {
                var state by remember {
                    mutableStateOf(
                        if (answeredImmediately) CallState.IN_PROGRESS else CallState.RINGING
                    )
                }

                when (state) {
                    CallState.RINGING -> IncomingCallScreen(
                        onAnswer = {
                            CallNotifier.cancel(this@IncomingCallActivity)
                            dismissKeyguardForCall()
                            state = CallState.IN_PROGRESS
                        },
                        onDecline = {
                            CallNotifier.cancel(this@IncomingCallActivity)
                            finish()
                        },
                    )

                    CallState.IN_PROGRESS -> LiveCallScreen(
                        onEndCall = {
                            CallNotifier.cancel(this@IncomingCallActivity)
                            finish()
                        },
                    )

                    else -> finish()
                }
            }
        }
    }

    /**
     * Must run before `setContent`. See the class comment.
     */
    private fun configureLockScreenBehaviour() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    /**
     * Only called once the user has actually answered. Requesting keyguard dismissal at
     * ring time would expose the device to anyone who picked up the phone.
     */
    private fun dismissKeyguardForCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

/** Convenience for the debug trigger and, later, the FCM receiver. */
fun Context.startIncomingCallDemo() {
    CallNotifier.postIncomingCall(this)
}
