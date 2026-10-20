package com.zywoolzh.electricaltip

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w400dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRenderTest {
    @Test fun cardFitsAndCanBeRenderedAtBothSizes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        for ((width, height) in listOf(360 to 400, 250 to 250)) {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            }
            val tip = KnowledgeContent.tips.first()
            val view = KnowledgeWidget.views(context, 100, tip, options).apply(context, FrameLayout(context))
            val w = (width * density).toInt()
            val h = (height * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val body = view.findViewById<TextView>(R.id.widget_body)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/widget-preview-${width}x$height.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("${width}x$height text height=${body.layout.height} body=${body.height} lines=${body.lineCount} text=${body.text}", body.layout.height <= body.height)
            assertTrue(view.findViewById<View>(R.id.tip_next).bottom <= view.height)
        }
    }
}
