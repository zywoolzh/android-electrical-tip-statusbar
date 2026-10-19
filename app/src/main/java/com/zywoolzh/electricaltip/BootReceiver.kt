package com.zywoolzh.electricaltip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && AppPreferences(context).rotationEnabled) {
            context.startForegroundService(Intent(context, KnowledgeNotificationService::class.java))
        }
    }
}
