package com.zywoolzh.electricaltip

import java.util.UUID

/** CSV supports UTF-8 BOM, CRLF, commas, escaped quotes, and multiline fields. */
object KnowledgeCsv {
    const val MAX_BYTES = 2 * 1024 * 1024
    private val headers = listOf("id", "category", "title", "summary", "body", "source", "favorite")
    private val aliases = mapOf("编号" to "id", "分类" to "category", "标题" to "title",
        "摘要" to "summary", "正文" to "body", "来源" to "source", "收藏" to "favorite")

    fun decode(text: String): List<KnowledgeTip> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "文件不能超过 2 MB" }
        val rows = parse(text.removePrefix("\uFEFF"))
        require(rows.size > 1) { "CSV 必须包含表头和至少一条知识" }
        val columns = rows.first().map { aliases[it.trim()] ?: it.trim().lowercase() }
        require(columns.toSet().size == columns.size) { "CSV 表头有重复列" }
        require(columns.containsAll(listOf("category", "title", "summary", "body"))) {
            "缺少列：需要 category,title,summary,body；也可使用 分类,标题,摘要,正文"
        }
        require(rows.size <= 2001) { "一次最多导入 2000 条" }
        val tips = rows.drop(1).mapIndexed { index, row ->
            require(row.size == columns.size) { "第 ${index + 2} 条记录的列数与表头不一致" }
            fun field(name: String) = columns.indexOf(name).let { if (it < 0) "" else row[it].trim() }
            val favorite = field("favorite")
            require(favorite in listOf("", "0", "1", "true", "false")) { "收藏列只支持 0/1 或 true/false" }
            KnowledgeTip(
                field("id").ifBlank { UUID.randomUUID().toString() }, field("category"),
                field("title"), field("summary"), field("body"), field("source"),
                favorite == "1" || favorite == "true",
            ).also { tip ->
                try { tip.validate() } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("第 ${index + 2} 条记录：${e.message}")
                }
            }
        }
        require(tips.map { it.id }.toSet().size == tips.size) { "文件中存在重复编号，请先修正" }
        return tips
    }

    fun encode(tips: List<KnowledgeTip>): String {
        fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
        return "\uFEFF" + (listOf(headers) + tips.map {
            listOf(it.id, it.category, it.title, it.summary, it.body, it.source, if (it.favorite) "1" else "0")
        }).joinToString("\r\n") { row -> row.joinToString(",", transform = ::quote) } + "\r\n"
    }

    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun endField() { row.add(field.toString()); field.clear(); closed = false }
        fun endRow() {
            endField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else field.append(c)
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closed) { "CSV 引号格式错误" }; quoted = true }
                ',' -> endField()
                '\r', '\n' -> {
                    endRow()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> { require(!closed) { "CSV 闭合引号后只能是逗号或换行" }; field.append(c) }
            }
            i++
        }
        require(!quoted) { "CSV 存在未闭合的引号" }
        if (field.isNotEmpty() || row.isNotEmpty() || closed) endRow()
        return rows
    }
}
