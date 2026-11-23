/**
 * 索引版本和迁移。表结构在 Room 实体上，升级只追加迁移。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 当前库版本，以及不能丢掉的表。
 *
 * 版本 1 一次建成全部表。迁移语句先经过 [ensureKeepsHistory]。
 */
internal object IndexSchema {
    internal const val VERSION: Int = 1

    /** `meta` 里记下的结构版本。 */
    internal const val SCHEMA_VERSION_KEY: String = "schema_version"

    internal val TABLES: List<String> = listOf(
        "meta",
        "assets",
        "features",
        "embeddings",
        "groups",
        "group_members",
        "candidates",
        "scan_sessions",
        "actions",
        "recycle_bin",
    )

    /** 从版本 1 往后的迁移。下标 0 表示 1 升到 2。目前没有后续版本。 */
    private val MIGRATIONS: List<List<String>> = emptyList()

    /**
     * 拒绝删掉回收站或删除记录的语句。
     *
     * @param sql 一条迁移语句。
     */
    internal fun ensureKeepsHistory(sql: String) {
        val normalized = sql.lowercase().replace(Regex("\\s+"), " ")
        val dropsHistory = Regex("""drop\s+table\s+(if\s+exists\s+)?(actions|recycle_bin)\b""")
        check(!dropsHistory.containsMatchIn(normalized)) {
            "迁移不能删除 actions 或 recycle_bin"
        }
    }

    /**
     * 交给 Room 的迁移。每条语句都先检查会不会删掉历史。
     */
    internal fun roomMigrations(): Array<Migration> {
        return MIGRATIONS.mapIndexed { index, steps ->
            val from = index + 1
            val to = from + 1
            object : Migration(from, to) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    steps.forEach { sql -> exec(db, sql) }
                    db.execSQL(
                        "INSERT OR REPLACE INTO meta(`key`, value) VALUES ('$SCHEMA_VERSION_KEY', '$to')",
                    )
                }
            }
        }.toTypedArray()
    }

    /**
     * 执行一条已经检查过的迁移语句。
     *
     * @param db 正在升级的库。
     * @param sql 一条 SQL。
     */
    internal fun exec(db: SupportSQLiteDatabase, sql: String) {
        ensureKeepsHistory(sql)
        db.execSQL(sql)
    }
}
