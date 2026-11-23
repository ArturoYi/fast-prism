/**
 * 打开索引库。外键和 WAL 由 Room 打开，损坏时不删除文件。
 *
 * @file
 */
package com.arturoyi.albumcleaner.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * 相册索引。版本见 [IndexSchema.VERSION]。
 */
@Database(
    entities = [
        MetaEntity::class,
        AssetEntity::class,
        FeatureEntity::class,
        EmbeddingEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        CandidateEntity::class,
        ScanSessionEntity::class,
        ActionEntity::class,
        RecycleEntity::class,
    ],
    version = IndexSchema.VERSION,
    exportSchema = false,
)
internal abstract class IndexDatabase : RoomDatabase() {
    /**
     * 十张表的访问入口。
     */
    public abstract fun index(): IndexDao

    /**
     * 打开连接，确认表还在，并做完整性校验。
     *
     * Room 只在结构身份对不上时检查表。缺表但身份仍在时，这里拒绝打开。
     */
    internal fun ensureUsable() {
        try {
            checkUsable(openHelper.writableDatabase)
        } catch (error: Throwable) {
            throw error.openFailure()
        }
    }

    internal companion object {
        /**
         * 打开或创建索引，并完成校验。失败时关掉连接再抛出。
         *
         * @param context 用来定位数据库文件。
         * @param name 文件名。
         */
        internal fun open(context: Context, name: String): IndexDatabase {
            val database = build(context, name)
            try {
                database.ensureUsable()
                return database
            } catch (error: Throwable) {
                database.close()
                throw error
            }
        }

        /**
         * 只构建连接，不校验表。测试用来把一张表删掉。
         */
        internal fun build(context: Context, name: String): IndexDatabase {
            return Room.databaseBuilder(context.applicationContext, IndexDatabase::class.java, name)
                .allowMainThreadQueries()
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(IndexOpenCallback())
                .openHelperFactory(KeepCorruptFileFactory())
                .addMigrations(*IndexSchema.roomMigrations())
                .build()
        }
    }
}

/**
 * 新建库时写下 schema_version，每次打开把同步级别设成 NORMAL。
 */
internal class IndexOpenCallback : RoomDatabase.Callback() {
    public override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR REPLACE INTO meta(`key`, value) VALUES (" +
                "'${IndexSchema.SCHEMA_VERSION_KEY}', '${IndexSchema.VERSION}')",
        )
    }

    public override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA synchronous = NORMAL")
    }
}

/**
 * 系统发现文件损坏时的默认动作是删掉整个库。这里改为抛出 [IndexCorruptedException]，回收站还留在磁盘上。
 */
internal class KeepCorruptFileFactory : SupportSQLiteOpenHelper.Factory {
    public override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val delegate = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(delegate.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                delegate.onConfigure(db)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                delegate.onCreate(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                delegate.onUpgrade(db, oldVersion, newVersion)
            }

            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                delegate.onDowngrade(db, oldVersion, newVersion)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                delegate.onOpen(db)
            }

            override fun onCorruption(db: SupportSQLiteDatabase) {
                throw IndexCorruptedException("索引文件损坏")
            }
        }
        val rebuilt = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(callback)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(false)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(rebuilt)
    }
}

private fun IndexDatabase.checkUsable(db: SupportSQLiteDatabase) {
    val missing = missingTables(db)
    if (missing.isNotEmpty()) {
        throw IndexCorruptedException("索引缺少表: ${missing.joinToString()}")
    }
    val integrity = integrity(db)
    if (!integrity.equals("ok", ignoreCase = true)) {
        throw IndexCorruptedException("索引校验失败: $integrity")
    }
}

private fun missingTables(db: SupportSQLiteDatabase): List<String> {
    val found = mutableSetOf<String>()
    db.query(SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type = 'table'")).use { cursor ->
        while (cursor.moveToNext()) found += cursor.getString(0)
    }
    return IndexSchema.TABLES.filter { name -> name !in found }
}

private fun integrity(db: SupportSQLiteDatabase): String {
    db.query(SimpleSQLiteQuery("PRAGMA integrity_check")).use { cursor ->
        return if (cursor.moveToFirst()) cursor.getString(0) else ""
    }
}

private fun Throwable.openFailure(): Throwable {
    generateSequence(this) { error -> error.cause }
        .filterIsInstance<IndexCorruptedException>()
        .firstOrNull()
        ?.let { return it }
    if (this is IllegalStateException || isSqliteFailure()) {
        return IndexCorruptedException(message ?: "索引打不开", this)
    }
    return this
}

private fun Throwable.isSqliteFailure(): Boolean {
    return generateSequence(this) { error -> error.cause }.any { error ->
        error.javaClass.name.startsWith("android.database.sqlite.SQLite")
    }
}
