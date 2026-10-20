package com.zywoolzh.electricaltip

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class KnowledgeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun userCanCreateAndReadCustomKnowledge() {
        compose.onNodeWithText("知识库").performClick()
        compose.onNodeWithText("新增").performClick()
        compose.onNodeWithText("标题（必填）").performTextInput("自定义测试知识")
        compose.onNodeWithText("桌面卡片摘要（必填）").performTextInput("我自己的桌面摘要，支持长期维护。")
        compose.onNodeWithText("详细正文（必填）").performScrollTo().performTextInput("我自己整理的详细正文，用于验证保存和阅读全文。")
        compose.onNodeWithText("保存知识").performScrollTo().performClick()
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("自定义测试知识").fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("编辑").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            println(compose.onRoot().printToString())
            println("Stored custom entries: " + KnowledgeStore(compose.activity).use { it.all().filter { tip -> tip.id.startsWith("builtin-").not() } })
            throw e
        }
        compose.onNodeWithText("自定义测试知识").assertIsDisplayed()
        compose.onNodeWithText("我自己整理的详细正文，用于验证保存和阅读全文。").assertIsDisplayed()
        val stored = KnowledgeStore(compose.activity).use { it.all().single { tip -> tip.title == "自定义测试知识" } }
        org.junit.Assert.assertEquals("自定义", stored.category)
    }
}
