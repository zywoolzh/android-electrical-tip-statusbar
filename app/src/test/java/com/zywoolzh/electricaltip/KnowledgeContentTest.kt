package com.zywoolzh.electricaltip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeContentTest {
    @Test
    fun knowledgeBaseCoversEveryRequiredCategory() {
        val categories = KnowledgeContent.tips.map { it.category }.toSet()
        assertEquals(setOf("配电", "MCC", "冷水机组", "UPS", "数据中心规范"), categories)
        assertEquals(15, KnowledgeContent.tips.size)
        assertTrue(KnowledgeContent.tips.all { it.summary.length >= 120 && it.body.length > it.summary.length })
        KnowledgeContent.tips.forEach { it.validate() }
    }

    @Test
    fun seedIdsAreUniqueAndStable() {
        assertEquals(KnowledgeContent.tips.size, KnowledgeContent.tips.map { it.id }.toSet().size)
    }

    @Test
    fun intervalSupportsHoursAndDays() {
        assertEquals(7_200_000L, IntervalCalculator.toMillis(2, IntervalUnit.HOURS))
        assertEquals(172_800_000L, IntervalCalculator.toMillis(2, IntervalUnit.DAYS))
        assertEquals(31_536_000_000L, IntervalCalculator.toMillis(365, IntervalUnit.DAYS))
    }
}
