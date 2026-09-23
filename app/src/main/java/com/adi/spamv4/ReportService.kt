package com.adi.spamv4

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ReportService : Service() {

    companion object {
        const val CHANNEL_ID = "adispam_report"
        const val NOTIF_ID = 6767
        var isRunning = false
            private set
        var logListener: ((String) -> Unit)? = null
        var statsListener: ((ReportEngine.Stats) -> Unit)? = null
        var deadListener: (() -> Unit)? = null

        private var job: Job? = null
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun start(ctx: Context, config: ReportEngine.Config) {
            val intent = Intent(ctx, ReportService::class.java)
            startServiceCompat(ctx, intent)
            job?.cancel()
            job = scope.launch {
                ReportEngine(IgApi()).run(
                    config = config,
                    onLog = { logListener?.invoke(it) },
                    onStats = { statsListener?.invoke(it) },
                    onSessionDead = { deadListener?.invoke() }
                )
                stop(ctx)
            }
            isRunning = true
        }

        fun stop(ctx: Context) {
            job?.cancel()
            job = null
            isRunning = false
            ctx.stopService(Intent(ctx, ReportService::class.java))
        }

        private fun startServiceCompat(ctx: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AdiSpamV4")
            .setContentText("reporting in progress")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Report", NotificationManager.IMPORTANCE_LOW
                )
                mgr.createNotificationChannel(ch)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}