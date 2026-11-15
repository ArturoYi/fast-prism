/**
 * 按分组暂停、继续和删除。
 *
 * @file
 */
package com.arturoyi.fastdowload.api

import com.arturoyi.fastdowload.core.model.DownloadRecord
import com.arturoyi.fastdowload.core.spi.DownloadRepository
import com.arturoyi.fastdowload.engine.dispatch.CommandDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [FastDownloader.groups] 上的分组操作。
 *
 * @param dispatcher 串行命令队列。
 * @param repository 用来按分组读取任务。
 */
public class Groups internal constructor(
    private val dispatcher: CommandDispatcher,
    private val repository: DownloadRepository,
) {
    /**
     * 读取一个分组里的任务。
     *
     * 在后台读取，不占用调用协程的线程。
     *
     * @param groupId 分组名。
     * @return 这一组的任务快照。
     */
    public suspend fun get(groupId: String): List<DownloadRecord> = withContext(Dispatchers.IO) {
        repository.query().filter { record -> record.request.groupId == groupId }
    }

    /**
     * 暂停这一组里还没结束的任务。
     *
     * @param groupId 分组名。
     */
    public fun pause(groupId: String) {
        dispatcher.pauseGroup(groupId)
    }

    /**
     * 继续这一组里已暂停或已登记的任务。
     *
     * @param groupId 分组名。
     */
    public fun resume(groupId: String) {
        dispatcher.resumeGroup(groupId)
    }

    /**
     * 取消这一组里还没结束的任务。
     *
     * @param groupId 分组名。
     */
    public fun cancel(groupId: String) {
        dispatcher.cancelGroup(groupId)
    }

    /**
     * 删除这一组的任务和目标文件。
     *
     * @param groupId 分组名。
     */
    public fun delete(groupId: String) {
        dispatcher.deleteGroup(groupId)
    }
}
