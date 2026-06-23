package com.punchlist.pocket.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.punchlist.pocket.R

/**
 * Posts and manages the "due soon" reminder notifications.
 *
 * The channel is created lazily (once per process) the first time a
 * notification is shown; on Android 8+ a channel is mandatory, on older
 * versions channel creation is a no-op.
 *
 * Each job gets its own notification id (derived from its row id kept positive
 * and within the int range) so multiple due-soon jobs don't clobber each other.
 * Because the caller dedupes via [hasBeenNotified], a job only fires once until
 * it leaves the due-soon window — re-posting with the same id just updates the
 * existing notification harmlessly.
 */
object NotificationHelper {

    const val CHANNEL_ID = "due_soon_reminders"
    private const val CHANNEL_NAME = "Due-soon reminders"
    private const val CHANNEL_DESC = "Reminds you when a job's items are due soon."

    /** Stable, positive int notification id for a job row id. */
    private fun jobIdToNotificationId(jobId: Long): Int =
        (jobId % Int.MAX_VALUE).toInt().coerceAtLeast(1)

    /** Creates the notification channel. Safe to call repeatedly. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = CHANNEL_DESC }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts a "due soon" notification for [jobName], quoting how many items are
     * due. Silently no-ops if [NotificationManagerCompat.areNotificationsEnabled]
     * is false (the user denied permission) so we never throw.
     */
    fun postDueSoon(context: Context, jobId: Long, jobName: String, dueSoonCount: Int) {
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val text = if (dueSoonCount > 1) {
            "$dueSoonCount items are due soon in \"$jobName\"."
        } else {
            "An item in \"$jobName\" is due soon."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PunchList reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(jobIdToNotificationId(jobId), notification)
        }
    }
}
