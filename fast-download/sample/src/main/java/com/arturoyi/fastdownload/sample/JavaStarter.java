package com.arturoyi.fastdownload.sample;

import com.arturoyi.fastdownload.api.DownloadRequestBuilder;
import com.arturoyi.fastdownload.api.FastDownloader;
import com.arturoyi.fastdownload.core.model.DownloadRequest;
import com.arturoyi.fastdownload.core.model.Priority;

/**
 * Java 侧用 Builder 组请求并入队。
 */
public final class JavaStarter {
    private JavaStarter() {
    }

    /**
     * 把示例地址登记成内部相对路径。
     *
     * @param downloader 已经初始化的下载器。
     * @param url 资源地址。
     * @return 任务标识。
     */
    public static String enqueue(FastDownloader downloader, String url) {
        DownloadRequest request = new DownloadRequestBuilder(url)
                .saveTo("downloads", "from-java.bin")
                .priority(Priority.NORMAL)
                .downloadOnEnqueue(false)
                .build();
        return downloader.getTasks().enqueue(request);
    }
}
