package com.zywoolzh.electricaltip

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.TestWorkerBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetIntegrationTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("knowledge.db")
        context.getSharedPreferences("electrical_tip_settings", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(Executor { it.run() }).build())
    }
    @Test fun editsAndFavoritesSurviveDatabaseReopen() {
        val tip = KnowledgeContent.tips.first().copy(title = "我的编辑", favorite = true)
        KnowledgeStore(context).use { it.save(tip) }
        KnowledgeStore(context).use {
            assertEquals(tip, it.all().first())
            assertEquals(15, it.all().size)
        }
    }
    @Test fun importUpdatesExistingKeepsFavoritesAndDoesNotDuplicate() {
        val old = KnowledgeContent.tips.first().copy(favorite = true)
        val fresh = old.copy(id = "my-id", favorite = false)
        KnowledgeStore(context).use {
            it.save(old)
            assertEquals(1 to 1, it.importTips(listOf(old.copy(title = "已更新", favorite = false), fresh)))
            assertEquals(0 to 2, it.importTips(listOf(old.copy(title = "已更新"), fresh)))
            assertEquals(16, it.all().size)
            assertTrue(it.all().first().favorite)
        }
    }
    @Test fun invalidBatchLeavesDatabaseUnchanged() {
        KnowledgeStore(context).use {
            val before = it.all()
            assertThrows(IllegalArgumentException::class.java) {
                it.importTips(listOf(before.first().copy(title = "不应写入"), before.last().copy(title = "")))
            }
            assertEquals(before, it.all())
        }
    }
    @Test fun categoryRotationWrapsAndDeletionSelectsValidFallback() {
        val prefs = AppPreferences(context)
        prefs.category = "UPS"
        assertEquals("builtin-ups-1", KnowledgeRepository.current(context)?.id)
        KnowledgeRepository.move(context, -1)
        assertEquals("builtin-ups-3", KnowledgeRepository.current(context)?.id)
        KnowledgeRepository.move(context, 1)
        assertEquals("builtin-ups-1", KnowledgeRepository.current(context)?.id)
        KnowledgeStore(context).use { it.delete("builtin-ups-1") }
        assertEquals("builtin-ups-2", KnowledgeRepository.current(context)?.id)
        prefs.category = "不存在"
        assertNull(KnowledgeRepository.current(context))
    }
    @Test fun deletingAllDoesNotReseedOnReopen() {
        KnowledgeStore(context).use { store -> store.all().forEach { store.delete(it.id) } }
        KnowledgeStore(context).use { assertTrue(it.all().isEmpty()) }
        assertNull(KnowledgeRepository.current(context))
    }
    @Test fun exportCanRestoreUserLibraryIntoEmptyDatabase() {
        val csv = KnowledgeStore(context).use { store ->
            store.save(KnowledgeContent.tips.first().copy(title = "自定义内容", favorite = true))
            KnowledgeCsv.encode(store.all()).also { store.all().forEach { store.delete(it.id) } }
        }
        KnowledgeStore(context).use {
            it.importTips(KnowledgeCsv.decode(csv))
            assertEquals("自定义内容", it.all().first().title)
            assertTrue(it.all().first().favorite)
        }
    }
    @Test fun widgetInflatesAndPaginationPreservesAllContent() {
        val tip = KnowledgeContent.tips.first()
        val pages = WidgetPagination.pages(tip.summary, 220, 70, 18f)
        assertEquals(tip.summary, pages.joinToString(""))
        assertTrue(pages.size > 1)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 300)
        }
        val root = KnowledgeWidget.views(context, 10, tip, options).apply(context, FrameLayout(context))
        assertEquals(tip.title, root.findViewById<TextView>(R.id.widget_title).text.toString())
        assertTrue(root.findViewById<TextView>(R.id.widget_body).text.isNotEmpty())
        AppPreferences(context).setPage(10, 1000)
        KnowledgeWidget.views(context, 10, tip, options)
        assertTrue(AppPreferences(context).page(10) < 1000)
    }
    @Test fun periodicWorkRotatesAndPauseCancelsIt() {
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        manager.bindAppWidgetIdIfAllowed(42, ComponentName(context, KnowledgeWidget::class.java))
        assertArrayEquals(intArrayOf(42), KnowledgeWidget.ids(context))
        val prefs = AppPreferences(context)
        val first = KnowledgeRepository.current(context)!!.id
        RotationScheduler.schedule(context, restart = true)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RotationScheduler.NAME).get()
        assertEquals(1, work.count { it.state == WorkInfo.State.ENQUEUED })
        val worker = TestWorkerBuilder<RotationWorker>(context, Executor { it.run() }).build()
        worker.doWork()
        assertNotEquals(first, KnowledgeRepository.current(context)!!.id)
        val currentId = prefs.currentId
        prefs.rotationEnabled = false
        RotationScheduler.schedule(context)
        worker.doWork()
        assertEquals(currentId, prefs.currentId)
        assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(RotationScheduler.NAME).get().all { it.state == WorkInfo.State.CANCELLED })
    }
}
