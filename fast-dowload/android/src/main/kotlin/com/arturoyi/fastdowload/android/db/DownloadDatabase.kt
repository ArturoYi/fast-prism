/**
 * 本机的下载数据库。
 *
 * @file
 */
package com.arturoyi.fastdowload.android.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 任务、分块、脏标记和响应指纹。
 *
 * 版本 2 起保存重试次数、速度上限和完整性校验。从版本 1 升级走 [MIGRATION_1_2]。
 */
@Database(
    entities = [
        DownloadEntity::class,
        BlockEntity::class,
        DirtyEntity::class,
        ResponseMetaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
public abstract class DownloadDatabase : RoomDatabase() {
    /**
     * 四张表的访问入口。
     *
     * @return 阻塞的 DAO。
     */
    public abstract fun downloads(): DownloadDao

    public companion object {
        /**
         * 打开应用私有目录里的数据库。
         *
         * @param context 用来定位数据库文件。会使用它的 applicationContext。
         * @return 可读写的数据库。
         */
        public fun create(context: Context, name: String = "fastdowload.db"): DownloadDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DownloadDatabase::class.java,
                name,
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}

/**
 * 给版本 1 的任务表补上重试、速度和校验列。
 *
 * 已有行使用默认值：还没重试过、不限速、不校验。
 */
public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    /**
     * 增加五列。不改已有任务的状态和偏移。
     *
     * @param db 正在升级的数据库。
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download ADD COLUMN retry_attempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE download ADD COLUMN max_retry_attempts INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE download ADD COLUMN retry_on_network INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE download ADD COLUMN speed_limit INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE download ADD COLUMN integrity TEXT NOT NULL DEFAULT ''")
    }
}
