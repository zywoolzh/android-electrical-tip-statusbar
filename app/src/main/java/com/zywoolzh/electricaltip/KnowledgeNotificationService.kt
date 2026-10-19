package com.zywoolzh.electricaltip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class KnowledgeNotificationService : Service() {
    private lateinit var preferences: AppPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val rotateRunnable = object : Runnable {
        override fun run() {
            if (preferences.rotationEnabled) {
                preferences.currentIndex += 1
                showCurrentTip()
                scheduleNextRotation()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        scheduleNextRotation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NEXT -> {
                preferences.currentIndex += 1
                showCurrentTip()
            }
            ACTION_REFRESH -> showCurrentTip()
        }
        scheduleNextRotation()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(rotateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleNextRotation() {
        handler.removeCallbacks(rotateRunnable)
        if (preferences.rotationEnabled) {
            handler.postDelayed(
                rotateRunnable,
                IntervalCalculator.toMillis(preferences.intervalValue, preferences.intervalUnit),
            )
        }
    }

    private fun showCurrentTip() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val tip = KnowledgeContent.tips[preferences.currentIndex]
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KnowledgeNotificationService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_electrical)
            .setContentTitle("${tip.category} · 电气知识")
            .setContentText(tip.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip.text))
            .setContentIntent(openAppIntent)
            .addAction(0, "换一条", nextIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "电气知识常驻通知",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在状态栏持续展示并轮换数据中心电气知识点"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_NEXT = "com.zywoolzh.electricaltip.NEXT"
        const val ACTION_REFRESH = "com.zywoolzh.electricaltip.REFRESH"
        private const val CHANNEL_ID = "electrical_knowledge"
        private const val NOTIFICATION_ID = 1001
    }
}
