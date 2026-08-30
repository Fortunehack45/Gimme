package org.airshare.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.airshare.app.AirShareApplication
import org.airshare.app.R
import org.airshare.app.ui.transfer.TransferProgressActivity

class TransferForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AirShare::TransferWakeLock")
        wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val sessionName = intent?.getStringExtra(EXTRA_SESSION_NAME) ?: "File Transfer"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
        val speedText = intent?.getStringExtra(EXTRA_SPEED_TEXT) ?: "0.0 MB/s"

        when (action) {
            ACTION_START -> {
                val notification = buildNotification(sessionName, speedText, progress)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_UPDATE -> {
                val notification = buildNotification(sessionName, speedText, progress)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, speedText: String, progress: Int): Notification {
        val contentIntent = Intent(this, TransferProgressActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AirShareApplication.CHANNEL_TRANSFERS)
            .setContentTitle(title)
            .setContentText("Transferring: $speedText ($progress%)")
            .setSmallIcon(R.drawable.ic_send)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "org.airshare.action.START"
        const val ACTION_UPDATE = "org.airshare.action.UPDATE"
        const val ACTION_STOP = "org.airshare.action.STOP"

        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED_TEXT = "extra_speed_text"

        fun startService(context: Context, sessionName: String) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_NAME, sessionName)
                putExtra(EXTRA_PROGRESS, 0)
                putExtra(EXTRA_SPEED_TEXT, "Starting...")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, sessionName: String, progress: Int, speedMBs: Double) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_SESSION_NAME, sessionName)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_SPEED_TEXT, "%.2f MB/s".format(speedMBs))
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
