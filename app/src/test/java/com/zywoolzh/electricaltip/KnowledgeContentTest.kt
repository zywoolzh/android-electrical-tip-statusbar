package com.zywoolzh.electricaltip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeContentTest {
    @Test
    fun knowledgeBaseCoversEveryRequiredCategory() {
        val categories = KnowledgeContent.tips.map { it.category }.toSet()
        assertEquals(setOf("配电", "MCC", "冷水机组", "UPS", "数据中心规范"), categories)
        assertTrue(KnowledgeContent.tips.all { it.text.isNotBlank() })
    }

    @Test
    fun indexWrapsInBothDirections() {
        assertEquals(0, KnowledgeContent.normalizedIndex(KnowledgeContent.tips.size))
        assertEquals(KnowledgeContent.tips.lastIndex, KnowledgeContent.normalizedIndex(-1))
    }

    @Test
    fun intervalSupportsHoursAndDays() {
        assertEquals(7_200_000L, IntervalCalculator.toMillis(2, IntervalUnit.HOURS))
        assertEquals(172_800_000L, IntervalCalculator.toMillis(2, IntervalUnit.DAYS))
    }
}
