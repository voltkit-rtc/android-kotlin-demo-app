package `in`.voltkit.livedemo.kotlin.rtc

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import `in`.voltkit.livedemo.kotlin.R

/**
 * Keeps a room or call alive while the app is in the background or the screen is off. Without a foreground service, Android
 * cuts the app's network and microphone a few seconds after it leaves the screen, and the call ends there.
 *
 * Started with startService while the app is visible, and promoted to the foreground at once: if Android refuses, nothing
 * crashes, and the room still works while the app is open. The notification opens the app and has a Leave action.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LEAVE) {
            onLeave?.invoke()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "VoltKit Kotlin"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        val leave = intent?.getStringExtra(EXTRA_LEAVE) ?: "Leave"
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(title, text, leave), serviceType())
        } catch (e: RuntimeException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        onLeave?.invoke()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Microphone once it may be used, so someone on stage keeps talking in the background. A listener who never gave that
     * permission runs as media playback: Android 14 refuses the microphone type without it.
     */
    private fun serviceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return if (mic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        }
    }

    private fun notification(title: String, text: String, leaveLabel: String): Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.deleteNotificationChannel("voltkit_live_calls") // the first build's quieter channel
        manager.createNotificationChannel(
            // Default importance, with no sound or vibration: it stays quiet, but is not folded into the "Silent" group, where
            // Android hides the Leave button until the notification is expanded.
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Live rooms and calls")
                .setShowBadge(false)
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build(),
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val leave = PendingIntent.getService(
            this,
            1,
            Intent(this, CallService::class.java).setAction(ACTION_LEAVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (open != null) setContentIntent(open) }
            .addAction(0, leaveLabel, leave)
            .build()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_LEAVE = "leaveLabel"
        private const val ACTION_LEAVE = "in.voltkit.livedemo.kotlin.LEAVE"
        private const val CHANNEL_ID = "voltkit_live_ongoing"
        private const val NOTIFICATION_ID = 7302

        @Volatile private var onLeave: (() -> Unit)? = null

        /** Calling this again updates the notification, and takes the microphone type once the permission is there. */
        fun start(context: Context, title: String, text: String, leaveLabel: String, onLeave: () -> Unit) {
            this.onLeave = onLeave
            val intent = Intent(context, CallService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_LEAVE, leaveLabel)
            try {
                context.startService(intent)
            } catch (e: RuntimeException) {
                // In the background already: only the background is lost.
            }
        }

        fun stop(context: Context) {
            onLeave = null
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
