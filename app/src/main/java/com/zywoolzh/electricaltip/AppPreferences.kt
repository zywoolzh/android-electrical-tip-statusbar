package com.zywoolzh.electricaltip

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var rotationEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var intervalValue: Int
        get() = preferences.getInt(KEY_INTERVAL_VALUE, 6)
        set(value) = preferences.edit().putInt(KEY_INTERVAL_VALUE, value.coerceIn(1, 365)).apply()

    var intervalUnit: IntervalUnit
        get() = runCatching {
            IntervalUnit.valueOf(preferences.getString(KEY_INTERVAL_UNIT, IntervalUnit.HOURS.name)!!)
        }.getOrDefault(IntervalUnit.HOURS)
        set(value) = preferences.edit().putString(KEY_INTERVAL_UNIT, value.name).apply()

    var currentIndex: Int
        get() = KnowledgeContent.normalizedIndex(preferences.getInt(KEY_CURRENT_INDEX, 0))
        set(value) = preferences.edit().putInt(KEY_CURRENT_INDEX, KnowledgeContent.normalizedIndex(value)).apply()

    companion object {
        private const val FILE_NAME = "electrical_tip_settings"
        private const val KEY_ENABLED = "rotation_enabled"
        private const val KEY_INTERVAL_VALUE = "interval_value"
        private const val KEY_INTERVAL_UNIT = "interval_unit"
        private const val KEY_CURRENT_INDEX = "current_index"
    }
}
