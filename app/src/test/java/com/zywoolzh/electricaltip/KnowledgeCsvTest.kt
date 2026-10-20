package com.zywoolzh.electricaltip

import org.junit.Assert.*
import org.junit.Test

class KnowledgeCsvTest {
    private val sample = KnowledgeTip("custom-1", "自定义", "逗号,与\"引号\"", "摘要\n第二段", "正文\r\n新行", "个人笔记", true)
    @Test fun roundTripPreservesChineseQuotesNewlinesAndFavorites() {
        assertEquals(listOf(sample), KnowledgeCsv.decode(KnowledgeCsv.encode(listOf(sample))))
    }
    @Test fun acceptsChineseHeadersAndGeneratesMissingIds() {
        val tips = KnowledgeCsv.decode("分类,标题,摘要,正文,来源\r\nUPS,旁路,摘要,正文,手册\r\n")
        assertEquals("UPS", tips.single().category)
        assertTrue(tips.single().id.isNotBlank())
    }
    @Test fun sameIdMeansSameItemOnReimport() {
        val encoded = KnowledgeCsv.encode(listOf(sample))
        assertEquals(KnowledgeCsv.decode(encoded).single().id, KnowledgeCsv.decode(encoded).single().id)
    }
    @Test fun rejectsDuplicatesMalformedCsvAndMissingFields() {
        listOf(
            KnowledgeCsv.encode(listOf(sample, sample)),
            "category,title,summary,body\nUPS,标题,摘要,\"未闭合",
            "category,title,summary,body\nUPS,标题,摘要",
            "category,title,summary,body\nUPS,,摘要,正文",
            "category,title,summary\nUPS,标题,摘要",
            "category,title,summary,body\nUPS,\"标题\"invalid,摘要,正文",
        ).forEach { input -> assertThrows(IllegalArgumentException::class.java) { KnowledgeCsv.decode(input) } }
    }
}
