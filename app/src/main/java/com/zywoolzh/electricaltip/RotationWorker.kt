package com.zywoolzh.electricaltip

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RotationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (AppPreferences(applicationContext).rotationEnabled && KnowledgeWidget.ids(applicationContext).isNotEmpty()) {
            KnowledgeRepository.move(applicationContext, 1)
            KnowledgeWidget.updateAll(applicationContext)
        }
        return Result.success()
    }
}

object RotationScheduler {
    const val NAME = "knowledge-widget-rotation"
    fun schedule(context: Context, restart: Boolean = false) {
        val prefs = AppPreferences(context)
        if (!prefs.rotationEnabled || KnowledgeWidget.ids(context).isEmpty()) { cancel(context); return }
        val interval = IntervalCalculator.toMillis(prefs.intervalValue, prefs.intervalUnit)
        val work = PeriodicWorkRequestBuilder<RotationWorker>(interval, TimeUnit.MILLISECONDS)
            .setInitialDelay(interval, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME,
            if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
}
