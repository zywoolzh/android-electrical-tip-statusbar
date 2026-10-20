package com.zywoolzh.electricaltip

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.text.LineBreaker
import android.os.Bundle
import android.text.StaticLayout
import android.text.Layout
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import java.util.concurrent.Executors

class KnowledgeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = dispatch {
        updateAll(context)
        RotationScheduler.schedule(context)
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = dispatch {
        AppPreferences(context).setPage(id, 0)
        updateAll(context)
    }
    override fun onDisabled(context: Context) { RotationScheduler.cancel(context) }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { AppPreferences(context).setPage(it, 0) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            dispatch {
                val prefs = AppPreferences(context)
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                when (intent.getStringExtra("command")) {
                    "next" -> KnowledgeRepository.move(context, 1)
                    "previous" -> KnowledgeRepository.move(context, -1)
                    "page_next" -> prefs.setPage(id, prefs.page(id) + 1)
                    "page_previous" -> prefs.setPage(id, (prefs.page(id) - 1).coerceAtLeast(0))
                    "favorite" -> intent.getStringExtra("tip_id")?.let { tipId ->
                        KnowledgeStore(context).use { it.toggleFavorite(tipId) }
                    }
                }
                updateAll(context)
            }
        } else super.onReceive(context, intent)
    }

    private fun dispatch(block: () -> Unit) {
        val pending = goAsync()
        executor.execute {
            try { block() } catch (e: Exception) { Log.e("KnowledgeWidget", "Widget update failed", e) }
            finally { pending?.finish() }
        }
    }

    companion object {
        const val ACTION = "com.zywoolzh.electricaltip.WIDGET_ACTION"
        private val executor = Executors.newSingleThreadExecutor()
        fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, KnowledgeWidget::class.java))

        fun updateAll(context: Context) {
            val tip = KnowledgeRepository.current(context)
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { id -> manager.updateAppWidget(id, views(context, id, tip, manager.getAppWidgetOptions(id))) }
        }

        internal fun views(context: Context, id: Int, tip: KnowledgeTip?, options: Bundle): RemoteViews {
            val prefs = AppPreferences(context)
            val metrics = context.resources.displayMetrics
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(180)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 320).coerceAtLeast(220)
            val widthPx = ((widthDp - 40) * metrics.density).toInt()
            val heightPx = ((heightDp - 198) * metrics.density).toInt().coerceAtLeast(24)
            val pages = WidgetPagination.pages(tip?.summary ?: "当前分类没有知识。点这里打开知识库，新增内容或切换分类。",
                widthPx, heightPx, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, metrics))
            val page = prefs.page(id).coerceIn(0, pages.lastIndex)
            prefs.setPage(id, page)
            return RemoteViews(context.packageName, R.layout.widget_knowledge).apply {
                setTextViewText(R.id.widget_category, "电气笔记  /  ${tip?.category ?: "知识库"}")
                setTextViewText(R.id.widget_title, tip?.title ?: "添加你的第一条知识")
                // A split at a paragraph boundary must not render an extra empty
                // line below this page. Keep original page strings for lossless paging.
                setTextViewText(R.id.widget_body, pages[page].trimEnd('\r', '\n'))
                setTextViewText(R.id.widget_page, "${page + 1} / ${pages.size} · 点标题读全文")
                setTextViewText(R.id.tip_favorite, if (tip?.favorite == true) "★ 已收藏" else "☆ 收藏")
                setBoolean(R.id.page_previous, "setEnabled", page > 0)
                setBoolean(R.id.page_next, "setEnabled", page < pages.lastIndex)
                val activity = Intent(context, MainActivity::class.java).apply {
                    putExtra("tip_id", tip?.id)
                    data = Uri.parse("electricaltip://read/${tip?.id.orEmpty()}/$id")
                }
                val open = PendingIntent.getActivity(context, id, activity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_title, open)
                setOnClickPendingIntent(R.id.widget_body, open)
                val settings = PendingIntent.getActivity(context, id,
                    Intent(context, MainActivity::class.java).setData(Uri.parse("electricaltip://library/$id")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                setOnClickPendingIntent(R.id.widget_category, settings)
                mapOf(R.id.tip_next to "next", R.id.tip_previous to "previous", R.id.tip_favorite to "favorite",
                    R.id.page_next to "page_next", R.id.page_previous to "page_previous").forEach { (viewId, command) ->
                    val action = Intent(context, KnowledgeWidget::class.java).setAction(ACTION)
                        .setData(Uri.parse("electricaltip://widget/$id/$command"))
                        .putExtra("command", command).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra("tip_id", tip?.id)
                    setOnClickPendingIntent(viewId, PendingIntent.getBroadcast(context, id, action,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
        }
    }
}

object WidgetPagination {
    fun pages(text: String, width: Int, height: Int, textSize: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val paint = TextPaint().apply { this.textSize = textSize }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setIncludePad(false).setLineSpacing(0f, 1.15f)
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build()
        val pages = mutableListOf<String>()
        var firstLine = 0
        while (firstLine < layout.lineCount) {
            var endLine = firstLine + 1
            while (endLine < layout.lineCount && layout.getLineBottom(endLine) - layout.getLineTop(firstLine) <= height) endLine++
            pages.add(text.substring(layout.getLineStart(firstLine), layout.getLineEnd(endLine - 1)))
            firstLine = endLine
        }
        return pages
    }
}
