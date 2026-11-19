package com.example.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object MeshServiceBus {
    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val stopRequests: SharedFlow<Unit> = _stopRequests.asSharedFlow()

    fun notifyStopRequested() {
        _stopRequests.tryEmit(Unit)
    }
}

class MeshNodeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isRunning = false
            MeshServiceBus.notifyStopRequested()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val peerCount = intent?.getIntExtra(EXTRA_PEER_COUNT, lastPeerCount) ?: lastPeerCount
        lastPeerCount = peerCount

        ensureChannel(this)
        val notification = buildNotification(this, peerCount)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        runCatching {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.cancel(NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "bluebl_node_relay"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.example.action.START_NODE"
        const val ACTION_STOP = "com.example.action.STOP_NODE"
        const val EXTRA_PEER_COUNT = "extra_peer_count"

        @Volatile
        private var isRunning = false

        @Volatile
        private var lastPeerCount = 0

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
                val existing = mgr.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.node_service_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.node_service_channel_desc)
                        setShowBadge(false)
                    }
                    mgr.createNotificationChannel(channel)
                }
            }
        }

        private fun buildNotification(context: Context, peerCount: Int): Notification {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val stopIntent = Intent(context, MeshNodeService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPending = PendingIntent.getService(
                context,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val title = context.getString(R.string.node_service_notification_title)
            val body = when {
                peerCount == 1 -> "1 dispositivo cercano por Bluetooth · Toca para abrir"
                peerCount > 1 -> "$peerCount dispositivos cercanos por Bluetooth · Toca para abrir"
                else -> context.getString(R.string.node_service_notification_text)
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mesh_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPending)
                .addAction(
                    android.R.drawable.ic_lock_power_off,
                    context.getString(R.string.mesh_notification_action_stop),
                    stopPending
                )
                .build()
        }

        fun start(context: Context, peerCount: Int = lastPeerCount) {
            lastPeerCount = peerCount
            ensureChannel(context)
            if (isRunning) {
                runCatching {
                    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    mgr?.notify(NOTIFICATION_ID, buildNotification(context, peerCount))
                }
                return
            }
            runCatching {
                val intent = Intent(context, MeshNodeService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PEER_COUNT, peerCount)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun updateStatus(context: Context, peerCount: Int) {
            if (!isRunning) return
            lastPeerCount = peerCount
            runCatching {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                mgr?.notify(NOTIFICATION_ID, buildNotification(context, peerCount))
            }
        }

        fun stop(context: Context) {
            isRunning = false
            runCatching {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                mgr?.cancel(NOTIFICATION_ID)
            }
            runCatching {
                context.stopService(Intent(context, MeshNodeService::class.java))
            }
        }
    }
}
