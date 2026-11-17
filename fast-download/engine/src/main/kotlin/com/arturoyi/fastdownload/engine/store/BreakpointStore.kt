/**
 * 下载执行时需要的断点读法。
 *
 * 在 [com.arturoyi.fastdownload.core.spi.DownloadRepository] 之上补上试连用的本地进度。
 *
 * @file
 */
package com.arturoyi.fastdownload.engine.store

import com.arturoyi.fastdownload.core.error.DownloadError
import com.arturoyi.fastdownload.core.spi.DownloadRepository
import com.arturoyi.fastdownload.engine.trial.TrialBaseline

/**
 * 内存仓库和 Room 仓库都要能给出试连基线。
 */
public interface BreakpointStore : DownloadRepository {
    /**
     * 用已写字节和指纹拼出试连进度。
     *
     * @param publicId 任务标识。
     * @return 没有记录时，已写字节为 0、总长度未知。
     */
    public fun baseline(publicId: String): TrialBaseline

    /**
     * 把任务标成正在下载。
     *
     * 单次下载调用使用。命令队列改状态时走 [DownloadRepository.transition]。
     *
     * @param publicId 任务标识。
     */
    public fun markDownloading(publicId: String)

    /**
     * 把任务标成完成。
     *
     * @param publicId 任务标识。
     */
    public fun markCompleted(publicId: String)

    /**
     * 把任务标成失败并记下错误。
     *
     * @param publicId 任务标识。
     * @param error 这次失败。
     */
    public fun markFailed(publicId: String, error: DownloadError)
}
