package com.arturoyi.fastdownload.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.arturoyi.fastdownload.core.model.DownloadRequest;
import com.arturoyi.fastdownload.core.model.Priority;
import com.arturoyi.fastdownload.core.state.Status;
import com.arturoyi.fastdownload.engine.store.InMemoryBreakpointStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Java 用 Builder 入队，不经过 Kotlin DSL。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class JavaDownloadTest {
    /**
     * Builder 生成的请求能同步拿到标识，并且不会立刻去连网络。
     */
    @Test
    public void builderEnqueuesWithoutKotlin() throws Exception {
        InMemoryBreakpointStore store = new InMemoryBreakpointStore();
        DownloaderConfig config = new DownloaderConfig.Builder(RuntimeEnvironment.getApplication())
                .namespace("java-demo")
                .concurrentLimit(2)
                .repository(store)
                .autoStart(false)
                .build();
        FastDownloader downloader = FastDownloader.create(config);
        try {
            DownloadRequest request = new DownloadRequestBuilder("https://example.com/a.bin")
                    .saveTo("downloads", "a.bin")
                    .priority(Priority.HIGH)
                    .downloadOnEnqueue(false)
                    .build();
            String id = downloader.getTasks().enqueue(request);
            assertNotNull(id);
            assertEquals(Status.ADDED, store.get(id).getStatus());
            assertEquals("downloads/a.bin", store.get(id).getRequest().getDestination().getRelativePath());
        } finally {
            downloader.close();
        }
    }
}
