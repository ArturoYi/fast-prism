/**
 * Room 仓库与内存仓库走同一套检查。
 *
 * @file
 */
package com.arturoyi.fastdownload.android.db

import com.arturoyi.fastdownload.testing.RepositoryChecks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

/**
 * [RoomDownloadRepository] 在本地数据库里满足仓库契约。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositoryTest {
    /**
     * 写入后重新打开数据库，排序、偏移和清洗仍然成立。
     */
    @Test
    fun matchesRepositoryContract() {
        val context = RuntimeEnvironment.getApplication()
        val repository = RoomDownloadRepository(context)
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                RepositoryChecks.verify(repository, context.filesDir)
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join()
        failure.get()?.let { throw it }
    }
}
