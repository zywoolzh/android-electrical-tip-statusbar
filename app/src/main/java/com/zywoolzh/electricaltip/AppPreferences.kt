package com.zywoolzh.electricaltip

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("electrical_tip_settings", Context.MODE_PRIVATE)
    var rotationEnabled: Boolean
        get() = preferences.getBoolean("rotation_enabled", true)
        set(value) { preferences.edit().putBoolean("rotation_enabled", value).apply() }
    var intervalValue: Int
        get() = preferences.getInt("interval_value", 6).coerceIn(1, 365)
        set(value) { preferences.edit().putInt("interval_value", value.coerceIn(1, 365)).apply() }
    var intervalUnit: IntervalUnit
        get() = runCatching {
            IntervalUnit.valueOf(preferences.getString("interval_unit", "HOURS")!!)
        }.getOrDefault(IntervalUnit.HOURS)
        set(value) { preferences.edit().putString("interval_unit", value.name).apply() }
    var currentId: String
        get() = preferences.getString("current_id", "")!!
        set(value) { preferences.edit().putString("current_id", value).apply() }
    var category: String
        get() = preferences.getString("category", "")!!
        set(value) { preferences.edit().putString("category", value).apply() }
    fun page(widgetId: Int) = preferences.getInt("page_$widgetId", 0)
    fun setPage(widgetId: Int, value: Int) { preferences.edit().putInt("page_$widgetId", value).apply() }
    fun clearPages() {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("page_") }.forEach(editor::remove)
        editor.apply()
    }
}
