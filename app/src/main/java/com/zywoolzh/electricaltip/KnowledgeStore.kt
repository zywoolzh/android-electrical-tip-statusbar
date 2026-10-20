package com.zywoolzh.electricaltip

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class KnowledgeStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "knowledge.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tips (
            id TEXT PRIMARY KEY, category TEXT NOT NULL, title TEXT NOT NULL,
            summary TEXT NOT NULL, body TEXT NOT NULL, source TEXT NOT NULL,
            favorite INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL
        )""")
        KnowledgeContent.tips.forEach { db.insertOrThrow("tips", null, values(it)) }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<KnowledgeTip> = readableDatabase.query(
        "tips", null, null, null, null, null, "rowid ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                fun field(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                add(KnowledgeTip(field("id"), field("category"), field("title"), field("summary"),
                    field("body"), field("source"), field("favorite") == "1"))
            }
        }
    }

    fun save(tip: KnowledgeTip) {
        tip.validate()
        val db = writableDatabase
        db.beginTransaction()
        try {
            upsert(db, tip)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Validate before the transaction; any failed write rolls back the entire import. */
    fun importTips(tips: List<KnowledgeTip>): Pair<Int, Int> {
        require(tips.isNotEmpty()) { "文件中没有知识条目" }
        require(tips.size <= 2000) { "一次最多导入 2000 条" }
        tips.forEach { it.validate() }
        require(tips.map { it.id }.toSet().size == tips.size) { "文件中存在重复编号，请先修正" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = all().associateBy { it.id }
            tips.forEach { tip ->
                // Import updates content, but preserves a user's existing favorite choice.
                upsert(db, tip.copy(favorite = existing[tip.id]?.favorite ?: tip.favorite))
            }
            db.setTransactionSuccessful()
            return tips.count { it.id !in existing } to tips.count { it.id in existing }
        } finally { db.endTransaction() }
    }

    fun delete(id: String) { writableDatabase.delete("tips", "id = ?", arrayOf(id)) }
    fun toggleFavorite(id: String) {
        writableDatabase.execSQL("UPDATE tips SET favorite = 1 - favorite WHERE id = ?", arrayOf(id))
    }
    private fun upsert(db: SQLiteDatabase, tip: KnowledgeTip) {
        val content = values(tip)
        if (db.update("tips", content, "id = ?", arrayOf(tip.id)) == 0) {
            db.insertOrThrow("tips", null, content)
        }
    }
    private fun values(tip: KnowledgeTip) = ContentValues().apply {
        put("id", tip.id); put("category", tip.category); put("title", tip.title)
        put("summary", tip.summary); put("body", tip.body); put("source", tip.source)
        put("favorite", if (tip.favorite) 1 else 0); put("updated_at", System.currentTimeMillis())
    }
}

/** Serializes manual changes and background rotation within the app process. */
object KnowledgeRepository {
    private val lock = Any()
    fun current(context: Context): KnowledgeTip? = synchronized(lock) {
        val prefs = AppPreferences(context)
        val all = KnowledgeStore(context).use { it.all() }
        val eligible = all.filter { prefs.category.isEmpty() || it.category == prefs.category }
        val current = eligible.firstOrNull { it.id == prefs.currentId } ?: eligible.firstOrNull()
        prefs.currentId = current?.id.orEmpty()
        current
    }
    fun move(context: Context, delta: Int) = synchronized(lock) {
        val prefs = AppPreferences(context)
        val tips = KnowledgeStore(context).use { it.all() }
            .filter { prefs.category.isEmpty() || it.category == prefs.category }
        if (tips.isNotEmpty()) {
            val index = tips.indexOfFirst { it.id == prefs.currentId }
            prefs.currentId = tips[if (index < 0) 0 else Math.floorMod(index + delta, tips.size)].id
        } else prefs.currentId = ""
        prefs.clearPages()
    }
}
