/**
 * 下载页的状态：输入框，以及还没完成的任务。
 *
 * @file
 */
package com.arturoyi.fastdownload.sample.futures.tasks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.arturoyi.fastdownload.core.model.DownloadRecord
import com.arturoyi.fastdownload.core.state.Status
import com.arturoyi.fastdownload.sample.SampleApp
import com.arturoyi.fastdownload.sample.utils.fileNameFromUrl

/**
 * 点一下就下载的示例。地址是 OVH 的公开测速文件。
 *
 * @property url 绝对地址。
 * @property fileName 内部目录里的文件名。
 */
internal data class QuickDownload(
    val url: String,
    val fileName: String,
)

/**
 * 链接不能用时的原因。句子由页面按当前语言写出。
 */
internal sealed interface UrlError {
    /** 输入框是空的。 */
    data object Empty : UrlError

    /** 用了 http，而不是 https。 */
    data object NeedHttps : UrlError

    /** 这条链接已经有任务。删掉那条之后才能再下。 */
    data object AlreadyAdded : UrlError

    /**
     * 下载器拒了这个地址。
     *
     * @property detail 下载器给出的说明。没有时为 null。
     */
    data class Unusable(val detail: String?) : UrlError
}

/** 一个 1 GB 的示例。文件长度是 1024³ 字节，支持断点。 */
internal val sampleDownload: QuickDownload = QuickDownload(
    url = "https://proof.ovh.net/files/1Gb.dat",
    fileName = "sample-1gb.bin",
)

/**
 * 下载页在界面上要画的内容。
 *
 * @property url 输入框里的链接。
 * @property error 链接不能用的原因。没有问题时为 null。
 * @property tasks 还没完成的任务，新的在前。
 * @property sample 页面上的示例下载。release 里为 null，按钮不出现。
 */
internal data class TasksUiState(
    val url: String,
    val error: UrlError?,
    val tasks: List<DownloadRecord>,
    val sample: QuickDownload?,
)

/**
 * 管链接输入，并把添加、开始、暂停和删除转给任务列表。
 *
 * @param application 用来取 [SampleApp.downloads]。
 */
internal class TasksViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val downloads = (application as SampleApp).downloads

    /** 输入框里的链接。 */
    var url by mutableStateOf("")
        private set

    /** 链接不能用的原因。没有问题时为 null。 */
    var urlError by mutableStateOf<UrlError?>(null)
        private set

    /**
     * 当前要画的内容。
     *
     * 在组合里读取。链接、错误或任务列表变化时会刷新。
     */
    val uiState: TasksUiState
        get() = TasksUiState(
            url = url,
            error = urlError,
            tasks = downloads.records.filter { it.status != Status.COMPLETED },
            sample = if (showSampleDownload) sampleDownload else null,
        )

    /**
     * 改输入框。用户接着改时，清掉上一次的错误。
     *
     * @param value 当前文本。
     */
    fun updateUrl(value: String) {
        url = value
        urlError = null
    }

    /**
     * 用输入框里的链接开始下载。
     *
     * @return 已经入队时为 true。链接是空的、不合法，或已经有任务时为 false。
     */
    fun submitUrl(): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            urlError = UrlError.Empty
            return false
        }
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            urlError = UrlError.NeedHttps
            return false
        }
        return try {
            if (!downloads.enqueue(trimmed, fileNameFromUrl(trimmed))) {
                urlError = UrlError.AlreadyAdded
                return false
            }
            url = ""
            urlError = null
            true
        } catch (error: IllegalArgumentException) {
            urlError = UrlError.Unusable(error.message)
            false
        }
    }

    /**
     * 下载一条准备好的示例。
     *
     * @param sample 按钮对应的地址和文件名。
     */
    fun quick(sample: QuickDownload) {
        try {
            urlError = if (downloads.enqueue(sample.url, sample.fileName)) {
                null
            } else {
                UrlError.AlreadyAdded
            }
        } catch (error: IllegalArgumentException) {
            urlError = UrlError.Unusable(error.message)
        }
    }

    /**
     * 开始这一条。
     *
     * 已登记和已暂停会接着下。失败或取消过的重新排队。已经在下或在排队时不做事。
     */
    fun start(id: String) {
        val status = downloads.records.firstOrNull { it.id == id }?.status ?: return
        if (!canStart(status)) return
        if (status == Status.FAILED || status == Status.CANCELLED) {
            downloads.retry(id)
        } else {
            downloads.resume(id)
        }
    }

    /**
     * 暂停这一条。断点还在。
     *
     * 只有正在排队、下载或等网络时才会停。其他状态不做事。
     */
    fun pause(id: String) {
        val status = downloads.records.firstOrNull { it.id == id }?.status ?: return
        if (!canPause(status)) return
        downloads.pause(id)
    }

    /** 去掉记录，并删除目标文件。 */
    fun delete(id: String) {
        downloads.delete(id)
    }
}

/** 已登记、已暂停、失败或取消之后可以开始。 */
internal fun canStart(status: Status): Boolean = when (status) {
    Status.ADDED, Status.PAUSED, Status.FAILED, Status.CANCELLED -> true
    else -> false
}

/** 正在排队、下载或等网络时可以暂停。 */
internal fun canPause(status: Status): Boolean = when (status) {
    Status.QUEUED, Status.WAITING_NETWORK, Status.DOWNLOADING -> true
    else -> false
}
