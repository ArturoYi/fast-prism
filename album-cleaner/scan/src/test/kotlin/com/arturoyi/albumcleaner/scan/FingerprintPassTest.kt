/**
 * 指纹阶段怎样读字节、解码，以及怎样跳过没变的行。
 *
 * @file
 */
package com.arturoyi.albumcleaner.scan

import android.os.CancellationSignal
import android.os.OperationCanceledException
import com.arturoyi.albumcleaner.storage.AlbumIndex
import com.arturoyi.albumcleaner.storage.AssetRecord
import com.arturoyi.albumcleaner.storage.ScanSessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 用真实索引，字节和缩略图都是假的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FingerprintPassTest {
    private val gray = GrayImage(8, 8, IntArray(64) { 20 })

    /**
     * 只有一张时不读文件字节，但会解一次缩略图。没变的话下次跳过。
     */
    @Test
    fun uniqueImageIsDecodedOnceAndThenReused() = runBlocking {
        AlbumIndex.open(context(), "unique.db").use { index ->
            index.upsertAssets(listOf(image(1L, size = 10L)))
            val files = FakeBytes(partials = emptyMap())
            val thumbs = FakeThumbs(gray)
            var now = 10L
            pass(index, files, thumbs) { now }.progress(CancellationSignal()).collect { }
            assertTrue(files.partialIds.isEmpty())
            assertTrue(files.fullIds.isEmpty())
            assertEquals(listOf("content://media/external/images/media/1"), thumbs.uris)
            val feature = index.feature(1L)
            assertNull(feature?.byteHash)
            assertEquals(contentFingerprint(10L, 2L, null), feature?.contentFingerprint)
            assertEquals(dHash(gray).toHashText(), feature?.dhash)
            assertEquals(0.0, feature?.blurScore ?: -1.0, 0.0)
            assertEquals(10L, feature?.computedAt)

            now = 90L
            thumbs.uris.clear()
            pass(index, files, thumbs) { now }.progress(CancellationSignal()).collect { }
            assertTrue(thumbs.uris.isEmpty())
            assertEquals(10L, index.feature(1L)?.computedAt)
        }
    }

    /**
     * 同大小但头尾不同，不读全文。
     */
    @Test
    fun differentPartialsSkipTheFullFile() = runBlocking {
        AlbumIndex.open(context(), "partial.db").use { index ->
            index.upsertAssets(listOf(image(1L, 100L), image(2L, 100L)))
            val files = FakeBytes(partials = mapOf(1L to "aa", 2L to "bb"))
            pass(index, files, FakeThumbs(gray)).progress(CancellationSignal()).collect { }
            assertEquals(listOf(1L, 2L), files.partialIds)
            assertTrue(files.fullIds.isEmpty())
            assertEquals("aa", index.feature(1L)?.byteHash)
            assertEquals("bb", index.feature(2L)?.byteHash)
            assertEquals(contentFingerprint(100L, 2L, "aa"), index.feature(1L)?.contentFingerprint)
        }
    }

    /**
     * 局部哈希相同且全文不同时，索引里留下全文哈希。
     */
    @Test
    fun collidingPartialsStoreTheFullHash() = runBlocking {
        AlbumIndex.open(context(), "collide.db").use { index ->
            index.upsertAssets(listOf(image(1L, 100L), image(2L, 100L)))
            val files = FakeBytes(
                partials = mapOf(1L to "aa", 2L to "aa"),
                fulls = mapOf(1L to "full-1", 2L to "full-2"),
            )
            pass(index, files, FakeThumbs(gray)).progress(CancellationSignal()).collect { }
            assertEquals(listOf(1L, 2L), files.fullIds)
            assertEquals("full-1", index.feature(1L)?.byteHash)
            assertEquals("full-2", index.feature(2L)?.byteHash)
            assertEquals("aa", index.feature(1L)?.contentFingerprint?.substringAfterLast("|"))
        }
    }

    /**
     * 局部哈希和全文都相同，仍然保存局部哈希。
     */
    @Test
    fun identicalFilesKeepThePartialHash() = runBlocking {
        AlbumIndex.open(context(), "same.db").use { index ->
            index.upsertAssets(listOf(image(3L, 80L), image(4L, 80L)))
            val files = FakeBytes(
                partials = mapOf(3L to "aa", 4L to "aa"),
                fulls = mapOf(3L to "same", 4L to "same"),
            )
            pass(index, files, FakeThumbs(gray)).progress(CancellationSignal()).collect { }
            assertEquals("aa", index.feature(3L)?.byteHash)
            assertEquals("aa", index.feature(4L)?.byteHash)
        }
    }

    /**
     * 视频只读字节，不解码。
     */
    @Test
    fun videoSkipsTheThumbnail() = runBlocking {
        AlbumIndex.open(context(), "video.db").use { index ->
            index.upsertAssets(listOf(image(8L, 50L, video = true), image(9L, 50L, video = true)))
            val files = FakeBytes(partials = mapOf(8L to "vv", 9L to "vw"))
            val thumbs = FakeThumbs(gray)
            pass(index, files, thumbs).progress(CancellationSignal()).collect { }
            assertEquals(listOf(8L, 9L), files.partialIds)
            assertTrue(thumbs.uris.isEmpty())
            assertNull(index.feature(8L)?.dhash)
            assertEquals("vv", index.feature(8L)?.byteHash)
        }
    }

    /**
     * 取消发生在下一张解码时。已经写入的特征还在，会话不标完成。
     */
    @Test
    fun cancelStopsBeforeTheNextDecode() = runBlocking {
        AlbumIndex.open(context(), "stop.db").use { index ->
            index.upsertAssets(listOf(image(1L, 10L), image(2L, 20L)))
            index.saveSession(ScanSessionRecord(METADATA_SESSION_ID, 2L, "FINGERPRINT", SESSION_RUNNING, 1L))
            val thumbs = FakeThumbs(gray, blockOn = 2L)
            val signal = CancellationSignal()
            val outcome = kotlinx.coroutines.CompletableDeferred<Throwable?>()
            val job = launch(Dispatchers.Default) {
                try {
                    pass(index, FakeBytes(emptyMap()), thumbs).progress(signal).collect { }
                    outcome.complete(null)
                } catch (error: Throwable) {
                    outcome.complete(error)
                }
            }
            assertTrue(thumbs.blocked.await(2, TimeUnit.SECONDS))
            signal.cancel()
            val error = withTimeout(2_000) { outcome.await() }
            job.join()
            assertTrue(error is ScanCancelledException)
            assertEquals(dHash(gray).toHashText(), index.feature(1L)?.dhash)
            assertNull(index.feature(2L))
            assertEquals(SESSION_RUNNING, index.session(METADATA_SESSION_ID)?.status)
        }
    }
}

private fun context() = RuntimeEnvironment.getApplication()

private fun pass(
    index: AlbumIndex,
    files: FileBytes,
    thumbs: ThumbnailDecoder,
    now: () -> Long = { 10L },
): FingerprintPass {
    return FingerprintPass(
        index = index,
        files = files,
        thumbnails = thumbs,
        now = now,
        pacer = YieldPacer(itemBudget = Int.MAX_VALUE, timeBudgetNs = Long.MAX_VALUE, pause = {}),
    )
}

private fun image(id: Long, size: Long, video: Boolean = false): AssetRecord {
    val kind = if (video) "video" else "images"
    return AssetRecord(
        assetId = id,
        uri = "content://media/external/$kind/media/$id",
        sizeBytes = size,
        width = 8,
        height = 8,
        dateAdded = 1L,
        dateModified = 2L,
        dateTaken = null,
        bucketId = "camera",
        relativePath = null,
        isFavorite = false,
        mediaVersion = null,
        indexedAt = 3L,
    )
}

private class FakeBytes(
    private val partials: Map<Long, String>,
    private val fulls: Map<Long, String> = emptyMap(),
) : FileBytes {
    val partialIds = mutableListOf<Long>()
    val fullIds = mutableListOf<Long>()

    override fun partial(uri: String, size: Long, signal: CancellationSignal): String? {
        if (signal.isCanceled) throw OperationCanceledException()
        val id = uri.substringAfterLast("/").toLong()
        partialIds += id
        return partials[id]
    }

    override fun full(uri: String, signal: CancellationSignal): String? {
        if (signal.isCanceled) throw OperationCanceledException()
        val id = uri.substringAfterLast("/").toLong()
        fullIds += id
        return fulls[id]
    }
}

private class FakeThumbs(
    private val image: GrayImage,
    private val blockOn: Long? = null,
) : ThumbnailDecoder {
    val uris = mutableListOf<String>()
    val blocked = CountDownLatch(1)

    override fun gray64(uri: String, signal: CancellationSignal): GrayImage? {
        if (signal.isCanceled) throw OperationCanceledException()
        val id = uri.substringAfterLast("/").toLong()
        uris += uri
        if (id == blockOn) {
            blocked.countDown()
            val lock = Object()
            signal.setOnCancelListener {
                synchronized(lock) { lock.notifyAll() }
            }
            val deadline = System.nanoTime() + 2_000_000_000L
            synchronized(lock) {
                while (!signal.isCanceled) {
                    val remainingMs = (deadline - System.nanoTime()) / 1_000_000
                    if (remainingMs <= 0L) error("取消没有传到解码")
                    lock.wait(remainingMs)
                }
            }
            throw OperationCanceledException()
        }
        return image
    }
}
