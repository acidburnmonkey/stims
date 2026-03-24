package acidburn.stims

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class StimsService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedPackages = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 10000L // Check every 10 seconds

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val packages = intent?.getStringArrayListExtra("selected_packages")

        if (packages != null) {
            selectedPackages = packages.toMutableSet()
            updateNotification()
        }

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)

        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        updateNotification()
        handler.post(monitorRunnable)
    }

    private fun updateNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stims Daemon Running")
            .setContentText("Monitoring ${selectedPackages.size} apps")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun checkForegroundApp() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            releaseWakeLock()
            return
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 15000, time)
        val event = UsageEvents.Event()
        var currentPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentPackage = event.packageName
            }
        }

        if (currentPackage != null && selectedPackages.contains(currentPackage)) {
            acquireWakeLock()
        } else if (currentPackage != null) {
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null || !wakeLock!!.isHeld) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Stims::WakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 min timeout
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID, "Stims Service", NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(serviceChannel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
        releaseWakeLock()
    }

    companion object {
        const val CHANNEL_ID = "StimsServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "acidburn.stims.STOP"
    }
}
