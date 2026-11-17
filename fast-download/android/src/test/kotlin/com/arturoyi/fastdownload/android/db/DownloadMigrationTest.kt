/**
 * 版本 1 的库升级后仍能读出任务，重试次数从 0 开始。
 *
 * @file
 */
package com.arturoyi.fastdownload.android.db

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [MIGRATION_1_2] 给旧表补上重试、速度和校验列。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadMigrationTest {
    /**
     * 打开版本 1 的库之后，原来的任务还在，并且重试次数是 0。
     */
    @Test
    fun version1GainsRetryColumns() {
        val context = RuntimeEnvironment.getApplication()
        val name = "migrate.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.execSQL(
            """
            CREATE TABLE download (
              public_id TEXT NOT NULL PRIMARY KEY,
              url TEXT NOT NULL,
              destination TEXT NOT NULL,
              status TEXT NOT NULL,
              priority INTEGER NOT NULL,
              group_id TEXT NOT NULL,
              network_type TEXT NOT NULL,
              enqueue_action TEXT NOT NULL,
              headers TEXT NOT NULL,
              tags TEXT NOT NULL,
              connection_count INTEGER NOT NULL,
              buffer_size INTEGER NOT NULL,
              download_on_enqueue INTEGER NOT NULL,
              total_bytes INTEGER NOT NULL,
              downloaded_bytes INTEGER NOT NULL,
              etag TEXT,
              last_modified TEXT,
              accept_ranges INTEGER NOT NULL,
              chunked INTEGER NOT NULL,
              error_code INTEGER,
              error_message TEXT,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            CREATE TABLE block (
              public_id TEXT NOT NULL,
              block_index INTEGER NOT NULL,
              start_offset INTEGER NOT NULL,
              content_length INTEGER NOT NULL,
              current_offset INTEGER NOT NULL,
              PRIMARY KEY(public_id, block_index)
            )
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            CREATE TABLE dirty_file (
              public_id TEXT NOT NULL PRIMARY KEY,
              marked_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            CREATE TABLE response_meta (
              url TEXT NOT NULL PRIMARY KEY,
              etag TEXT,
              last_modified TEXT,
              content_length INTEGER NOT NULL,
              accept_ranges INTEGER NOT NULL,
              chunked INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        sqlite.execSQL(
            """
            INSERT INTO download (
              public_id, url, destination, status, priority, group_id, network_type, enqueue_action,
              headers, tags, connection_count, buffer_size, download_on_enqueue, total_bytes,
              downloaded_bytes, etag, last_modified, accept_ranges, chunked, error_code, error_message,
              created_at, updated_at
            ) VALUES (
              'abc', 'https://example.com/a.bin', 'a.bin', 'QUEUED', 0, 'default', 'ALL',
              'UPDATE_ACCORDINGLY', '', '', -1, 8192, 1, -1, 0, NULL, NULL, 0, 0, NULL, NULL, 10, 10
            )
            """.trimIndent(),
        )
        sqlite.version = 1
        sqlite.close()

        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val worker = Thread {
            try {
                val repository = RoomDownloadRepository(context, name)
                val record = repository.get("abc")
                assertEquals(0, record?.retryAttempts)
                assertEquals("QUEUED", record?.status?.name)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join()
        failure.get()?.let { throw it }
    }
}
