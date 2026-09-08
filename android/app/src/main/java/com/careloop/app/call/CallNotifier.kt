package com.careloop.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.getSystemService
import com.careloop.app.R

/**
 * Builds and posts Cara's incoming call.
 *
 * This is the product's signature interaction: at the scheduled time the elder's phone
 * shows a **real incoming-call screen**, not a notification banner — the same mechanism
 * WhatsApp and Signal use, riding on data/wifi with no telephony cost.
 *
 * ## The Android 14 constraint that shapes this whole file
 *
 * `USE_FULL_SCREEN_INTENT` stopped being freely available in Android 14 (API 34). It is
 * now auto-granted **only** to apps whose core function is calling or alarms. A
 * medication/health app does not automatically qualify, so on a modern device
 * [NotificationManager.canUseFullScreenIntent] can return `false` and our call would
 * silently degrade to a banner the user can miss.
 *
 * There is a real argument that CareLoop *is* a calling app — it receives genuine
 * bidirectional voice calls. That is a Play-policy judgement we cannot resolve unilaterally,
 * so this code is written for the pessimistic case:
 *
 * 1. Always check [canRingFullScreen] before attaching a full-screen intent.
 * 2. When it is unavailable, still ring, vibrate and wake the screen — degrade to a
 *    high-priority `CallStyle` heads-up notification that looks deliberate, not broken.
 * 3. Earn the permission during onboarding, in plain language, rather than ambushing the
 *    user with a system dialog.
 *
 * ## A distinction that is easy to get wrong
 *
 * The **full-screen intent** launches the *ringing* UI. The **answer action** is a
 * separate intent fired when the user accepts. They are different moments and must not be
 * wired to the same PendingIntent — doing so makes the phone behave as though the call was
 * answered the instant it arrives.
 */
object CallNotifier {

    const val CHANNEL_ID = "careloop_calls"
    const val NOTIFICATION_ID = 2001

    const val ACTION_ANSWER = "com.careloop.app.ACTION_ANSWER"
    const val ACTION_DECLINE = "com.careloop.app.ACTION_DECLINE"
    const val EXTRA_ANSWERED = "answered"

    /**
     * Must be `IMPORTANCE_HIGH` or higher. At `DEFAULT` or below the system will never
     * present a full-screen intent, no matter what permissions are granted.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Check-in calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Cara's daily check-in calls. These ring like a phone call."
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 800, 1000, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Whether we may present a genuine full-screen incoming call.
     *
     * Below API 34 this was effectively always allowed. From API 34 it is a special-access
     * permission the user grants in Settings.
     */
    fun canRingFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService<NotificationManager>() ?: return false
        return manager.canUseFullScreenIntent()
    }

    /**
     * Posts the incoming call.
     *
     * @return `true` if it was presented full-screen, `false` if it degraded to heads-up.
     * The caller may use this to decide whether to nudge the user about the permission
     * afterwards — never before, and never mid-call.
     */
    fun postIncomingCall(context: Context): Boolean {
        ensureChannel(context)

        val cara = Person.Builder()
            .setName("Cara")
            .setImportant(true)
            // Marked as a bot deliberately. Being transparent that Cara is an AI is a
            // trust decision, not a legal one — research is consistent that hiding it
            // damages adoption rather than helping it.
            .setBot(true)
            .build()

        val fullScreenIntent = pendingActivity(
            context,
            requestCode = 0,
            intent = Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )

        // Answering opens the same activity, but tells it to go straight into the call.
        val answerIntent = pendingActivity(
            context,
            requestCode = 1,
            intent = Intent(context, IncomingCallActivity::class.java).apply {
                action = ACTION_ANSWER
                putExtra(EXTRA_ANSWERED, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )

        val declineIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, CallActionReceiver::class.java).apply { action = ACTION_DECLINE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val fullScreenAllowed = canRingFullScreen(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(cara, declineIntent, answerIntent)
            )
            .setContentTitle("Cara")
            .setContentText("Your daily check-in")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Keeps the call from being casually swiped away mid-ring.
            .setOngoing(true)
            .setAutoCancel(false)

        if (fullScreenAllowed) {
            // `true` = present it even if the device is currently in use, not only when locked.
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        // Safe to call without POST_NOTIFICATIONS: if the permission is missing the
        // platform drops the notification rather than throwing. The permission is
        // requested in-context during onboarding, which measurably outperforms
        // asking at first launch.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())

        return fullScreenAllowed
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun pendingActivity(context: Context, requestCode: Int, intent: Intent) =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/*
 * TODO(backend): FCM entry point.
 *
 * Add the Firebase dependency, then a service extending FirebaseMessagingService:
 *
 *   class CareLoopMessagingService : FirebaseMessagingService() {
 *       override fun onMessageReceived(message: RemoteMessage) {
 *           // Must be a high-priority *data* message, not a notification message —
 *           // notification messages are handled by the system when the app is
 *           // backgrounded and will never reach this callback, so the call would
 *           // never ring.
 *           CallNotifier.postIncomingCall(this)
 *       }
 *       override fun onNewToken(token: String) {
 *           // TODO: register token against the elder's Firestore document
 *       }
 *   }
 *
 * and register it in AndroidManifest.xml with the MESSAGING_EVENT intent filter.
 *
 * Known field risk worth testing early: aggressive OEM battery management (Xiaomi,
 * Samsung, Huawei) can delay or drop high-priority FCM for backgrounded apps. Test on
 * those specific vendors before relying on delivery, and consider a WorkManager
 * safety-net check.
 */
