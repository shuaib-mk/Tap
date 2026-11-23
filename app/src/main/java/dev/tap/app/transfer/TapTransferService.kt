package dev.tap.app.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.tap.app.MainActivity
import dev.tap.app.protocol.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground Service for TAP transfers.
 * Ensures transfers continue reliably when the screen turns off or the user switches apps.
 * Displays real-time progress notification with file name, speed, and cancel action.
 */
class TapTransferService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START_FOREGROUND -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "TAP Transfer"
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Transferring files..."
                startForeground(NOTIFICATION_ID, buildNotification(title, message, 0, 100))
            }
            ACTION_UPDATE_PROGRESS -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "TAP Transfer"
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Transferring files..."
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val total = intent.getIntExtra(EXTRA_TOTAL, 100)
                val notification = buildNotification(title, message, progress, total)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TAP File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress of active TAP file transfers"
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        message: String,
        progress: Int,
        maxProgress: Int
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, TapTransferService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .setProgress(maxProgress, progress, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "tap_transfers_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_FOREGROUND = "dev.tap.app.START_TRANSFER"
        const val ACTION_UPDATE_PROGRESS = "dev.tap.app.UPDATE_PROGRESS"
        const val ACTION_STOP_SERVICE = "dev.tap.app.STOP_TRANSFER"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_TOTAL = "extra_total"

        fun start(context: Context, title: String, message: String) {
            val intent = Intent(context, TapTransferService::class.java).apply {
                action = ACTION_START_FOREGROUND
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, title: String, message: String, progress: Int, total: Int) {
            val intent = Intent(context, TapTransferService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_TOTAL, total)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TapTransferService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
